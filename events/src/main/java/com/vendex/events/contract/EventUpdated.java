package com.vendex.events.contract;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Published when an organizer changes event metadata or dates. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EventUpdated(
        UUID eventId,
        UUID organizerId,
        String name,
        String city,
        String state,
        LocalDate startDate,
        LocalDate endDate,
        Instant timestamp
) {}
