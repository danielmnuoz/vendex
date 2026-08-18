package com.vendex.notification.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OverlapInterest(
        UUID id,
        UUID overlapId,
        UUID interestedVendorId,
        UUID counterpartyVendorId,
        UUID eventId,
        BigDecimal score,
        InterestStatus status,
        Instant createdAt,
        Instant updatedAt
) {}
