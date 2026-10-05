package com.harsha.queueflow.exception;

import java.time.Instant;
import java.util.List;

/**
 * Clean, uniform error payload returned for every non-2xx API response.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldViolation> violations
) {
    public record FieldViolation(String field, String message) {
    }
}
