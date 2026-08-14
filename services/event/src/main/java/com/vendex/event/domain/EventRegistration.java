package com.vendex.event.domain;

import java.time.Instant;
import java.util.UUID;

public record EventRegistration(
        UUID id,
        UUID eventId,
        UUID userId,
        RegistrationRole role,
        String booth,
        Instant registeredAt
) {}
