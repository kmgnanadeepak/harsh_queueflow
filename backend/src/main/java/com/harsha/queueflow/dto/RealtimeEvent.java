package com.harsha.queueflow.dto;

import java.time.Instant;
import java.util.List;

import com.harsha.queueflow.enums.CounterStatus;
import com.harsha.queueflow.enums.NotificationType;
import com.harsha.queueflow.enums.PriorityType;
import com.harsha.queueflow.enums.TokenStatus;

/**
 * Aggregate payload pushed over STOMP topics so the UI can react instantly.
 */
public record RealtimeEvent(
        String type,
        Long tokenId,
        String tokenNumber,
        Long serviceId,
        String serviceName,
        Long userId,
        PriorityType priority,
        TokenStatus status,
        CounterStatus counterStatus,
        Long counterId,
        Integer position,
        Integer peopleAhead,
        Integer estimatedWaitingMinutes,
        Integer waitingCount,
        Integer servingCount,
        Integer activeCounters,
        Integer unreadCount,
        String message,
        Instant occurredAt
) {
    public static final String TOKEN_CREATED = "TOKEN_CREATED";
    public static final String TOKEN_CALLED = "TOKEN_CALLED";
    public static final String TOKEN_STATUS_CHANGED = "TOKEN_STATUS_CHANGED";
    public static final String TOKEN_COMPLETED = "TOKEN_COMPLETED";
    public static final String TOKEN_SKIPPED = "TOKEN_SKIPPED";
    public static final String QUEUE_UPDATED = "QUEUE_UPDATED";
    public static final String NOTIFICATION = "NOTIFICATION";

    public static RealtimeEvent of(String type, QueueTokenView token) {
        return new RealtimeEvent(type, token.id(), token.tokenNumber(), token.serviceId(), token.serviceName(),
                token.userId(), token.priority(), token.status(), null, token.counterId(), token.position(),
                token.peopleAhead(), token.estimatedWaitingMinutes(), null, null, null, null, null,
                Instant.now());
    }

    public static RealtimeEvent notification(Long userId, Long tokenId, NotificationType type, String message,
                                             Integer unreadCount) {
        return new RealtimeEvent(NOTIFICATION, tokenId, null, null, null, userId, null, null, null, null, null,
                null, null, null, null, null, unreadCount, message, Instant.now());
    }
}
