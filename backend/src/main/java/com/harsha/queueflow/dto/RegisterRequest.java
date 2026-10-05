package com.harsha.queueflow.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Public self-service registration payload.
 *
 * <p>There is deliberately no {@code role} field: a public registration request
 * can only ever create a USER. Privilege is granted exclusively by an ADMIN
 * through {@code POST /api/admin/users}.</p>
 */
public record RegisterRequest(
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 120, message = "Name must be between 2 and 120 characters")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "A valid email address is required")
        @Size(max = 190, message = "Email must not exceed 190 characters")
        String email,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^[+]?[0-9]{7,15}$", message = "Enter a valid phone number (7-15 digits)")
        String phone,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be at least 8 characters")
        String password
) {
}
