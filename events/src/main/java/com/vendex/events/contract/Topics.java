package com.vendex.events.contract;

/**
 * Kafka topic names, centralized so producers (Phase 2) and consumers
 * (Phase 3 overlap engine + notifications) reference one source of truth.
 */
public final class Topics {

    private Topics() {}

    public static final String INVENTORY_UPDATED = "inventory.updated";
    public static final String BUYLIST_UPDATED = "buylist.updated";
    public static final String EVENT_CREATED = "event.created";
    public static final String EVENT_VENDOR_REGISTERED = "event.vendor_registered";
}
