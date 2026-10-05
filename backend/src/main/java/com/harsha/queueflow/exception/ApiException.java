package com.harsha.queueflow.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for all business exceptions. Carries the HTTP status and a
 * user-safe message; stack traces are never propagated to the client.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
