package com.vendex.cardcatalog.repository;

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

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@Testcontainers
@Import(CatalogSyncStateRepository.class)
@ImportAutoConfiguration({FlywayAutoConfiguration.class, JdbcTemplateAutoConfiguration.class})
class CatalogSyncStateRepositoryIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    CatalogSyncStateRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void resetState() {
        jdbc.update("""
                UPDATE catalog_sync_state
                SET lease_owner = NULL, lease_expires_at = NULL,
                    last_started_at = NULL, last_completed_at = NULL,
                    last_failed_at = NULL, last_card_count = NULL,
                    last_error = NULL
                WHERE source = 'tcgdex'
                """);
    }

    @Test
    void leaseIsExclusiveUntilItExpires() {
        Instant now = Instant.parse("2026-08-18T12:00:00Z");
        var first = repository.tryAcquire("tcgdex", now, Duration.ofHours(2));

        assertThat(first).isPresent();
        assertThat(repository.tryAcquire("tcgdex", now.plusSeconds(60), Duration.ofHours(2)))
                .isEmpty();
        assertThat(repository.tryAcquire("tcgdex", now.plus(Duration.ofHours(2)), Duration.ofHours(2)))
                .isPresent()
                .containsInstanceOf(java.util.UUID.class);
    }

    @Test
    void completionReleasesLeaseAndRecordsCardCount() {
        Instant now = Instant.parse("2026-08-18T12:00:00Z");
        var owner = repository.tryAcquire("tcgdex", now, Duration.ofHours(2)).orElseThrow();

        repository.markSucceeded("tcgdex", owner, now.plusSeconds(30), 20_431);

        assertThat(jdbc.queryForObject(
                "SELECT last_card_count FROM catalog_sync_state WHERE source = 'tcgdex'",
                Integer.class)).isEqualTo(20_431);
        assertThat(repository.tryAcquire("tcgdex", now.plusSeconds(31), Duration.ofHours(2)))
                .isPresent();
    }

    @Test
    void failureReleasesLeaseAndRecordsBoundedError() {
        Instant now = Instant.parse("2026-08-18T12:00:00Z");
        var owner = repository.tryAcquire("tcgdex", now, Duration.ofHours(2)).orElseThrow();
        String longError = "x".repeat(2_100);

        repository.markFailed("tcgdex", owner, now.plusSeconds(30), longError);

        assertThat(jdbc.queryForObject(
                "SELECT last_error FROM catalog_sync_state WHERE source = 'tcgdex'",
                String.class))
                .hasSize(2_000);
        assertThat(jdbc.queryForObject(
                "SELECT lease_owner FROM catalog_sync_state WHERE source = 'tcgdex'",
                java.util.UUID.class))
                .isNull();
        assertThat(repository.tryAcquire("tcgdex", now.plusSeconds(31), Duration.ofHours(2)))
                .isPresent();
    }
}
