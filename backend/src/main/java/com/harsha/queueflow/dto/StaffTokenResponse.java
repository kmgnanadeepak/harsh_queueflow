package com.harsha.queueflow.dto;

import java.time.Instant;

import com.harsha.queueflow.enums.PriorityType;
import com.harsha.queueflow.enums.TokenStatus;

/**
 * Staff facing row of the waiting queue. Contains no customer contact details.
 */
public record StaffTokenResponse(
        Long id,
        String tokenNumber,
        PriorityType priority,
        TokenStatus status,
        Instant createdAt,
        Instant calledAt,
        Instant servingAt,
        Instant completedAt,
        long waitingMinutes,
        String counterNumber,
        String ownerName
) {
}
