package com.harsha.queueflow.dto;

import java.time.Instant;

import com.harsha.queueflow.enums.PriorityType;
import com.harsha.queueflow.enums.TokenStatus;

/**
 * The "when is my turn?" view of a token, from the perspective of its owner.
 */
public record TokenResponse(
        Long id,
        String tokenNumber,
        PriorityType priority,
        TokenStatus status,
        Long serviceId,
        String serviceName,
        String organizationName,
        int averageServiceTime,
        Long counterId,
        String counterNumber,
        Integer position,
        Integer peopleAhead,
        Integer estimatedWaitingMinutes,
        int activeCounters,
        Instant createdAt,
        Instant calledAt,
        Instant servingAt,
        Instant completedAt
) {
}
