package com.vendex.buylist.repository;

import com.vendex.buylist.domain.CardCondition;
import com.vendex.buylist.domain.WantedCard;
import com.vendex.buylist.domain.WantedCardInput;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class BuyListRepository {

    private static final RowMapper<WantedCard> ROW_MAPPER = (rs, rowNum) -> new WantedCard(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("vendor_id"),
            (UUID) rs.getObject("card_id"),
            CardCondition.valueOf(rs.getString("minimum_condition")),
            rs.getBigDecimal("max_buy_price"),
            rs.getInt("quantity_wanted"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    private final NamedParameterJdbcTemplate jdbc;

    public BuyListRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public WantedCard insert(UUID vendorId, UUID cardId, WantedCardInput input, Instant now) {
        return jdbc.queryForObject(
                """
                INSERT INTO wanted_cards
                    (vendor_id, card_id, minimum_condition, max_buy_price,
                     quantity_wanted, created_at, updated_at)
                VALUES
                    (:vendor_id, :card_id, :minimum_condition, :max_buy_price,
                     :quantity_wanted, :now, :now)
                RETURNING *
                """,
                params(input, now)
                        .addValue("vendor_id", vendorId)
                        .addValue("card_id", cardId),
                ROW_MAPPER
        );
    }

    public Optional<WantedCard> update(UUID wantedCardId, WantedCardInput input, Instant now) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    """
                    UPDATE wanted_cards SET
                        minimum_condition = :minimum_condition,
                        max_buy_price = :max_buy_price,
                        quantity_wanted = :quantity_wanted,
                        updated_at = :now
                    WHERE id = :id
                    RETURNING *
                    """,
                    params(input, now).addValue("id", wantedCardId),
                    ROW_MAPPER
            ));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<WantedCard> findById(UUID wantedCardId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM wanted_cards WHERE id = :id",
                    new MapSqlParameterSource("id", wantedCardId),
                    ROW_MAPPER
            ));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<WantedCard> delete(UUID wantedCardId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "DELETE FROM wanted_cards WHERE id = :id RETURNING *",
                    new MapSqlParameterSource("id", wantedCardId),
                    ROW_MAPPER
            ));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<WantedCard> listForVendor(UUID vendorId, int limit, int offset) {
        return jdbc.query(
                """
                SELECT * FROM wanted_cards
                WHERE vendor_id = :vendor_id
                ORDER BY updated_at DESC, id ASC
                LIMIT :limit OFFSET :offset
                """,
                pageParams(limit, offset).addValue("vendor_id", vendorId),
                ROW_MAPPER
        );
    }

    /** Browseable demand for the active vendor roster projected for an event. */
    public List<WantedCard> listForEvent(
            UUID eventId,
            UUID cardId,
            Collection<CardCondition> minimumConditions,
            BigDecimal minMaxBuyPrice,
            int limit,
            int offset) {
        StringBuilder sql = new StringBuilder("""
                SELECT wc.*
                FROM wanted_cards wc
                JOIN event_vendor_roster roster ON roster.vendor_id = wc.vendor_id
                WHERE roster.event_id = :event_id
                  AND roster.active
                """);
        MapSqlParameterSource parameters = pageParams(limit, offset)
                .addValue("event_id", eventId);
        if (cardId != null) {
            sql.append(" AND wc.card_id = :card_id\n");
            parameters.addValue("card_id", cardId);
        }
        if (minimumConditions != null && !minimumConditions.isEmpty()) {
            sql.append(" AND wc.minimum_condition IN (:minimum_conditions)\n");
            parameters.addValue("minimum_conditions",
                    minimumConditions.stream().map(Enum::name).toList());
        }
        if (minMaxBuyPrice != null) {
            sql.append(" AND wc.max_buy_price >= :min_max_buy_price\n");
            parameters.addValue("min_max_buy_price", minMaxBuyPrice);
        }
        sql.append("""
                ORDER BY wc.max_buy_price DESC, wc.updated_at DESC, wc.id ASC
                LIMIT :limit OFFSET :offset
                """);
        return jdbc.query(sql.toString(), parameters, ROW_MAPPER);
    }

    /**
     * Applies an idempotent, out-of-order-safe roster fact. At equal
     * timestamps an unregistration wins, preventing accidental resurrection.
     */
    public void projectRoster(UUID eventId, UUID vendorId, boolean active, Instant occurredAt) {
        jdbc.update(
                """
                INSERT INTO event_vendor_roster (event_id, vendor_id, active, occurred_at)
                VALUES (:event_id, :vendor_id, :active, :occurred_at)
                ON CONFLICT (event_id, vendor_id) DO UPDATE SET
                    active = EXCLUDED.active,
                    occurred_at = EXCLUDED.occurred_at
                WHERE EXCLUDED.occurred_at > event_vendor_roster.occurred_at
                   OR (EXCLUDED.occurred_at = event_vendor_roster.occurred_at
                       AND event_vendor_roster.active AND NOT EXCLUDED.active)
                """,
                new MapSqlParameterSource()
                        .addValue("event_id", eventId)
                        .addValue("vendor_id", vendorId)
                        .addValue("active", active)
                        .addValue("occurred_at", Timestamp.from(occurredAt))
        );
    }

    private static MapSqlParameterSource params(WantedCardInput input, Instant now) {
        return new MapSqlParameterSource()
                .addValue("minimum_condition", input.minimumCondition().name())
                .addValue("max_buy_price", input.maxBuyPrice())
                .addValue("quantity_wanted", input.quantityWanted())
                .addValue("now", Timestamp.from(now));
    }

    private static MapSqlParameterSource pageParams(int limit, int offset) {
        return new MapSqlParameterSource()
                .addValue("limit", limit)
                .addValue("offset", offset);
    }
}
