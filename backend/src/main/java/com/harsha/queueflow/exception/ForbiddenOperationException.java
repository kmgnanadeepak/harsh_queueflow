package com.harsha.queueflow.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an authenticated principal is not allowed to act on a resource
 * (for example a USER touching a token that belongs to somebody else).
 * Rendered as HTTP 403 without disclosing whether the resource exists.
 */
public class ForbiddenOperationException extends ApiException {
    public ForbiddenOperationException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
