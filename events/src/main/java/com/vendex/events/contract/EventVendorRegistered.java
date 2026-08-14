package com.vendex.events.contract;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.util.UUID;

/**
 * Published on {@link Topics#EVENT_VENDOR_REGISTERED} when a vendor registers
 * for an event. The Phase 3 overlap engine consumes this to (re)compute
 * overlaps for the newly-arrived vendor against everyone else at the event.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EventVendorRegistered(
        UUID eventId,
        UUID vendorId,
        Instant timestamp
) {}
