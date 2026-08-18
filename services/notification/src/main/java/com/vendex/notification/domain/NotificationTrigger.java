package com.vendex.notification.domain;

public enum NotificationTrigger {
    OVERLAP_BUYLIST("overlap_buylist"),
    OVERLAP_LIQUIDATE("overlap_liquidate"),
    SAVED_OVERLAP_ACTIVE("saved_overlap_active");

    private final String databaseValue;

    NotificationTrigger(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static NotificationTrigger parse(String value) {
        for (NotificationTrigger trigger : values()) {
            if (trigger.databaseValue.equalsIgnoreCase(value)) {
                return trigger;
            }
        }
        throw new IllegalArgumentException("unsupported notification trigger " + value);
    }
}
