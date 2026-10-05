package com.harsha.queueflow.enums;

/**
 * Identity provider linked to a QueueFlow account.
 *
 * <p>The model is intentionally provider-agnostic so additional providers
 * (Microsoft, Apple, ...) can be added later without schema changes.</p>
 */
public enum AuthProvider {
    /** Email + BCrypt password. */
    LOCAL,
    /** Google via OAuth 2.0 / OpenID Connect. */
    GOOGLE;

    public boolean isFederated() {
        return this != LOCAL;
    }
}
