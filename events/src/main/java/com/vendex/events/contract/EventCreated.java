package com.vendex.events.contract;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Published on {@link Topics#EVENT_CREATED} when an organizer creates an event.
 * Carries the denormalized event header so downstream consumers (Phase 3
 * notifications, future analytics) don't need a synchronous lookup.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EventCreated(
        UUID eventId,
        UUID organizerId,
        String name,
        String city,
        String state,
        LocalDate startDate,
        LocalDate endDate,
        Instant timestamp
) {}
