package com.harsha.queueflow.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {
    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }

    public static NotFoundException of(String entity, Object id) {
        return new NotFoundException(entity + " not found with id " + id);
    }
}
