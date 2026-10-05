package com.harsha.queueflow.dto;

import java.time.Instant;

import com.harsha.queueflow.entity.QueueToken;
import com.harsha.queueflow.enums.PriorityType;
import com.harsha.queueflow.enums.TokenStatus;

/**
 * Flat, fully initialised projection of a token used by the queue engine and by
 * the real-time publisher. Prevents lazy-loading outside of transactions.
 */
public record QueueTokenView(
        Long id,
        String tokenNumber,
        Long userId,
        String userName,
        Long serviceId,
        String serviceName,
        String organizationName,
        int averageServiceTime,
        int activeCounters,
        Long counterId,
        String counterNumber,
        PriorityType priority,
        TokenStatus status,
        Instant createdAt,
        Instant calledAt,
        Instant servingAt,
        Instant completedAt,
        Integer position,
        Integer peopleAhead,
        Integer estimatedWaitingMinutes
) {
    public static QueueTokenView of(QueueToken token) {
        return new QueueTokenView(
                token.getId(),
                token.getTokenNumber(),
                token.getUser().getId(),
                token.getUser().getName(),
                token.getQueueService().getId(),
                token.getQueueService().getName(),
                token.getQueueService().getOrganization() == null
                        ? null : token.getQueueService().getOrganization().getName(),
                token.getQueueService().getAverageServiceTime(),
                0,
                token.getCounter() == null ? null : token.getCounter().getId(),
                token.getCounter() == null ? null : token.getCounter().getCounterNumber(),
                token.getPriorityType(),
                token.getStatus(),
                token.getCreatedAt(),
                token.getCalledAt(),
                token.getServingAt(),
                token.getCompletedAt(),
                null,
                null,
                null
        );
    }

    public QueueTokenView withQueueMetrics(int newPosition, int newPeopleAhead, int estimatedMinutes,
                                           int newActiveCounters) {
        return new QueueTokenView(id, tokenNumber, userId, userName, serviceId, serviceName, organizationName,
                averageServiceTime, newActiveCounters, counterId, counterNumber, priority, status, createdAt,
                calledAt, servingAt, completedAt, newPosition, newPeopleAhead, estimatedMinutes);
    }
}
