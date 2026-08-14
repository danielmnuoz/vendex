package com.vendex.inventory.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryItemInput(
        UUID eventId,
        CardCondition condition,
        String gradingCompany,
        BigDecimal grade,
        int quantity,
        BigDecimal askingPrice,
        InventoryPriority priority
) {}
