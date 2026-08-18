package com.vendex.overlap.repository;

import com.vendex.overlap.domain.CardCondition;
import com.vendex.overlap.domain.DemandSnapshot;
import com.vendex.overlap.domain.InventoryPriority;
import com.vendex.overlap.domain.InventorySnapshot;
import com.vendex.overlap.domain.Overlap;
import com.vendex.overlap.domain.OverlapCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@Testcontainers
@Import(OverlapRepository.class)
@ImportAutoConfiguration({FlywayAutoConfiguration.class, JdbcTemplateAutoConfiguration.class})
class OverlapRepositoryIT {

    private static final Instant NOW = Instant.parse("2026-08-17T12:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired OverlapRepository repository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE TABLE saved_overlaps, overlap_opportunities");
    }

    @Test
    void upsertReactivatesStableOpportunityAndVendorReadsAreSymmetric() {
        OverlapCandidate initial = candidate("70.00", "80.00");
        Overlap first = repository.upsert(initial, NOW);
        repository.deactivate(first.id(), NOW.plusSeconds(1));
        Overlap reactivated = repository.upsert(candidate(initial, "75.00", "85.00"),
                NOW.plusSeconds(2));

        assertThat(reactivated.id()).isEqualTo(first.id());
        assertThat(reactivated.active()).isTrue();
        assertThat(reactivated.askingPrice()).isEqualByComparingTo("75.00");
        assertThat(repository.listForVendor(initial.buyerVendorId(), initial.eventId(), 10, 0))
                .extracting(Overlap::id).containsExactly(first.id());
        assertThat(repository.listForVendor(initial.sellerVendorId(), initial.eventId(), 10, 0))
                .extracting(Overlap::id).containsExactly(first.id());
    }

    @Test
    void saveIsIdempotentAndRetainsInactiveSnapshot() {
        Overlap active = repository.upsert(candidate("20.00", "30.00"), NOW);
        var first = repository.save(active.buyerVendorId(), active, NOW.plusSeconds(1));
        var second = repository.save(active.buyerVendorId(), active, NOW.plusSeconds(2));
        repository.deactivate(active.id(), NOW.plusSeconds(3));

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.savedOverlap().id()).isEqualTo(first.savedOverlap().id());
        assertThat(repository.listSaved(active.buyerVendorId(), active.eventId(), 10, 0))
                .singleElement().satisfies(saved -> {
                    assertThat(saved.id()).isEqualTo(first.savedOverlap().id());
                    assertThat(saved.overlap().active()).isFalse();
                });
    }

    @Test
    void retireVendorRemovesBothBuyerAndSellerDirections() {
        OverlapCandidate first = candidate("20.00", "30.00");
        repository.upsert(first, NOW);
        OverlapCandidate reverse = new OverlapCandidate(
                UUID.randomUUID(), first.eventId(), first.sellerVendorId(),
                first.buyerVendorId(), UUID.randomUUID(),
                inventory(first.buyerVendorId(), UUID.randomUUID(), "10.00"),
                demand(first.sellerVendorId(), UUID.randomUUID(), "20.00"),
                new BigDecimal("75.00"));
        repository.upsert(reverse, NOW);

        assertThat(repository.deactivateVendor(
                first.eventId(), first.buyerVendorId(), NOW.plusSeconds(1))).hasSize(2);
    }

    private static OverlapCandidate candidate(String asking, String max) {
        UUID eventId = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID card = UUID.randomUUID();
        return new OverlapCandidate(UUID.randomUUID(), eventId, buyer, seller, card,
                inventory(seller, card, asking), demand(buyer, card, max),
                new BigDecimal("80.00"));
    }

    private static OverlapCandidate candidate(
            OverlapCandidate original, String asking, String max) {
        return new OverlapCandidate(original.id(), original.eventId(),
                original.buyerVendorId(), original.sellerVendorId(), original.cardId(),
                inventory(original.sellerVendorId(), original.cardId(), asking),
                demand(original.buyerVendorId(), original.cardId(), max),
                new BigDecimal("85.00"));
    }

    private static InventorySnapshot inventory(UUID vendor, UUID card, String price) {
        return new InventorySnapshot(UUID.randomUUID(), vendor, null, card,
                CardCondition.NM, 2, new BigDecimal(price), InventoryPriority.NORMAL,
                true, NOW);
    }

    private static DemandSnapshot demand(UUID vendor, UUID card, String max) {
        return new DemandSnapshot(UUID.randomUUID(), vendor, card, CardCondition.LP,
                new BigDecimal(max), 1, true, NOW);
    }
}
