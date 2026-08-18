package com.vendex.overlap.service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class OverlapIds {
    private OverlapIds() {}

    public static UUID forOpportunity(
            UUID eventId, UUID buyerVendorId, UUID sellerVendorId, UUID cardId) {
        String identity = "vendex-overlap:" + eventId + ":" + buyerVendorId
                + ":" + sellerVendorId + ":" + cardId;
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
    }
}
