package com.vendex.overlap.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InventorySnapshot(
        UUID itemId,
        UUID vendorId,
        UUID eventId,
        UUID cardId,
        CardCondition condition,
        int quantity,
        BigDecimal askingPrice,
        InventoryPriority priority,
        boolean active,
        Instant occurredAt
) {}
