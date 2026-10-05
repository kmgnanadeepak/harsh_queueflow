package com.harsha.queueflow.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code POST /api/staff/tokens/call-next}.
 *
 * <p>Either an explicit counter, or the counter currently assigned to the staff
 * member. A staff member can never act on a counter they are not assigned to -
 * the service layer enforces that against the JWT identity.</p>
 */
public record CallNextRequest(
        Long counterId,
        Long serviceId,
        @NotNull(message = "Confirm the counter you are serving from")
        Boolean confirmCounter
) {
}
