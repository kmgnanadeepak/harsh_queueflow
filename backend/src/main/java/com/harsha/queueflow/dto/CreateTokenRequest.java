package com.harsha.queueflow.dto;

import java.time.Instant;

import com.harsha.queueflow.enums.PriorityType;
import com.harsha.queueflow.enums.TokenStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request to join a queue. The owning user is always taken from the JWT, never
 * from the request body.
 */
public record CreateTokenRequest(
        @NotNull(message = "Service is required")
        Long serviceId,

        PriorityType priority,

        @Size(max = 200, message = "Note must not exceed 200 characters")
        String note
) {
    public PriorityType priorityOrDefault() {
        return priority == null ? PriorityType.NORMAL : priority;
    }
}
