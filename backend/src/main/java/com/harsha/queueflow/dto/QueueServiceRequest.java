package com.harsha.queueflow.dto;

import java.time.Instant;

import com.harsha.queueflow.entity.QueueService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record QueueServiceRequest(
        @NotBlank(message = "Service name is required")
        @Size(min = 2, max = 150, message = "Name must be between 2 and 150 characters")
        String name,

        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description,

        @NotNull(message = "Average service time is required")
        @Min(value = 1, message = "Average service time must be at least 1 minute")
        @Max(value = 480, message = "Average service time must be 480 minutes or less")
        Integer averageServiceTime,

        @NotNull(message = "Organization is required")
        Long organizationId,

        Boolean active
) {
    public boolean activeOrDefault() {
        return active == null || active;
    }

    public record Response(
            Long id,
            String name,
            String description,
            int averageServiceTime,
            boolean active,
            Long organizationId,
            String organizationName,
            Instant createdAt,
            long waitingCount
    ) {
        public static Response from(QueueService service, long waitingCount) {
            return new Response(
                    service.getId(),
                    service.getName(),
                    service.getDescription(),
                    service.getAverageServiceTime(),
                    service.isActive(),
                    service.getOrganization().getId(),
                    service.getOrganization().getName(),
                    service.getCreatedAt(),
                    waitingCount
            );
        }
    }
}
