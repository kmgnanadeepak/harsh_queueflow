package com.harsha.queueflow.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.harsha.queueflow.dto.QueueTokenView;
import com.harsha.queueflow.dto.RealtimeEvent;

/**
 * Publishes queue/token/notification updates to STOMP topics so dashboards
 * update without polling or page refreshes.
 */
@Service
public class RealtimePublisher {

    private static final Logger log = LoggerFactory.getLogger(RealtimePublisher.class);

    private final SimpMessagingTemplate messagingTemplate;

    public RealtimePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishTokenCreated(QueueTokenView token) {
        publishToQueue(token, RealtimeEvent.of(RealtimeEvent.TOKEN_CREATED, token));
    }

    public void publishTokenCalled(QueueTokenView token) {
        publishToQueue(token, RealtimeEvent.of(RealtimeEvent.TOKEN_CALLED, token));
    }

    public void publishTokenStatusChanged(QueueTokenView token) {
        publishToQueue(token, RealtimeEvent.of(RealtimeEvent.TOKEN_STATUS_CHANGED, token));
    }

    public void publishTokenCompleted(QueueTokenView token) {
        publishToQueue(token, RealtimeEvent.of(RealtimeEvent.TOKEN_COMPLETED, token));
    }

    public void publishTokenSkipped(QueueTokenView token) {
        publishToQueue(token, RealtimeEvent.of(RealtimeEvent.TOKEN_SKIPPED, token));
    }

    public void publishQueueUpdate(Long serviceId, RealtimeEvent event) {
        messagingTemplate.convertAndSend("/topic/queue/" + serviceId, event);
    }

    public void publishNotification(Long userId, RealtimeEvent event) {
        messagingTemplate.convertAndSend("/topic/notifications/" + userId, event);
    }

    private void publishToQueue(QueueTokenView token, RealtimeEvent event) {
        try {
            messagingTemplate.convertAndSend("/topic/queue/" + token.serviceId(), event);
            messagingTemplate.convertAndSend("/topic/token/" + token.id(), event);
        } catch (RuntimeException ex) {
            // Real-time delivery is best effort; a broker hiccup must never fail the
            // business transaction that produced the event.
            log.warn("Unable to publish realtime event for token {}", token.id(), ex);
        }
    }
}
