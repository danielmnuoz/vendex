package com.vendex.inventory.repository;

import com.vendex.inventory.domain.CardCondition;
import com.vendex.inventory.domain.InventoryItem;
import com.vendex.inventory.domain.InventoryItemInput;
import com.vendex.inventory.domain.InventoryPriority;
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
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@Testcontainers
@Import(InventoryRepository.class)
@ImportAutoConfiguration({FlywayAutoConfiguration.class, JdbcTemplateAutoConfiguration.class})
@TestPropertySource(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/outbox")
class InventoryRepositoryIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired InventoryRepository repository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE TABLE inventory_items");
    }

    @Test
    void persistsUpdatesAndRemovesInventory() {
        UUID vendorId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-08-14T10:00:00Z");
        InventoryItem created = repository.insert(vendorId, cardId,
                input(null, CardCondition.NM, "12.00"), createdAt);

        InventoryItem updated = repository.update(created.id(),
                input(null, CardCondition.LP, "10.50"), createdAt.plusSeconds(60)).orElseThrow();

        assertThat(repository.findById(created.id())).contains(updated);
        assertThat(updated.condition()).isEqualTo(CardCondition.LP);
        assertThat(updated.askingPrice()).isEqualByComparingTo("10.50");
        assertThat(repository.delete(created.id())).contains(updated);
        assertThat(repository.findById(created.id())).isEmpty();
    }

    @Test
    void eventViewsIncludeAlwaysAvailableButExcludeOtherEvents() {
        UUID vendorId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        UUID requestedEvent = UUID.randomUUID();
        UUID otherEvent = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-14T10:00:00Z");
        InventoryItem always = repository.insert(vendorId, cardId,
                input(null, CardCondition.NM, "20.00"), now);
        InventoryItem scoped = repository.insert(vendorId, cardId,
                input(requestedEvent, CardCondition.LP, "15.00"), now.plusSeconds(1));
        repository.insert(vendorId, cardId,
                input(otherEvent, CardCondition.MP, "5.00"), now.plusSeconds(2));

        assertThat(repository.listForVendorAndEvent(vendorId, requestedEvent, 10, 0))
                .extracting(InventoryItem::id)
                .containsExactly(scoped.id(), always.id());
    }

    @Test
    void buyerSearchIsCardScopedAndAppliesConditionAndPriceFilters() {
        UUID vendorId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        UUID otherCardId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-14T10:00:00Z");
        InventoryItem matching = repository.insert(vendorId, cardId,
                input(eventId, CardCondition.LP, "14.00"), now);
        repository.insert(vendorId, cardId,
                input(eventId, CardCondition.NM, "25.00"), now);
        repository.insert(vendorId, otherCardId,
                input(eventId, CardCondition.LP, "8.00"), now);

        List<InventoryItem> found = repository.searchForEvent(eventId, cardId,
                List.of(CardCondition.LP), new BigDecimal("20.00"), 10, 0);

        assertThat(found).extracting(InventoryItem::id).containsExactly(matching.id());
    }

    private static InventoryItemInput input(UUID eventId, CardCondition condition, String price) {
        return new InventoryItemInput(eventId, condition, null, null, 1,
                new BigDecimal(price), InventoryPriority.NORMAL);
    }
}
