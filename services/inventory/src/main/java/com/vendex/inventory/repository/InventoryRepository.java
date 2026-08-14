package com.vendex.inventory.repository;

import com.vendex.inventory.domain.CardCondition;
import com.vendex.inventory.domain.InventoryItem;
import com.vendex.inventory.domain.InventoryItemInput;
import com.vendex.inventory.domain.InventoryPriority;
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
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public class InventoryRepository {

    private static final RowMapper<InventoryItem> ROW_MAPPER = (rs, rowNum) -> new InventoryItem(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("vendor_id"),
            (UUID) rs.getObject("card_id"),
            (UUID) rs.getObject("event_id"),
            CardCondition.valueOf(rs.getString("condition")),
            rs.getString("grading_company"),
            rs.getBigDecimal("grade"),
            rs.getInt("quantity"),
            rs.getBigDecimal("asking_price"),
            InventoryPriority.valueOf(rs.getString("priority").toUpperCase()),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    private final NamedParameterJdbcTemplate jdbc;

    public InventoryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public InventoryItem insert(UUID vendorId, UUID cardId, InventoryItemInput input, Instant now) {
        return jdbc.queryForObject(
                """
                INSERT INTO inventory_items
                    (vendor_id, card_id, event_id, condition, grading_company, grade,
                     quantity, asking_price, priority, created_at, updated_at)
                VALUES
                    (:vendor_id, :card_id, :event_id, :condition, :grading_company, :grade,
                     :quantity, :asking_price, :priority, :now, :now)
                RETURNING *
                """,
                params(vendorId, cardId, input, now),
                ROW_MAPPER
        );
    }

    public Optional<InventoryItem> update(UUID itemId, InventoryItemInput input, Instant now) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    """
                    UPDATE inventory_items SET
                        event_id = :event_id,
                        condition = :condition,
                        grading_company = :grading_company,
                        grade = :grade,
                        quantity = :quantity,
                        asking_price = :asking_price,
                        priority = :priority,
                        updated_at = :now
                    WHERE id = :item_id
                    RETURNING *
                    """,
                    inputParams(input, now).addValue("item_id", itemId),
                    ROW_MAPPER
            ));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<InventoryItem> findById(UUID itemId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM inventory_items WHERE id = :id",
                    new MapSqlParameterSource("id", itemId),
                    ROW_MAPPER
            ));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<InventoryItem> delete(UUID itemId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "DELETE FROM inventory_items WHERE id = :id RETURNING *",
                    new MapSqlParameterSource("id", itemId),
                    ROW_MAPPER
            ));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<InventoryItem> listForVendor(UUID vendorId, int limit, int offset) {
        return jdbc.query(
                """
                SELECT * FROM inventory_items
                WHERE vendor_id = :vendor_id
                ORDER BY updated_at DESC, id ASC
                LIMIT :limit OFFSET :offset
                """,
                pageParams(limit, offset).addValue("vendor_id", vendorId),
                ROW_MAPPER
        );
    }

    /** Event-specific items plus the vendor's always-available inventory. */
    public List<InventoryItem> listForVendorAndEvent(
            UUID vendorId, UUID eventId, int limit, int offset) {
        return jdbc.query(
                """
                SELECT * FROM inventory_items
                WHERE vendor_id = :vendor_id
                  AND (event_id = :event_id OR event_id IS NULL)
                ORDER BY updated_at DESC, id ASC
                LIMIT :limit OFFSET :offset
                """,
                pageParams(limit, offset)
                        .addValue("vendor_id", vendorId)
                        .addValue("event_id", eventId),
                ROW_MAPPER
        );
    }

    /** Query-only supply view; never supports a browse-all path. */
    public List<InventoryItem> searchForEvent(
            UUID eventId, UUID cardId, Collection<CardCondition> conditions,
            BigDecimal maxAskingPrice, int limit, int offset) {
        StringBuilder sql = new StringBuilder("""
                SELECT * FROM inventory_items
                WHERE card_id = :card_id
                  AND (event_id = :event_id OR event_id IS NULL)
                """);
        MapSqlParameterSource params = pageParams(limit, offset)
                .addValue("card_id", cardId)
                .addValue("event_id", eventId);
        if (conditions != null && !conditions.isEmpty()) {
            sql.append(" AND condition IN (:conditions)\n");
            params.addValue("conditions", conditions.stream().map(Enum::name).toList());
        }
        if (maxAskingPrice != null) {
            sql.append(" AND asking_price <= :max_asking_price\n");
            params.addValue("max_asking_price", maxAskingPrice);
        }
        sql.append(" ORDER BY asking_price ASC, condition ASC, id ASC LIMIT :limit OFFSET :offset");
        return jdbc.query(sql.toString(), params, ROW_MAPPER);
    }

    private static MapSqlParameterSource params(
            UUID vendorId, UUID cardId, InventoryItemInput input, Instant now) {
        return inputParams(input, now)
                .addValue("vendor_id", vendorId)
                .addValue("card_id", cardId);
    }

    private static MapSqlParameterSource inputParams(InventoryItemInput input, Instant now) {
        return new MapSqlParameterSource()
                .addValue("event_id", input.eventId())
                .addValue("condition", input.condition().name())
                .addValue("grading_company", input.gradingCompany())
                .addValue("grade", input.grade())
                .addValue("quantity", input.quantity())
                .addValue("asking_price", input.askingPrice())
                .addValue("priority", input.priority().name().toLowerCase(Locale.ROOT))
                .addValue("now", Timestamp.from(now));
    }

    private static MapSqlParameterSource pageParams(int limit, int offset) {
        return new MapSqlParameterSource()
                .addValue("limit", limit)
                .addValue("offset", offset);
    }
}
