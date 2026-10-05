package com.harsha.queueflow.dto;

import java.time.Instant;

import com.harsha.queueflow.entity.User;
import com.harsha.queueflow.enums.AuthProvider;
import com.harsha.queueflow.enums.Role;

/**
 * Safe user representation returned by the API. Never contains the password hash
 * or any provider secret.
 */
public record UserResponse(
        Long id,
        String name,
        String email,
        String phone,
        Role role,
        AuthProvider provider,
        String profileImage,
        boolean active,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getProvider(),
                user.getProfileImage(),
                user.isActive(),
                user.getCreatedAt()
        );
    }
}
