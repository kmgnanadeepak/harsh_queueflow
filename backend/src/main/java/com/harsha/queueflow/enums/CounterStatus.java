package com.harsha.queueflow.enums;

public enum CounterStatus {
    AVAILABLE,
    BUSY,
    OFFLINE;

    /** Counters that can actually serve a token right now. */
    public boolean isServingCapable() {
        return this != OFFLINE;
    }
}
