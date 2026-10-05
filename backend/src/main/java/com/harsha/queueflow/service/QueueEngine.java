package com.harsha.queueflow.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.harsha.queueflow.config.QueueFlowProperties;
import com.harsha.queueflow.entity.Counter;
import com.harsha.queueflow.entity.QueueService;
import com.harsha.queueflow.entity.QueueToken;
import com.harsha.queueflow.dto.QueueTokenView;
import com.harsha.queueflow.enums.CounterStatus;
import com.harsha.queueflow.enums.TokenStatus;
import com.harsha.queueflow.repository.CounterRepository;
import com.harsha.queueflow.repository.QueueTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The heart of QueueFlow's queueing logic: ordering, position and the estimated
 * waiting time.
 *
 * <h2>Ordering algorithm</h2>
 * <p>Tokens are ordered by an <em>effective rank</em> that combines priority with
 * ageing so that low priority customers are never starved:</p>
 * <pre>
 *   baseRank    = EMERGENCY 0, ELDERLY 1, DISABLED 2, NORMAL 3
 *   waitedMins  = minutes since the token was created
 *   ageBoost    = min(waitedMins / ageingIntervalMinutes, ageingMaxBoost)
 *   effectiveRank = max(0, baseRank - ageBoost)
 *   ordering   = effectiveRank ASC, then createdAt ASC, then id ASC
 * </pre>
 * <p>With the default configuration a NORMAL token gains one rank after every 15
 * minutes of waiting and can gain at most {@code ageingMaxBoost} ranks, so it can
 * overtake a freshly arriving ELDERLY/DISABLED customer but never a fresh or
 * already-waiting EMERGENCY case. The result is priority with a strict fairness
 * bound rather than strict priority.</p>
 *
 * <h2>Estimated waiting time</h2>
 * <pre>
 *   estimatedWaitingMinutes = ceil(peopleAhead * averageServiceTime / activeCounters)
 * </pre>
 * <p>{@code activeCounters} counts the service's active counters that are not
 * OFFLINE; when it is zero the estimate falls back to the serial sum so the UI
 * never shows a division by zero.</p>
 */
@Service
public class QueueEngine {

    private final QueueTokenRepository tokenRepository;
    private final CounterRepository counterRepository;
    private final QueueFlowProperties properties;

    public QueueEngine(QueueTokenRepository tokenRepository,
                       CounterRepository counterRepository,
                       QueueFlowProperties properties) {
        this.tokenRepository = tokenRepository;
        this.counterRepository = counterRepository;
        this.properties = properties;
    }

    /** Comparator implementing the documented priority + ageing policy. */
    public Comparator<QueueToken> waitingComparator(Instant now) {
        int interval = Math.max(1, properties.getQueue().getAgeingIntervalMinutes());
        int maxBoost = Math.max(0, properties.getQueue().getAgeingMaxBoost());
        return Comparator
                .comparingInt((QueueToken token) -> effectiveRank(token, now, interval, maxBoost))
                .thenComparing(QueueToken::getCreatedAt)
                .thenComparing(QueueToken::getId);
    }

    public int effectiveRank(QueueToken token, Instant now, int interval, int maxBoost) {
        long waitedMinutes = Duration.between(token.getCreatedAt(), now).toMinutes();
        if (waitedMinutes < 0) {
            waitedMinutes = 0;
        }
        int boost = (int) Math.min(waitedMinutes / interval, maxBoost);
        return Math.max(0, token.getPriorityType().baseRank() - boost);
    }

    public int effectiveRank(QueueToken token) {
        return effectiveRank(token, Instant.now(),
                Math.max(1, properties.getQueue().getAgeingIntervalMinutes()),
                Math.max(0, properties.getQueue().getAgeingMaxBoost()));
    }

    @Transactional(readOnly = true)
    public List<QueueToken> orderedWaiting(QueueService service) {
        List<QueueToken> waiting = new ArrayList<>(
                tokenRepository.findByQueueServiceIdAndStatusOrderByCreatedAtAsc(service.getId(), TokenStatus.WAITING));
        waiting.sort(waitingComparator(Instant.now()));
        return waiting;
    }

    @Transactional(readOnly = true)
    public QueueSnapshot snapshot(QueueService service) {
        Instant now = Instant.now();
        List<QueueToken> ordered = orderedWaiting(service);
        int activeCounters = countActiveCounters(service);
        int averageServiceTime = Math.max(1, service.getAverageServiceTime());

        Map<Long, Integer> positionByTokenId = new HashMap<>();
        List<QueueTokenView> views = new ArrayList<>(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            QueueToken token = ordered.get(i);
            int position = i + 1;
            int peopleAhead = i;
            int estimated = estimateWaitingMinutes(peopleAhead, averageServiceTime, activeCounters);
            positionByTokenId.put(token.getId(), position);
            views.add(QueueTokenView.of(token).withQueueMetrics(position, peopleAhead, estimated, activeCounters));
        }
        return new QueueSnapshot(views, positionByTokenId, activeCounters, now);
    }

    /**
     * Position/ETA of a single token inside its service queue. Tokens that are no
     * longer WAITING report position {@code 0} (they have left the waiting line).
     */
    @Transactional(readOnly = true)
    public QueueTokenView describe(QueueToken token) {
        QueueService service = token.getQueueService();
        int activeCounters = countActiveCounters(service);
        int averageServiceTime = Math.max(1, service.getAverageServiceTime());
        QueueTokenView base = QueueTokenView.of(token);

        if (token.getStatus() != TokenStatus.WAITING) {
            return base.withQueueMetrics(0, 0, 0, activeCounters);
        }
        List<QueueToken> ordered = orderedWaiting(service);
        int index = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).getId().equals(token.getId())) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return base.withQueueMetrics(0, 0, 0, activeCounters);
        }
        return base.withQueueMetrics(index + 1, index,
                estimateWaitingMinutes(index, averageServiceTime, activeCounters), activeCounters);
    }

    public int countActiveCounters(QueueService service) {
        List<Counter> counters = counterRepository
                .findByQueueServiceIdAndActiveTrueAndStatusNotOrderByCounterNumberAsc(service.getId(),
                        CounterStatus.OFFLINE);
        return counters.size();
    }

    public static int estimateWaitingMinutes(int peopleAhead, int averageServiceTime, int activeCounters) {
        if (peopleAhead <= 0) {
            return 0;
        }
        if (activeCounters <= 0) {
            // No active counter: fall back to a serial estimate instead of dividing by zero.
            return peopleAhead * Math.max(1, averageServiceTime);
        }
        double raw = (peopleAhead * (double) Math.max(1, averageServiceTime)) / activeCounters;
        return (int) Math.ceil(raw);
    }

    /** Immutable view of a computed queue. */
    public record QueueSnapshot(
            List<QueueTokenView> ordered,
            Map<Long, Integer> positionByTokenId,
            int activeCounters,
            Instant generatedAt
    ) {
        public static QueueSnapshot empty() {
            return new QueueSnapshot(List.of(), Map.of(), 0, Instant.now());
        }
    }
}
