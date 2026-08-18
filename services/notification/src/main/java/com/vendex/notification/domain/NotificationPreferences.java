package com.vendex.notification.domain;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record NotificationPreferences(
        UUID userId,
        boolean inAppEnabled,
        boolean emailEnabled,
        boolean overlapBuyListEnabled,
        boolean overlapLiquidateEnabled,
        boolean savedOverlapActiveEnabled,
        DigestMode digestMode,
        Set<UUID> mutedEventIds,
        Instant updatedAt
) {
    public boolean enabled(NotificationTrigger trigger) {
        return switch (trigger) {
            case OVERLAP_BUYLIST -> overlapBuyListEnabled;
            case OVERLAP_LIQUIDATE -> overlapLiquidateEnabled;
            case SAVED_OVERLAP_ACTIVE -> savedOverlapActiveEnabled;
        };
    }
}
