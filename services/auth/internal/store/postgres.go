package store

import (
	"context"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
)

type Postgres struct {
	pool *pgxpool.Pool
}

func NewPostgres(pool *pgxpool.Pool) *Postgres {
	return &Postgres{pool: pool}
}

func (p *Postgres) CreateUser(ctx context.Context, u User) error {
	_, err := p.pool.Exec(ctx, `
		INSERT INTO users (id, email, password_hash, role, shop_name, city, state, created_at, updated_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
	`, u.ID, u.Email, u.PasswordHash, u.Role, nullable(u.ShopName), nullable(u.City), nullable(u.State), u.CreatedAt, u.UpdatedAt)
	if err != nil {
		var pgErr *pgconn.PgError
		if errors.As(err, &pgErr) && pgErr.Code == "23505" {
			return ErrEmailTaken
		}
		return fmt.Errorf("insert user: %w", err)
	}
	return nil
}

func (p *Postgres) GetUserByEmail(ctx context.Context, email string) (User, error) {
	return p.queryUser(ctx, "WHERE email = $1", email)
}

func (p *Postgres) GetUserByID(ctx context.Context, id uuid.UUID) (User, error) {
	return p.queryUser(ctx, "WHERE id = $1", id)
}

func (p *Postgres) queryUser(ctx context.Context, where string, arg any) (User, error) {
	var u User
	var shopName, city, state *string
	q := "SELECT id, email, password_hash, role, shop_name, city, state, created_at, updated_at FROM users " + where
	err := p.pool.QueryRow(ctx, q, arg).Scan(
		&u.ID, &u.Email, &u.PasswordHash, &u.Role,
		&shopName, &city, &state,
		&u.CreatedAt, &u.UpdatedAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return User{}, ErrNotFound
	}
	if err != nil {
		return User{}, fmt.Errorf("query user: %w", err)
	}
	u.ShopName = deref(shopName)
	u.City = deref(city)
	u.State = deref(state)
	return u, nil
}

func (p *Postgres) UpdateUserProfile(ctx context.Context, id uuid.UUID, shopName, city, state string) (User, error) {
	now := time.Now().UTC()
	_, err := p.pool.Exec(ctx, `
		UPDATE users SET shop_name = $2, city = $3, state = $4, updated_at = $5 WHERE id = $1
	`, id, nullable(shopName), nullable(city), nullable(state), now)
	if err != nil {
		return User{}, fmt.Errorf("update user: %w", err)
	}
	return p.GetUserByID(ctx, id)
}

func (p *Postgres) InsertRefreshToken(ctx context.Context, t RefreshToken) error {
	_, err := p.pool.Exec(ctx, `
		INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at, revoked, created_at)
		VALUES ($1, $2, $3, $4, $5, $6)
	`, t.ID, t.UserID, t.TokenHash, t.ExpiresAt, t.Revoked, t.CreatedAt)
	if err != nil {
		return fmt.Errorf("insert refresh token: %w", err)
	}
	return nil
}

func (p *Postgres) GetRefreshTokenByHash(ctx context.Context, hash string) (RefreshToken, error) {
	var t RefreshToken
	err := p.pool.QueryRow(ctx, `
		SELECT id, user_id, token_hash, expires_at, revoked, created_at
		FROM refresh_tokens WHERE token_hash = $1
	`, hash).Scan(&t.ID, &t.UserID, &t.TokenHash, &t.ExpiresAt, &t.Revoked, &t.CreatedAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return RefreshToken{}, ErrNotFound
	}
	if err != nil {
		return RefreshToken{}, fmt.Errorf("query refresh token: %w", err)
	}
	return t, nil
}

func (p *Postgres) RevokeRefreshToken(ctx context.Context, id uuid.UUID) error {
	_, err := p.pool.Exec(ctx, `UPDATE refresh_tokens SET revoked = TRUE WHERE id = $1`, id)
	if err != nil {
		return fmt.Errorf("revoke refresh token: %w", err)
	}
	return nil
}

