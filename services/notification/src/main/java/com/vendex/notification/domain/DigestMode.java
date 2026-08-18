package com.vendex.notification.domain;

public enum DigestMode {
    REAL_TIME("real_time"),
    DAILY("daily"),
    EVENT_ONLY("event_only");

    private final String databaseValue;

    DigestMode(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static DigestMode parse(String value) {
        for (DigestMode mode : values()) {
            if (mode.databaseValue.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("unsupported digest mode " + value);
    }
}
