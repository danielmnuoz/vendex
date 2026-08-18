package com.vendex.notification.domain;

import java.time.Instant;
import java.util.UUID;

public record NotificationRecord(
        UUID id,
        UUID vendorId,
        UUID eventId,
        NotificationTrigger trigger,
        UUID overlapId,
        UUID cardId,
        UUID counterpartyVendorId,
        String payloadJson,
        boolean read,
        boolean active,
        Instant sourceOccurredAt,
        Instant availableAt,
        Instant createdAt,
        Instant updatedAt
) {}