func (p *Postgres) GetActiveSigningKey(ctx context.Context) (SigningKey, error) {
	var k SigningKey
	err := p.pool.QueryRow(ctx, `
		SELECT id, public_key, private_key_encrypted, alg, created_at, rotated_at, revoked_at
		FROM signing_keys
		WHERE rotated_at IS NULL AND revoked_at IS NULL
		LIMIT 1
	`).Scan(&k.ID, &k.PublicKeyPEM, &k.PrivateKeyEncrypted, &k.Alg, &k.CreatedAt, &k.RotatedAt, &k.RevokedAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return SigningKey{}, ErrNotFound
	}
	if err != nil {
		return SigningKey{}, fmt.Errorf("query active signing key: %w", err)
	}
	return k, nil
}

func (p *Postgres) ListJWKSKeys(ctx context.Context) ([]SigningKey, error) {
	rows, err := p.pool.Query(ctx, `
		SELECT id, public_key, private_key_encrypted, alg, created_at, rotated_at, revoked_at
		FROM signing_keys
		WHERE revoked_at IS NULL
		ORDER BY created_at DESC
	`)
	if err != nil {
		return nil, fmt.Errorf("query jwks: %w", err)
	}
	defer rows.Close()
	var out []SigningKey
	for rows.Next() {
		var k SigningKey
		if err := rows.Scan(&k.ID, &k.PublicKeyPEM, &k.PrivateKeyEncrypted, &k.Alg, &k.CreatedAt, &k.RotatedAt, &k.RevokedAt); err != nil {
			return nil, err
		}
		out = append(out, k)
	}
	return out, rows.Err()
}

func (p *Postgres) GetSigningKeyByID(ctx context.Context, id uuid.UUID) (SigningKey, error) {
	var k SigningKey
	err := p.pool.QueryRow(ctx, `
		SELECT id, public_key, private_key_encrypted, alg, created_at, rotated_at, revoked_at
		FROM signing_keys WHERE id = $1
	`, id).Scan(&k.ID, &k.PublicKeyPEM, &k.PrivateKeyEncrypted, &k.Alg, &k.CreatedAt, &k.RotatedAt, &k.RevokedAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return SigningKey{}, ErrNotFound
	}
	if err != nil {
		return SigningKey{}, fmt.Errorf("query signing key: %w", err)
	}
	return k, nil
}

func (p *Postgres) InsertSigningKey(ctx context.Context, k SigningKey) error {
	_, err := p.pool.Exec(ctx, `
		INSERT INTO signing_keys (id, public_key, private_key_encrypted, alg, created_at, rotated_at, revoked_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7)
	`, k.ID, k.PublicKeyPEM, k.PrivateKeyEncrypted, k.Alg, k.CreatedAt, k.RotatedAt, k.RevokedAt)
	if err != nil {
		return fmt.Errorf("insert signing key: %w", err)
	}
	return nil
}

func (p *Postgres) MarkSigningKeyRotated(ctx context.Context, id uuid.UUID, at time.Time) error {
	_, err := p.pool.Exec(ctx, `UPDATE signing_keys SET rotated_at = $2 WHERE id = $1`, id, at)
	if err != nil {
		return fmt.Errorf("mark rotated: %w", err)
	}
	return nil
}

func (p *Postgres) MarkSigningKeyRevoked(ctx context.Context, id uuid.UUID, at time.Time) error {
	_, err := p.pool.Exec(ctx, `UPDATE signing_keys SET revoked_at = $2 WHERE id = $1`, id, at)
	if err != nil {
		return fmt.Errorf("mark revoked: %w", err)
	}
	return nil
}

func nullable(s string) *string {
	if s == "" {
		return nil
	}
	return &s
}

func deref(s *string) string {
	if s == nil {
		return ""
	}
	return *s
}
