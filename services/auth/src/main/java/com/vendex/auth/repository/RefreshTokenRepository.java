package com.vendex.auth.repository;

import com.vendex.auth.domain.RefreshToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

/**
 * The refresh-token store is intentionally NOT a Spring Data {@code
 * CrudRepository} — the security-critical primitive ({@link #consume}) is a
 * compare-and-swap UPDATE that returns the row in a single statement, and
 * Spring Data JDBC's derived-query DSL doesn't model that cleanly.
 */
@Repository
public class RefreshTokenRepository {

    private static final RowMapper<RefreshToken> ROW_MAPPER = (rs, rowNum) -> new RefreshToken(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("user_id"),
            rs.getString("token_hash"),
            rs.getTimestamp("expires_at").toInstant(),
            rs.getBoolean("revoked"),
            rs.getTimestamp("created_at").toInstant()
    );

    private final NamedParameterJdbcTemplate jdbc;

    @Autowired
    public RefreshTokenRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public RefreshToken insert(UUID userId, String tokenHash, java.time.Instant expiresAt) {
        return jdbc.queryForObject(
                """
                INSERT INTO refresh_tokens (user_id, token_hash, expires_at)
                VALUES (:user_id, :token_hash, :expires_at)
                RETURNING id, user_id, token_hash, expires_at, revoked, created_at
                """,
                new MapSqlParameterSource()
                        .addValue("user_id", userId)
                        .addValue("token_hash", tokenHash)
                        .addValue("expires_at", Timestamp.from(expiresAt)),
                ROW_MAPPER
        );
    }

    /**
     * Atomically marks a refresh token as revoked and returns it. Returns
     * empty if the token doesn't exist or was already revoked.
     *
     * <p>This is the load-bearing operation for refresh-token rotation
     * safety: two concurrent refreshes with the same token race on the
     * UPDATE, and at most one observes the {@code revoked = FALSE} row to
     * flip. The loser sees zero rows and the caller treats it as an invalid
     * token.
     */
    public Optional<RefreshToken> consume(String tokenHash) {
        try {
            RefreshToken consumed = jdbc.queryForObject(
                    """
                    UPDATE refresh_tokens
                    SET revoked = TRUE
                    WHERE token_hash = :token_hash AND revoked = FALSE
                    RETURNING id, user_id, token_hash, expires_at, revoked, created_at
                    """,
                    new MapSqlParameterSource("token_hash", tokenHash),
                    ROW_MAPPER
            );
            return Optional.ofNullable(consumed);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
