package com.vendex.overlap.service;

import com.vendex.overlap.domain.CardCondition;
import com.vendex.overlap.domain.DemandSnapshot;
import com.vendex.overlap.domain.InventoryPriority;
import com.vendex.overlap.domain.InventorySnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OverlapScorerTest {

    private final OverlapScorer scorer = new OverlapScorer();

    @Test
    void scoresPriceConditionQuantityAndLiquidation() {
        InventorySnapshot inventory = inventory(
                CardCondition.NM, 2, "90.00", InventoryPriority.LIQUIDATE);
        DemandSnapshot demand = demand(CardCondition.LP, "100.00", 4);

        assertThat(scorer.score(inventory, demand)).hasValue(new BigDecimal("80.25"));
    }

    @Test
    void rejectsPriceAndConditionMismatches() {
        assertThat(scorer.score(
                inventory(CardCondition.MP, 1, "10.00", InventoryPriority.NORMAL),
                demand(CardCondition.LP, "20.00", 1))).isEmpty();
        assertThat(scorer.score(
                inventory(CardCondition.NM, 1, "21.00", InventoryPriority.NORMAL),
                demand(CardCondition.LP, "20.00", 1))).isEmpty();
    }

    @Test
    void zeroPriceDemandOnlyAcceptsFreeSupply() {
        assertThat(scorer.score(
                inventory(CardCondition.NM, 1, "0.00", InventoryPriority.NORMAL),
                demand(CardCondition.NM, "0.00", 1)))
                .hasValue(new BigDecimal("80.00"));
    }

    private static InventorySnapshot inventory(
            CardCondition condition, int quantity, String price, InventoryPriority priority) {
        return new InventorySnapshot(UUID.randomUUID(), UUID.randomUUID(), null, CARD_ID,
                condition, quantity, new BigDecimal(price), priority, true, Instant.EPOCH);
    }

    private static DemandSnapshot demand(
            CardCondition condition, String price, int quantity) {
        return new DemandSnapshot(UUID.randomUUID(), UUID.randomUUID(), CARD_ID, condition,
                new BigDecimal(price), quantity, true, Instant.EPOCH);
    }

    private static final UUID CARD_ID = UUID.randomUUID();
}
