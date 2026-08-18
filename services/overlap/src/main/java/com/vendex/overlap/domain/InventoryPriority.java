package com.vendex.overlap.domain;

public enum InventoryPriority {
    NORMAL,
    LIQUIDATE;

    public static InventoryPriority parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("inventory priority is required");
        }
        return valueOf(value.toUpperCase());
    }
}
