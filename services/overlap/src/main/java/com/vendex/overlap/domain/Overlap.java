package com.vendex.overlap.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Overlap(
        UUID id,
        UUID eventId,
        UUID buyerVendorId,
        UUID sellerVendorId,
        UUID cardId,
        UUID inventoryItemId,
        UUID wantedCardId,
        CardCondition sellerCondition,
        CardCondition minimumCondition,
        int availableQuantity,
        int quantityWanted,
        BigDecimal askingPrice,
        BigDecimal maxBuyPrice,
        InventoryPriority inventoryPriority,
        BigDecimal score,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
