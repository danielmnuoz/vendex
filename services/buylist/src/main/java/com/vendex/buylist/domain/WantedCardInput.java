package com.vendex.buylist.domain;

import java.math.BigDecimal;

public record WantedCardInput(
        CardCondition minimumCondition,
        BigDecimal maxBuyPrice,
        int quantityWanted
) {}
