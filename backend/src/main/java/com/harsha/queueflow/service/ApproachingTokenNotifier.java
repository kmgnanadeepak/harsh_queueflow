package com.harsha.queueflow.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.harsha.queueflow.config.QueueFlowProperties;
import com.harsha.queueflow.dto.QueueTokenView;
import com.harsha.queueflow.entity.QueueService;
import com.harsha.queueflow.entity.QueueToken;
import com.harsha.queueflow.enums.NotificationType;
import com.harsha.queueflow.repository.QueueServiceRepository;
import com.harsha.queueflow.repository.QueueTokenRepository;

/**
 * Fires the "your turn is approaching" notification.
 *
 * <p>Runs on a fixed schedule, inspects every WAITING token whose live position
 * is within the configured threshold and notifies each owner exactly once per
 * token (idempotency enforced by the notifications table).</p>
 */
@Component
public class ApproachingTokenNotifier {

    private static final Logger log = LoggerFactory.getLogger(ApproachingTokenNotifier.class);

    private final QueueServiceRepository serviceRepository;
    private final QueueTokenRepository tokenRepository;
    private final QueueEngine queueEngine;
    private final NotificationService notificationService;
    private final QueueFlowProperties properties;

    public ApproachingTokenNotifier(QueueServiceRepository serviceRepository,
                                    QueueTokenRepository tokenRepository,
                                    QueueEngine queueEngine,
                                    NotificationService notificationService,
                                    QueueFlowProperties properties) {
        this.serviceRepository = serviceRepository;
        this.tokenRepository = tokenRepository;
        this.queueEngine = queueEngine;
        this.notificationService = notificationService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${queueflow.queue.approaching-scan-interval-ms:60000}",
            initialDelayString = "${queueflow.queue.approaching-scan-interval-ms:60000}")
    public void scheduledScan() {
        try {
            scan();
        } catch (RuntimeException ex) {
            log.warn("Approaching-token scan failed", ex);
        }
    }

    /**
     * Scans every active service once. Exposed so it can be triggered directly by
     * tests and by the admin "run scan" action.
     *
     * @return the number of notifications created
     */
    @Transactional
    public int scan() {
        int threshold = Math.max(0, properties.getQueue().getApproachingThreshold());
        int notified = 0;

        for (QueueService service : serviceRepository.findByActiveTrueOrderByNameAsc()) {
            if (queueEngine.countActiveCounters(service) == 0) {
                // Nobody is being served, so "your turn is close" would be misleading.
                continue;
            }
            QueueEngine.QueueSnapshot snapshot = queueEngine.snapshot(service);
            for (QueueTokenView view : snapshot.ordered()) {
                if (view.position() == null || view.position() > threshold) {
                    continue;
                }
                if (notificationService.alreadyNotified(view.userId(), view.id(),
                        NotificationType.TOKEN_APPROACHING)) {
                    continue;
                }
                QueueToken token = tokenRepository.findById(view.id()).orElse(null);
                if (token == null) {
                    continue;
                }
                int peopleAhead = view.peopleAhead() == null ? 0 : view.peopleAhead();
                String message = peopleAhead <= 0
                        ? "Token " + token.getTokenNumber() + " is next in line - please stay ready."
                        : "Token " + token.getTokenNumber() + " is close. Only " + peopleAhead
                        + (peopleAhead == 1 ? " person" : " people") + " ahead of you (~"
                        + view.estimatedWaitingMinutes() + " min).";
                notificationService.notify(token.getUser(), token, NotificationType.TOKEN_APPROACHING, message);
                notified++;
            }
        }
        return notified;
    }
}
