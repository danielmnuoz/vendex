package com.vendex.events.contract;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published on {@link Topics#INVENTORY_UPDATED} whenever a vendor's inventory
 * changes. {@code eventId} is null when the item is always-available rather
 * than scoped to a specific event.
 *
 * <p>Snake-cased on the wire ({@code vendor_id}, ...) to match the schema in
 * technical-spec.md.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record InventoryUpdated(
        UUID inventoryItemId,
        UUID vendorId,
        UUID eventId,
        UUID cardId,
        String condition,
        int quantity,
        BigDecimal askingPrice,
        String priority,
        Action action,
        Instant timestamp
) {}
