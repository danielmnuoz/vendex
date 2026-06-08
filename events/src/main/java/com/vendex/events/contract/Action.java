package com.vendex.events.contract;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The kind of mutation an {@code *.updated} event describes. Serializes to the
 * lowercase string the technical spec documents ({@code "added"} / {@code
 * "removed"} / {@code "updated"}).
 */
public enum Action {
    ADDED,
    REMOVED,
    UPDATED;

    @JsonValue
    public String wireValue() {
        return name().toLowerCase();
    }
}
