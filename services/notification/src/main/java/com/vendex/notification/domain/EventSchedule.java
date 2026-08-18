package com.vendex.notification.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EventSchedule(
        UUID eventId,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        Instant occurredAt
) {}
