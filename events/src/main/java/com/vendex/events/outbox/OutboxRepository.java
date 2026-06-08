package com.vendex.events.outbox;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.UUID;

/**
 * JDBC access to a service's {@code outbox} table. Every producing service has
 * its own {@code outbox} table in its own database (DDL shipped as the shared
 * Flyway migration at {@code classpath:db/outbox/V1000__outbox.sql}); this
 * class is wired against that service's {@link NamedParameterJdbcTemplate}, so
 * {@link #insert} enlists in whatever transaction the caller already has open.
 */
public class OutboxRepository {

    private static final RowMapper<OutboxRecord> ROW_MAPPER = (rs, rowNum) -> new OutboxRecord(
            (UUID) rs.getObject("id"),
            rs.getString("aggregate_type"),
            rs.getString("aggregate_id"),
            rs.getString("topic"),
            rs.getString("partition_key"),
            rs.getString("payload")
    );

    private final NamedParameterJdbcTemplate jdbc;

    public OutboxRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Insert a pending event. MUST be called inside the same transaction as the
     * business write it accompanies — that's the whole point of the outbox: the
     * row and the domain change commit (or roll back) together.
     *
     * @return the generated outbox row id
     */
    public UUID insert(String aggregateType, String aggregateId, String topic,
                       String partitionKey, String payloadJson) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("aggregate_type", aggregateType)
                .addValue("aggregate_id", aggregateId)
                .addValue("topic", topic)
                .addValue("partition_key", partitionKey)
                .addValue("payload", payloadJson);
        return jdbc.queryForObject(
                """
                INSERT INTO outbox (aggregate_type, aggregate_id, topic, partition_key, payload)
                VALUES (:aggregate_type, :aggregate_id, :topic, :partition_key, CAST(:payload AS jsonb))
                RETURNING id
                """,
                params,
                UUID.class
        );
    }

    /**
     * Oldest-first batch of not-yet-published rows. Ordered by {@code
     * created_at, id} so per-key publish order matches write order.
     */
    public List<OutboxRecord> fetchUnpublished(int limit) {
        return jdbc.query(
                """
                SELECT id, aggregate_type, aggregate_id, topic, partition_key, payload::text AS payload
                FROM outbox
                WHERE published_at IS NULL
                ORDER BY created_at, id
                LIMIT :limit
                """,
                new MapSqlParameterSource("limit", limit),
                ROW_MAPPER
        );
    }

    /** Stamp a row published once its Kafka send has been acknowledged. */
    public void markPublished(UUID id) {
        jdbc.update(
                "UPDATE outbox SET published_at = NOW() WHERE id = :id",
                new MapSqlParameterSource("id", id)
        );
    }
}
