package com.harsha.queueflow.dto;

import java.time.Instant;

import com.harsha.queueflow.entity.Organization;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrganizationRequest(
        @NotBlank(message = "Organization name is required")
        @Size(min = 2, max = 150, message = "Name must be between 2 and 150 characters")
        String name,

        @Size(max = 255, message = "Location must not exceed 255 characters")
        String location,

        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description,

        Boolean active
) {
    public boolean activeOrDefault() {
        return active == null || active;
    }

    public record Response(
            Long id,
            String name,
            String location,
            String description,
            boolean active,
            Instant createdAt,
            long serviceCount
    ) {
        public static Response from(Organization organization, long serviceCount) {
            return new Response(
                    organization.getId(),
                    organization.getName(),
                    organization.getLocation(),
                    organization.getDescription(),
                    organization.isActive(),
                    organization.getCreatedAt(),
                    serviceCount
            );
        }
    }
}
