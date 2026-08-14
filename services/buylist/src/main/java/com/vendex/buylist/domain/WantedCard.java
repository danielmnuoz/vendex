package com.vendex.buylist.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WantedCard(
        UUID id,
        UUID vendorId,
        UUID cardId,
        CardCondition minimumCondition,
        BigDecimal maxBuyPrice,
        int quantityWanted,
        Instant createdAt,
        Instant updatedAt
) {}
