package com.vendex.auth.repository;

import com.vendex.auth.domain.SigningKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SigningKeyRepository {

    private static final RowMapper<SigningKey> ROW_MAPPER = (rs, rowNum) -> new SigningKey(
            (UUID) rs.getObject("id"),
            rs.getString("public_key"),
            rs.getBytes("private_key_encrypted"),
            rs.getString("alg"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("rotated_at") == null ? null : rs.getTimestamp("rotated_at").toInstant(),
            rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant()
    );

    private final NamedParameterJdbcTemplate jdbc;

    @Autowired
    public SigningKeyRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public SigningKey insert(UUID id, String publicKeyPem, byte[] privateKeyEncrypted, String alg) {
        return jdbc.queryForObject(
                """
                INSERT INTO signing_keys (id, public_key, private_key_encrypted, alg)
                VALUES (:id, :public_key, :private_key_encrypted, :alg)
                RETURNING id, public_key, private_key_encrypted, alg, created_at, rotated_at, revoked_at
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("public_key", publicKeyPem)
                        .addValue("private_key_encrypted", privateKeyEncrypted)
                        .addValue("alg", alg),
                ROW_MAPPER
        );
    }

    public Optional<SigningKey> findActive() {
        try {
            SigningKey k = jdbc.queryForObject(
                    """
                    SELECT id, public_key, private_key_encrypted, alg, created_at, rotated_at, revoked_at
                    FROM signing_keys
                    WHERE rotated_at IS NULL AND revoked_at IS NULL
                    """,
                    new MapSqlParameterSource(),
                    ROW_MAPPER
            );
            return Optional.ofNullable(k);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<SigningKey> findById(UUID id) {
        try {
            SigningKey k = jdbc.queryForObject(
                    """
                    SELECT id, public_key, private_key_encrypted, alg, created_at, rotated_at, revoked_at
                    FROM signing_keys
                    WHERE id = :id
                    """,
                    new MapSqlParameterSource("id", id),
                    ROW_MAPPER
            );
            return Optional.ofNullable(k);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /** All keys not yet revoked — i.e. the JWKS publication set. */
    public List<SigningKey> findAllPublished() {
        return jdbc.query(
                """
                SELECT id, public_key, private_key_encrypted, alg, created_at, rotated_at, revoked_at
                FROM signing_keys
                WHERE revoked_at IS NULL
                ORDER BY created_at DESC
                """,
                new MapSqlParameterSource(),
                ROW_MAPPER
        );
    }

    /** Marks a key as no longer the active signer. Used during rotation. */
    public void markRotated(UUID id, java.time.Instant when) {
        jdbc.update(
                "UPDATE signing_keys SET rotated_at = :when WHERE id = :id AND rotated_at IS NULL",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("when", Timestamp.from(when))
        );
    }
}
