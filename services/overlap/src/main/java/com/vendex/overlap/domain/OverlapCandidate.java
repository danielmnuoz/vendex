package com.vendex.overlap.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record OverlapCandidate(
        UUID id,
        UUID eventId,
        UUID buyerVendorId,
        UUID sellerVendorId,
        UUID cardId,
        InventorySnapshot inventory,
        DemandSnapshot demand,
        BigDecimal score
) {}
