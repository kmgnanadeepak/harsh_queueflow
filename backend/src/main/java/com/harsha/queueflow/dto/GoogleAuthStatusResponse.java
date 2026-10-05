package com.harsha.queueflow.dto;

/**
 * Tells the React login page whether the "Continue with Google" button can be
 * used, so the UI never offers a flow the server cannot complete.
 */
public record GoogleAuthStatusResponse(boolean enabled, String authorizationUrl, String loginUrl) {
}
