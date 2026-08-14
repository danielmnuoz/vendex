package com.vendex.buylist.repository;

import com.vendex.buylist.domain.CardCondition;
import com.vendex.buylist.domain.WantedCard;
import com.vendex.buylist.domain.WantedCardInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JdbcTest
@Testcontainers
@Import(BuyListRepository.class)
@ImportAutoConfiguration({FlywayAutoConfiguration.class, JdbcTemplateAutoConfiguration.class})
@TestPropertySource(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/outbox")
class BuyListRepositoryIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired BuyListRepository repository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE TABLE wanted_cards, event_vendor_roster");
    }

    @Test
    void persistsUpdatesAndRemoves() {
        UUID vendorId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-14T12:00:00Z");
        WantedCard created = repository.insert(vendorId, cardId, input("20.00"), now);
        WantedCard updated = repository.update(created.id(), input("25.00"),
                now.plusSeconds(1)).orElseThrow();

        assertThat(updated.maxBuyPrice()).isEqualByComparingTo("25.00");
        assertThat(repository.findById(created.id())).contains(updated);
        assertThat(repository.delete(created.id())).contains(updated);
        assertThat(repository.findById(created.id())).isEmpty();
    }

    @Test
    void enforcesOneCardPerVendor() {
        UUID vendorId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-14T12:00:00Z");
        repository.insert(vendorId, cardId, input("20.00"), now);

        assertThatThrownBy(() -> repository.insert(
                vendorId, cardId, input("30.00"), now.plusSeconds(2)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void eventBrowseIncludesOnlyActiveProjectedVendorsAndAppliesFilters() {
        UUID eventId = UUID.randomUUID();
        UUID otherEventId = UUID.randomUUID();
        UUID activeVendor = UUID.randomUUID();
        UUID inactiveVendor = UUID.randomUUID();
        UUID otherVendor = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-14T12:00:00Z");
        WantedCard matching = repository.insert(activeVendor, cardId, input("30.00"), now);
        repository.insert(inactiveVendor, cardId, input("40.00"), now);
        repository.insert(otherVendor, cardId, input("50.00"), now);
        repository.projectRoster(eventId, activeVendor, true, now);
        repository.projectRoster(eventId, inactiveVendor, false, now);
        repository.projectRoster(otherEventId, otherVendor, true, now);

        List<WantedCard> found = repository.listForEvent(eventId, cardId,
                List.of(CardCondition.LP), new BigDecimal("25.00"), 10, 0);

        assertThat(found).extracting(WantedCard::id).containsExactly(matching.id());
    }

    @Test
    void newerRosterFactWinsAndRemovalWinsTimestampTies() {
        UUID eventId = UUID.randomUUID();
        UUID vendorId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        Instant newer = Instant.parse("2026-08-14T12:01:00Z");
        repository.insert(vendorId, cardId, input("20.00"), newer);

        repository.projectRoster(eventId, vendorId, false, newer);
        repository.projectRoster(eventId, vendorId, true, newer.minusSeconds(30));
        repository.projectRoster(eventId, vendorId, true, newer);

        assertThat(repository.listForEvent(eventId, null, List.of(), null, 10, 0)).isEmpty();

        repository.projectRoster(eventId, vendorId, true, newer.plusSeconds(30));
        assertThat(repository.listForEvent(eventId, null, List.of(), null, 10, 0))
                .hasSize(1);
    }

    private static WantedCardInput input(String price) {
        return new WantedCardInput(CardCondition.LP, new BigDecimal(price), 2);
    }
}
