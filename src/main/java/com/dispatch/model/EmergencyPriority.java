package com.dispatch.model;

public enum EmergencyPriority {
    CRITICAL(1), HIGH(2), MODERATE(3), NORMAL(4);

    private final int rank;
    EmergencyPriority(int rank) { this.rank = rank; }
    public int getRank() { return rank; }
}
