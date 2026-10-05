package com.harsha.queueflow.dto;

import com.harsha.queueflow.enums.AuthProvider;
import com.harsha.queueflow.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Admin-only user management payload. This is the only path through which a
 * STAFF or ADMIN account can come into existence.
 */
public record AdminUserRequest(
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 120, message = "Name must be between 2 and 120 characters")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "A valid email address is required")
        String email,

        @Pattern(regexp = "^$|^[+]?[0-9]{7,15}$", message = "Enter a valid phone number (7-15 digits)")
        String phone,

        @Size(min = 8, max = 72, message = "Password must be at least 8 characters")
        String password,

        @NotNull(message = "Role is required")
        Role role,

        Boolean active
) {
    public boolean activeOrDefault() {
        return active == null || active;
    }
}
