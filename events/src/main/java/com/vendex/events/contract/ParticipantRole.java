package com.vendex.events.contract;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ParticipantRole {
    VENDOR,
    ATTENDEE;

    @JsonValue
    public String wireValue() {
        return name().toLowerCase();
    }
}
