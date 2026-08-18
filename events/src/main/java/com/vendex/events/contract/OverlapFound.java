package com.vendex.events.contract;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A materialized event-scoped opportunity. The deterministic overlap ID is
 * derived from event + buyer + seller + card, so consumers can upsert and
 * retire the same opportunity across repeated recomputations.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OverlapFound(
        UUID overlapId,
        UUID eventId,
        UUID buyerVendorId,
        UUID sellerVendorId,
        UUID cardId,
        UUID inventoryItemId,
        UUID wantedCardId,
        String sellerCondition,
        String minimumCondition,
        int availableQuantity,
        int quantityWanted,
        BigDecimal askingPrice,
        BigDecimal maxBuyPrice,
        String inventoryPriority,
        BigDecimal score,
        Action action,
        Instant timestamp
) {}
