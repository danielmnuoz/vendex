package com.vendex.overlap.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DemandSnapshot(
        UUID wantedCardId,
        UUID vendorId,
        UUID cardId,
        CardCondition minimumCondition,
        BigDecimal maxBuyPrice,
        int quantityWanted,
        boolean active,
        Instant occurredAt
) {}
