package com.vendex.cardcatalog.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Persists a crash-expiring sync lease plus the last run outcome.
 * The lease prevents several service replicas from writing the same catalog
 * concurrently without tying correctness to an in-memory scheduler lock.
 */
@Repository
public class CatalogSyncStateRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public CatalogSyncStateRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UUID> tryAcquire(String source, Instant now, Duration leaseDuration) {
        UUID owner = UUID.randomUUID();
        var params = new MapSqlParameterSource()
                .addValue("source", source)
                .addValue("owner", owner)
                .addValue("now", Timestamp.from(now))
                .addValue("expires_at", Timestamp.from(now.plus(leaseDuration)));
        return jdbc.query(
                        """
                        UPDATE catalog_sync_state
                        SET lease_owner = :owner,
                            lease_expires_at = :expires_at,
                            last_started_at = :now,
                            last_error = NULL,
                            updated_at = :now
                        WHERE source = :source
                          AND (lease_expires_at IS NULL OR lease_expires_at <= :now)
                        RETURNING lease_owner
                        """,
                        params,
                        (rs, rowNum) -> (UUID) rs.getObject("lease_owner"))
                .stream()
                .findFirst();
    }

    public void markSucceeded(String source, UUID owner, Instant completedAt, int cardCount) {
        int changed = jdbc.update(
                """
                UPDATE catalog_sync_state
                SET lease_owner = NULL,
                    lease_expires_at = NULL,
                    last_completed_at = :completed_at,
                    last_card_count = :card_count,
                    last_error = NULL,
                    updated_at = :completed_at
                WHERE source = :source AND lease_owner = :owner
                """,
                new MapSqlParameterSource()
                        .addValue("source", source)
                        .addValue("owner", owner)
                        .addValue("completed_at", Timestamp.from(completedAt))
                        .addValue("card_count", cardCount));
        requireOwnedLease(changed, source, owner);
    }

    public void markFailed(String source, UUID owner, Instant failedAt, String error) {
        String boundedError = error == null ? "Unknown sync failure"
                : error.substring(0, Math.min(error.length(), 2_000));
        int changed = jdbc.update(
                """
                UPDATE catalog_sync_state
                SET lease_owner = NULL,
                    lease_expires_at = NULL,
                    last_failed_at = :failed_at,
                    last_error = :error,
                    updated_at = :failed_at
                WHERE source = :source AND lease_owner = :owner
                """,
                new MapSqlParameterSource()
                        .addValue("source", source)
                        .addValue("owner", owner)
                        .addValue("failed_at", Timestamp.from(failedAt))
                        .addValue("error", boundedError));
        requireOwnedLease(changed, source, owner);
    }

    private static void requireOwnedLease(int changed, String source, UUID owner) {
        if (changed != 1) {
            throw new IllegalStateException(
                    "catalog sync lease is no longer owned: source=" + source + ", owner=" + owner);
        }
    }
}
