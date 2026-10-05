package com.harsha.queueflow.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.harsha.queueflow.dto.QueueStatusResponse;
import com.harsha.queueflow.entity.Counter;
import com.harsha.queueflow.entity.QueueService;
import com.harsha.queueflow.entity.QueueToken;
import com.harsha.queueflow.enums.CounterStatus;
import com.harsha.queueflow.enums.TokenStatus;
import com.harsha.queueflow.repository.CounterRepository;
import com.harsha.queueflow.repository.QueueTokenRepository;

@Service
public class QueueStatusService {

    private final CatalogService catalogService;
    private final QueueEngine queueEngine;
    private final CounterRepository counterRepository;
    private final QueueTokenRepository tokenRepository;

    public QueueStatusService(CatalogService catalogService,
                              QueueEngine queueEngine,
                              CounterRepository counterRepository,
                              QueueTokenRepository tokenRepository) {
        this.catalogService = catalogService;
        this.queueEngine = queueEngine;
        this.counterRepository = counterRepository;
        this.tokenRepository = tokenRepository;
    }

    @Transactional(readOnly = true)
    public QueueStatusResponse status(Long serviceId) {
        QueueService service = catalogService.require(serviceId);
        QueueEngine.QueueSnapshot snapshot = queueEngine.snapshot(service);

        List<QueueToken> tokens = tokenRepository.findByQueueServiceIdAndStatusInOrderByCreatedAtAsc(
                serviceId, List.of(TokenStatus.WAITING, TokenStatus.CALLED, TokenStatus.SERVING,
                        TokenStatus.COMPLETED, TokenStatus.SKIPPED, TokenStatus.CANCELLED));

        int serving = (int) tokens.stream().filter(t -> t.getStatus() == TokenStatus.SERVING).count();
        int called = (int) tokens.stream().filter(t -> t.getStatus() == TokenStatus.CALLED).count();
        int completedToday = (int) tokens.stream()
                .filter(t -> t.getStatus() == TokenStatus.COMPLETED && t.getCompletedAt() != null
                        && t.getCompletedAt().isAfter(Instant.now().minusSeconds(86_400)))
                .count();

        List<Counter> counters = counterRepository.findByQueueServiceIdAndActiveTrueOrderByCounterNumberAsc(serviceId);
        Map<Long, String> servingTokens = servingTokensByCounter(tokens);
        int available = (int) counters.stream().filter(c -> c.getStatus() == CounterStatus.AVAILABLE).count();

        return new QueueStatusResponse(
                service.getId(),
                service.getName(),
                service.getOrganization().getName(),
                service.getAverageServiceTime(),
                snapshot.ordered().size(),
                serving,
                called,
                completedToday,
                snapshot.activeCounters(),
                available,
                snapshot.ordered().stream()
                        .map(view -> new QueueStatusResponse.QueueEntry(
                                view.tokenNumber(),
                                view.position() == null ? 0 : view.position(),
                                view.peopleAhead() == null ? 0 : view.peopleAhead(),
                                view.estimatedWaitingMinutes() == null ? 0 : view.estimatedWaitingMinutes(),
                                view.priority().name()))
                        .toList(),
                counters.stream()
                        .map(counter -> new QueueStatusResponse.CounterSummary(
                                counter.getId(),
                                counter.getCounterNumber(),
                                counter.getStatus(),
                                servingTokens.get(counter.getId())))
                        .toList(),
                snapshot.generatedAt()
        );
    }

    private Map<Long, String> servingTokensByCounter(List<QueueToken> tokens) {
        return tokens.stream()
                .filter(t -> t.getCounter() != null
                        && (t.getStatus() == TokenStatus.SERVING || t.getStatus() == TokenStatus.CALLED))
                .collect(java.util.stream.Collectors.toMap(
                        t -> t.getCounter().getId(), t -> t.getTokenNumber(), (a, b) -> a));
    }
}
