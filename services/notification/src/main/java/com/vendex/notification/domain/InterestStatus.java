package com.vendex.notification.domain;

public enum InterestStatus {
    PENDING,
    REVEALED,
    DECLINED,
    EXPIRED;

    public static InterestStatus parse(String value) {
        return valueOf(value.toUpperCase());
    }
}
