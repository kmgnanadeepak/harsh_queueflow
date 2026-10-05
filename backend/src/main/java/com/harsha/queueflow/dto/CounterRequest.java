package com.harsha.queueflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.harsha.queueflow.enums.CounterStatus;

public record CounterRequest(
        @NotBlank(message = "Counter number is required")
        @Size(max = 30, message = "Counter number must not exceed 30 characters")
        String counterNumber,

        @Size(max = 120, message = "Name must not exceed 120 characters")
        String name,

        CounterStatus status,

        @NotNull(message = "Organization is required")
        Long organizationId,

        @NotNull(message = "Service is required")
        Long serviceId,

        Long staffId,

        Boolean active
) {
    public CounterStatus statusOrDefault() {
        return status == null ? CounterStatus.AVAILABLE : status;
    }

    public boolean activeOrDefault() {
        return !Boolean.FALSE.equals(active);
    }
}
