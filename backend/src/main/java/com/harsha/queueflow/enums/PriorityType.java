package com.harsha.queueflow.enums;

/**
 * Queue priority. Lower {@link #baseRank()} means served earlier.
 */
public enum PriorityType {
    EMERGENCY(0),
    ELDERLY(1),
    DISABLED(2),
    NORMAL(3);

    private final int baseRank;

    PriorityType(int baseRank) {
        this.baseRank = baseRank;
    }

    public int baseRank() {
        return baseRank;
    }
}
