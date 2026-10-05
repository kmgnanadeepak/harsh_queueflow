package com.harsha.queueflow.enums;

/**
 * Authorization roles recognised by QueueFlow.
 */
public enum Role {
    USER,
    STAFF,
    ADMIN;

    public boolean isPrivileged() {
        return this == STAFF || this == ADMIN;
    }
}
