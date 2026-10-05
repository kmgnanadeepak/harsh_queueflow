package com.harsha.queueflow.dto;

import java.time.Instant;

import com.harsha.queueflow.entity.Notification;
import com.harsha.queueflow.enums.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NotificationResponse(
        Long id,
        Long tokenId,
        String tokenNumber,
        NotificationType type,
        String message,
        boolean read,
        Instant createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getToken() == null ? null : notification.getToken().getId(),
                notification.getToken() == null ? null : notification.getToken().getTokenNumber(),
                notification.getType(),
                notification.getMessage(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }

    public record CreateRequest(
            @NotBlank(message = "Message is required")
            @Size(max = 500, message = "Message must not exceed 500 characters")
            String message,

            Long tokenId
    ) {
    }
}
