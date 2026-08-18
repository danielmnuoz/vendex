package com.vendex.events.contract;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.util.UUID;

/** Durable event-plan action consumed by notifications/conversation state. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OverlapSaved(
        UUID savedOverlapId,
        UUID overlapId,
        UUID vendorId,
        UUID eventId,
        UUID buyerVendorId,
        UUID sellerVendorId,
        UUID cardId,
        Instant timestamp
) {}
