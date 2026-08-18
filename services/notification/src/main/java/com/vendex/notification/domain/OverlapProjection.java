package com.vendex.notification.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OverlapProjection(
        UUID overlapId,
        UUID eventId,
        UUID buyerVendorId,
        UUID sellerVendorId,
        UUID cardId,
        String inventoryPriority,
        BigDecimal score,
        String payloadJson,
        boolean active,
        int actionRank,
        Instant occurredAt
) {}
