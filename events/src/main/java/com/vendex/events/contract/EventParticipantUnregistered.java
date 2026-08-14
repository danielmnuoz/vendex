package com.vendex.events.contract;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.util.UUID;

/**
 * Published after a vendor or attendee leaves an event so downstream roster
 * projections do not retain stale participants and matches.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EventParticipantUnregistered(
        UUID eventId,
        UUID userId,
        ParticipantRole role,
        Instant timestamp
) {}
