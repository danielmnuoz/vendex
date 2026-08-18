package com.vendex.overlap.service;

import com.vendex.overlap.domain.DemandSnapshot;
import com.vendex.overlap.domain.InventoryPriority;
import com.vendex.overlap.domain.InventorySnapshot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Component
public class OverlapScorer {

    private static final BigDecimal PRICE_WEIGHT = new BigDecimal("35");
    private static final BigDecimal CONDITION_BASE = new BigDecimal("20");
    private static final BigDecimal CONDITION_BONUS = new BigDecimal("5");
    private static final BigDecimal QUANTITY_WEIGHT = new BigDecimal("25");
    private static final BigDecimal LIQUIDATE_WEIGHT = new BigDecimal("15");

    /** Returns empty when price or condition makes the pair non-actionable. */
    public Optional<BigDecimal> score(InventorySnapshot inventory, DemandSnapshot demand) {
        if (!inventory.active() || !demand.active()
                || !inventory.cardId().equals(demand.cardId())
                || !inventory.condition().meets(demand.minimumCondition())
                || inventory.askingPrice().compareTo(demand.maxBuyPrice()) > 0) {
            return Optional.empty();
        }

        BigDecimal price = demand.maxBuyPrice().signum() == 0
                ? PRICE_WEIGHT
                : inventory.askingPrice()
                    .divide(demand.maxBuyPrice(), 8, RoundingMode.HALF_UP)
                    .min(BigDecimal.ONE)
                    .multiply(PRICE_WEIGHT);
        BigDecimal condition = CONDITION_BASE.add(
                BigDecimal.valueOf(inventory.condition()
                                .qualityMargin(demand.minimumCondition()))
                        .divide(BigDecimal.valueOf(4), 8, RoundingMode.HALF_UP)
                        .multiply(CONDITION_BONUS));
        BigDecimal quantity = BigDecimal.valueOf(
                        Math.min(inventory.quantity(), demand.quantityWanted()))
                .divide(BigDecimal.valueOf(demand.quantityWanted()), 8, RoundingMode.HALF_UP)
                .multiply(QUANTITY_WEIGHT);
        BigDecimal priority = inventory.priority() == InventoryPriority.LIQUIDATE
                ? LIQUIDATE_WEIGHT : BigDecimal.ZERO;
        return Optional.of(price.add(condition).add(quantity).add(priority)
                .setScale(2, RoundingMode.HALF_UP));
    }
}
