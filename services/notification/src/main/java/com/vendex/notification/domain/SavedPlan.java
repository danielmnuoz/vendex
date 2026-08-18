package com.vendex.notification.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SavedPlan(
        UUID savedOverlapId,
        UUID overlapId,
        UUID vendorId,
        UUID eventId,
        UUID buyerVendorId,
        UUID sellerVendorId,
        UUID cardId,
        BigDecimal score,
        Instant savedAt,
        Instant activatedAt
) {
    public UUID counterpartyVendorId() {
        return vendorId.equals(buyerVendorId) ? sellerVendorId : buyerVendorId;
    }
}
