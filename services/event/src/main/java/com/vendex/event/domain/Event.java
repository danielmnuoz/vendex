package com.vendex.event.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record Event(
        UUID id,
        UUID organizerId,
        String name,
        String city,
        String state,
        String venue,
        LocalDate startDate,
        LocalDate endDate,
        String description,
        Instant createdAt,
        Instant updatedAt
) {}
