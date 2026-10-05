package com.harsha.queueflow.enums;

public enum TokenStatus {
    WAITING,
    CALLED,
    SERVING,
    COMPLETED,
    SKIPPED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == SKIPPED || this == CANCELLED;
    }

    public boolean isOpen() {
        return this == WAITING || this == CALLED || this == SERVING;
    }

    public boolean isActiveInQueue() {
        return this == WAITING || this == CALLED || this == SERVING;
    }
}
