package com.vendex.inventory.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InventoryItem(
        UUID id,
        UUID vendorId,
        UUID cardId,
        UUID eventId,
        CardCondition condition,
        String gradingCompany,
        BigDecimal grade,
        int quantity,
        BigDecimal askingPrice,
        InventoryPriority priority,
        Instant createdAt,
        Instant updatedAt
) {}
