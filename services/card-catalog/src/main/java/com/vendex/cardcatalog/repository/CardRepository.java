package com.vendex.cardcatalog.repository;

import com.vendex.cardcatalog.domain.Card;
import com.vendex.cardcatalog.domain.CardSeed;
import com.vendex.cardcatalog.domain.SetSummary;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Hand-rolled repository (not a {@code CrudRepository}) because the read
 * paths use Postgres-specific features Spring Data JDBC's derived-query
 * DSL doesn't model cleanly:
 *
 * <ul>
 *   <li>UPSERT with {@code COALESCE} on nullable columns, so a thin re-seed
 *       (set stub without rarity, say) doesn't clobber richer existing values.</li>
 *   <li>{@code pg_trgm} {@code similarity()} for fuzzy SearchCards ordering.</li>
 *   <li>{@code GROUP BY set_id} aggregation for ListSets.</li>
 * </ul>
 */
@Repository
public class CardRepository {

    private static final RowMapper<Card> ROW_MAPPER = (rs, rowNum) -> new Card(
            (UUID) rs.getObject("id"),
            rs.getString("external_id"),
            rs.getString("name"),
            rs.getString("set_id"),
            rs.getString("set_name"),
            rs.getString("set_series"),
            rs.getString("rarity"),
            rs.getString("image_url"),
            rs.getString("image_url_large"),
            getLocalDate(rs, "release_date"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    private static java.time.LocalDate getLocalDate(ResultSet rs, String col) throws SQLException {
        Date d = rs.getDate(col);
        return d == null ? null : d.toLocalDate();
    }

    private final NamedParameterJdbcTemplate jdbc;

    public CardRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Card> findByExternalId(String externalId) {
        try {
            Card card = jdbc.queryForObject(
                    "SELECT * FROM cards WHERE external_id = :external_id",
                    new MapSqlParameterSource("external_id", externalId),
                    ROW_MAPPER
            );
            return Optional.ofNullable(card);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<Card> findByExternalIds(Collection<String> externalIds) {
        if (externalIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(
                "SELECT * FROM cards WHERE external_id IN (:ids)",
                new MapSqlParameterSource("ids", externalIds),
                ROW_MAPPER
        );
    }

    /**
     * Fuzzy search ordered by trigram similarity to {@code query}. When
     * {@code query} is blank, results are ordered alphabetically by name.
     *
     * <p>Returns one row beyond {@code limit} so callers can detect "more
     * results available" without a separate COUNT.
     */
    public List<Card> search(String query, String setIdFilter, String rarityFilter, int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT * FROM cards WHERE 1=1");
        MapSqlParameterSource params = new MapSqlParameterSource();

        boolean fuzzy = query != null && !query.isBlank();
        if (fuzzy) {
            sql.append(" AND name % :query");
            params.addValue("query", query);
        }
        if (setIdFilter != null && !setIdFilter.isBlank()) {
            sql.append(" AND set_id = :set_id");
            params.addValue("set_id", setIdFilter);
        }
        if (rarityFilter != null && !rarityFilter.isBlank()) {
            sql.append(" AND rarity = :rarity");
            params.addValue("rarity", rarityFilter);
        }
        if (fuzzy) {
            sql.append(" ORDER BY similarity(name, :query) DESC, external_id ASC");
        } else {
            sql.append(" ORDER BY name ASC, external_id ASC");
        }
        sql.append(" LIMIT :limit OFFSET :offset");
        params.addValue("limit", limit);
        params.addValue("offset", offset);

        return jdbc.query(sql.toString(), params, ROW_MAPPER);
    }

    public List<SetSummary> listSets() {
        return jdbc.query(
                """
                SELECT set_id,
                       MAX(set_name)        AS set_name,
                       MAX(set_series)      AS set_series,
                       COUNT(*)::int        AS card_count
                FROM cards
                GROUP BY set_id
                ORDER BY MAX(set_name)
                """,
                (rs, rowNum) -> new SetSummary(
                        rs.getString("set_id"),
                        rs.getString("set_name"),
                        rs.getString("set_series"),
                        rs.getInt("card_count")
                )
        );
    }

    /**
     * UPSERT a single card by {@code external_id}. On conflict, mutable
     * fields are updated, but nullable fields use {@code COALESCE(EXCLUDED,
     * existing)} so a thinner re-seed (e.g. set stub without rarity) doesn't
     * wipe out a previously-enriched value.
     *
     * <p>{@code updated_at} is bumped only when the upsert actually changed
     * something — we still set it unconditionally here because PG's UPSERT
     * fires the update branch even when values are identical; this is fine
     * for our seed cadence (~hourly at most).
     */
    public Card upsert(CardSeed seed) {
        return jdbc.queryForObject(
                """
                INSERT INTO cards (external_id, name, set_id, set_name, set_series,
                                   rarity, image_url, image_url_large, release_date)
                VALUES (:external_id, :name, :set_id, :set_name, :set_series,
                        :rarity, :image_url, :image_url_large, :release_date)
                ON CONFLICT (external_id) DO UPDATE SET
                    name            = EXCLUDED.name,
                    set_id          = EXCLUDED.set_id,
                    set_name        = EXCLUDED.set_name,
                    set_series      = COALESCE(EXCLUDED.set_series,      cards.set_series),
                    rarity          = COALESCE(EXCLUDED.rarity,          cards.rarity),
                    image_url       = COALESCE(EXCLUDED.image_url,       cards.image_url),
                    image_url_large = COALESCE(EXCLUDED.image_url_large, cards.image_url_large),
                    release_date    = COALESCE(EXCLUDED.release_date,    cards.release_date),
                    updated_at      = NOW()
                RETURNING *
                """,
                buildParams(seed),
                ROW_MAPPER
        );
    }

    /**
     * Batch upsert. Returns the resulting Card rows in the same order they
     * were upserted, so callers (mainly tests + dry-run counts) can correlate.
     */
    public List<Card> upsertAll(Collection<CardSeed> seeds) {
        List<Card> out = new ArrayList<>(seeds.size());
        for (CardSeed seed : seeds) {
            out.add(upsert(seed));
        }
        return out;
    }

    private static MapSqlParameterSource buildParams(CardSeed seed) {
        return new MapSqlParameterSource()
                .addValue("external_id", seed.externalId())
                .addValue("name", seed.name())
                .addValue("set_id", seed.setId())
                .addValue("set_name", seed.setName())
                .addValue("set_series", seed.setSeries())
                .addValue("rarity", seed.rarity())
                .addValue("image_url", seed.imageUrl())
                .addValue("image_url_large", seed.imageUrlLarge())
                .addValue("release_date", seed.releaseDate() == null ? null : Date.valueOf(seed.releaseDate()));
    }
}
