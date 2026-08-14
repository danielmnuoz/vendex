package com.vendex.events.contract;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.util.UUID;

/**
 * Published on {@link Topics#BUYLIST_UPDATED} whenever a vendor's buy list
 * changes. Buy lists are persistent (not event-scoped), so there is
 * deliberately no {@code eventId} here — the Phase 3 overlap engine checks a
 * vendor's buy list against every event they're registered for.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record BuyListUpdated(
        UUID vendorId,
        UUID cardId,
        Action action,
        Instant timestamp
) {}
