package com.vendex.overlap.repository;

import com.vendex.overlap.domain.CardCondition;
import com.vendex.overlap.domain.InventoryPriority;
import com.vendex.overlap.domain.Overlap;
import com.vendex.overlap.domain.OverlapCandidate;
import com.vendex.overlap.domain.SavedOverlap;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class OverlapRepository {

    private static final RowMapper<Overlap> OVERLAP_MAPPER = (rs, rowNum) -> mapOverlap(rs, "");

    private final NamedParameterJdbcTemplate jdbc;

    public OverlapRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Overlap> findActivePair(UUID eventId, UUID sellerId, UUID buyerId) {
        return jdbc.query("""
                SELECT * FROM overlap_opportunities
                WHERE event_id = :event_id
                  AND seller_vendor_id = :seller_id
                  AND buyer_vendor_id = :buyer_id
                  AND active
                ORDER BY card_id
                """, pairParams(eventId, sellerId, buyerId), OVERLAP_MAPPER);
    }

    public Overlap upsert(OverlapCandidate candidate, Instant now) {
        return jdbc.queryForObject("""
                INSERT INTO overlap_opportunities (
                    id, event_id, buyer_vendor_id, seller_vendor_id, card_id,
                    inventory_item_id, wanted_card_id, seller_condition,
                    minimum_condition, available_quantity, quantity_wanted,
                    asking_price, max_buy_price, inventory_priority, score,
                    active, created_at, updated_at)
                VALUES (
                    :id, :event_id, :buyer_id, :seller_id, :card_id,
                    :inventory_item_id, :wanted_card_id, :seller_condition,
                    :minimum_condition, :available_quantity, :quantity_wanted,
                    :asking_price, :max_buy_price, :inventory_priority, :score,
                    TRUE, :now, :now)
                ON CONFLICT (event_id, buyer_vendor_id, seller_vendor_id, card_id)
                DO UPDATE SET
                    inventory_item_id = EXCLUDED.inventory_item_id,
                    wanted_card_id = EXCLUDED.wanted_card_id,
                    seller_condition = EXCLUDED.seller_condition,
                    minimum_condition = EXCLUDED.minimum_condition,
                    available_quantity = EXCLUDED.available_quantity,
                    quantity_wanted = EXCLUDED.quantity_wanted,
                    asking_price = EXCLUDED.asking_price,
                    max_buy_price = EXCLUDED.max_buy_price,
                    inventory_priority = EXCLUDED.inventory_priority,
                    score = EXCLUDED.score,
                    active = TRUE,
                    updated_at = EXCLUDED.updated_at
                RETURNING *
                """, candidateParams(candidate, now), OVERLAP_MAPPER);
    }

    public Optional<Overlap> deactivate(UUID overlapId, Instant now) {
        return optional("""
                UPDATE overlap_opportunities SET active = FALSE, updated_at = :now
                WHERE id = :id AND active
                RETURNING *
                """, new MapSqlParameterSource("id", overlapId)
                .addValue("now", Timestamp.from(now)));
    }

    public List<Overlap> deactivatePair(
            UUID eventId, UUID sellerId, UUID buyerId, Instant now) {
        return jdbc.query("""
                UPDATE overlap_opportunities SET active = FALSE, updated_at = :now
                WHERE event_id = :event_id
                  AND seller_vendor_id = :seller_id
                  AND buyer_vendor_id = :buyer_id
                  AND active
                RETURNING *
                """, pairParams(eventId, sellerId, buyerId)
                .addValue("now", Timestamp.from(now)), OVERLAP_MAPPER);
    }

    public List<Overlap> deactivateVendor(UUID eventId, UUID vendorId, Instant now) {
        return jdbc.query("""
                UPDATE overlap_opportunities SET active = FALSE, updated_at = :now
                WHERE event_id = :event_id
                  AND (seller_vendor_id = :vendor_id OR buyer_vendor_id = :vendor_id)
                  AND active
                RETURNING *
                """, new MapSqlParameterSource("event_id", eventId)
                .addValue("vendor_id", vendorId)
                .addValue("now", Timestamp.from(now)), OVERLAP_MAPPER);
    }

    public Optional<Overlap> findById(UUID id) {
        return optional("SELECT * FROM overlap_opportunities WHERE id = :id",
                new MapSqlParameterSource("id", id));
    }

    public List<Overlap> listForVendor(
            UUID vendorId, UUID eventId, int limit, int offset) {
        return jdbc.query("""
                SELECT * FROM overlap_opportunities
                WHERE event_id = :event_id
                  AND (buyer_vendor_id = :vendor_id OR seller_vendor_id = :vendor_id)
                  AND active
                ORDER BY score DESC, updated_at DESC, id
                LIMIT :limit OFFSET :offset
                """, pageParams(vendorId, eventId, limit, offset), OVERLAP_MAPPER);
    }

    public SaveResult save(UUID vendorId, Overlap overlap, Instant now) {
        int inserted = jdbc.update("""
                INSERT INTO saved_overlaps (vendor_id, overlap_id, event_id, created_at)
                VALUES (:vendor_id, :overlap_id, :event_id, :now)
                ON CONFLICT (vendor_id, overlap_id) DO NOTHING
                """, new MapSqlParameterSource("vendor_id", vendorId)
                .addValue("overlap_id", overlap.id())
                .addValue("event_id", overlap.eventId())
                .addValue("now", Timestamp.from(now)));
        return new SaveResult(findSaved(vendorId, overlap.id()).orElseThrow(), inserted == 1);
    }

    public List<SavedOverlap> listSaved(
            UUID vendorId, UUID eventId, int limit, int offset) {
        return jdbc.query(savedSelect() + """
                WHERE s.vendor_id = :vendor_id AND s.event_id = :event_id
                ORDER BY s.created_at DESC, s.id
                LIMIT :limit OFFSET :offset
                """, pageParams(vendorId, eventId, limit, offset), this::mapSaved);
    }

    private Optional<SavedOverlap> findSaved(UUID vendorId, UUID overlapId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(savedSelect() + """
                    WHERE s.vendor_id = :vendor_id AND s.overlap_id = :overlap_id
                    """, new MapSqlParameterSource("vendor_id", vendorId)
                    .addValue("overlap_id", overlapId), this::mapSaved));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private Optional<Overlap> optional(String sql, MapSqlParameterSource parameters) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, parameters, OVERLAP_MAPPER));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private SavedOverlap mapSaved(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new SavedOverlap(
                (UUID) rs.getObject("saved_id"),
                (UUID) rs.getObject("saved_vendor_id"),
                mapOverlap(rs, "overlap_"),
                rs.getTimestamp("saved_created_at").toInstant());
    }

    private static Overlap mapOverlap(java.sql.ResultSet rs, String prefix)
            throws java.sql.SQLException {
        return new Overlap(
                (UUID) rs.getObject(prefix + "id"),
                (UUID) rs.getObject(prefix + "event_id"),
                (UUID) rs.getObject(prefix + "buyer_vendor_id"),
                (UUID) rs.getObject(prefix + "seller_vendor_id"),
                (UUID) rs.getObject(prefix + "card_id"),
                (UUID) rs.getObject(prefix + "inventory_item_id"),
                (UUID) rs.getObject(prefix + "wanted_card_id"),
                CardCondition.parse(rs.getString(prefix + "seller_condition")),
                CardCondition.parse(rs.getString(prefix + "minimum_condition")),
                rs.getInt(prefix + "available_quantity"),
                rs.getInt(prefix + "quantity_wanted"),
                rs.getBigDecimal(prefix + "asking_price"),
                rs.getBigDecimal(prefix + "max_buy_price"),
                InventoryPriority.parse(rs.getString(prefix + "inventory_priority")),
                rs.getBigDecimal(prefix + "score"),
                rs.getBoolean(prefix + "active"),
                rs.getTimestamp(prefix + "created_at").toInstant(),
                rs.getTimestamp(prefix + "updated_at").toInstant());
    }

    private static MapSqlParameterSource candidateParams(
            OverlapCandidate candidate, Instant now) {
        return new MapSqlParameterSource()
                .addValue("id", candidate.id())
                .addValue("event_id", candidate.eventId())
                .addValue("buyer_id", candidate.buyerVendorId())
                .addValue("seller_id", candidate.sellerVendorId())
                .addValue("card_id", candidate.cardId())
                .addValue("inventory_item_id", candidate.inventory().itemId())
                .addValue("wanted_card_id", candidate.demand().wantedCardId())
                .addValue("seller_condition", candidate.inventory().condition().name())
                .addValue("minimum_condition", candidate.demand().minimumCondition().name())
                .addValue("available_quantity", candidate.inventory().quantity())
                .addValue("quantity_wanted", candidate.demand().quantityWanted())
                .addValue("asking_price", candidate.inventory().askingPrice())
                .addValue("max_buy_price", candidate.demand().maxBuyPrice())
                .addValue("inventory_priority",
                        candidate.inventory().priority().name().toLowerCase())
                .addValue("score", candidate.score())
                .addValue("now", Timestamp.from(now));
    }

    private static MapSqlParameterSource pairParams(
            UUID eventId, UUID sellerId, UUID buyerId) {
        return new MapSqlParameterSource("event_id", eventId)
                .addValue("seller_id", sellerId)
                .addValue("buyer_id", buyerId);
    }

    private static MapSqlParameterSource pageParams(
            UUID vendorId, UUID eventId, int limit, int offset) {
        return new MapSqlParameterSource("vendor_id", vendorId)
                .addValue("event_id", eventId)
                .addValue("limit", limit)
                .addValue("offset", offset);
    }

    private static String savedSelect() {
        return """
                SELECT
                    s.id AS saved_id,
                    s.vendor_id AS saved_vendor_id,
                    s.created_at AS saved_created_at,
                    o.id AS overlap_id,
                    o.event_id AS overlap_event_id,
                    o.buyer_vendor_id AS overlap_buyer_vendor_id,
                    o.seller_vendor_id AS overlap_seller_vendor_id,
                    o.card_id AS overlap_card_id,
                    o.inventory_item_id AS overlap_inventory_item_id,
                    o.wanted_card_id AS overlap_wanted_card_id,
                    o.seller_condition AS overlap_seller_condition,
                    o.minimum_condition AS overlap_minimum_condition,
                    o.available_quantity AS overlap_available_quantity,
                    o.quantity_wanted AS overlap_quantity_wanted,
                    o.asking_price AS overlap_asking_price,
                    o.max_buy_price AS overlap_max_buy_price,
                    o.inventory_priority AS overlap_inventory_priority,
                    o.score AS overlap_score,
                    o.active AS overlap_active,
                    o.created_at AS overlap_created_at,
                    o.updated_at AS overlap_updated_at
                FROM saved_overlaps s
                JOIN overlap_opportunities o ON o.id = s.overlap_id
                """;
    }

    public record SaveResult(SavedOverlap savedOverlap, boolean created) {}
}
