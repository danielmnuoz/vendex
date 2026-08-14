package com.vendex.events.contract;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.util.UUID;

/**
 * Published on {@link Topics#EVENT_ATTENDEE_REGISTERED} when an attendee
 * registers for an event. Phase 5 consumers use this fact to scope anonymous
 * listings and event browsing without reaching into the Event Service DB.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EventAttendeeRegistered(
        UUID eventId,
        UUID attendeeId,
        Instant timestamp
) {}
