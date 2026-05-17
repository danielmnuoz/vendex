//go:build integration

// Integration tests for the Postgres store. Run with:
//
//	make test-integration
//
// or directly:
//
//	AUTH_TEST_DATABASE_URL=postgres://... go test -tags integration ./services/auth/internal/store/
//
// The harness expects an empty database it can run migrations against.
// docker-compose.test.yml provisions a throwaway Postgres for CI.
package store

import (
	"context"
	"database/sql"
	"errors"
	"os"
	"path/filepath"
	"runtime"
	"testing"
	"time"

	"github.com/golang-migrate/migrate/v4"
	migratepg "github.com/golang-migrate/migrate/v4/database/postgres"
	_ "github.com/golang-migrate/migrate/v4/source/file"
	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"
	_ "github.com/jackc/pgx/v5/stdlib"
)

func dsn(t *testing.T) string {
	t.Helper()
	v := os.Getenv("AUTH_TEST_DATABASE_URL")
	if v == "" {
		t.Skip("AUTH_TEST_DATABASE_URL not set; skipping integration test")
	}
	return v
}

func setupDB(t *testing.T) *pgxpool.Pool {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	// Use database/sql driver to run migrations down then up for a clean state.
	sqlDB, err := sql.Open("pgx", dsn(t))
	if err != nil {
		t.Fatalf("sql open: %v", err)
	}
	defer sqlDB.Close()

	driver, err := migratepg.WithInstance(sqlDB, &migratepg.Config{})
	if err != nil {
		t.Fatalf("migrate driver: %v", err)
	}

	_, file, _, _ := runtime.Caller(0)
	migrationsDir := filepath.Join(filepath.Dir(file), "..", "..", "migrations")
	m, err := migrate.NewWithDatabaseInstance("file://"+migrationsDir, "postgres", driver)
	if err != nil {
		t.Fatalf("migrate new: %v", err)
	}

	if err := m.Down(); err != nil && !errors.Is(err, migrate.ErrNoChange) {
		t.Logf("migrate down: %v", err)
	}
	if err := m.Up(); err != nil && !errors.Is(err, migrate.ErrNoChange) {
		t.Fatalf("migrate up: %v", err)
	}

	pool, err := pgxpool.New(ctx, dsn(t))
	if err != nil {
		t.Fatalf("pgxpool: %v", err)
	}
	t.Cleanup(func() { pool.Close() })
	return pool
}

func TestPostgres_UserCRUD(t *testing.T) {
	pool := setupDB(t)
	s := NewPostgres(pool)
	ctx := context.Background()

	u := User{
		ID:           uuid.New(),
		Email:        "vendor@x.com",
		PasswordHash: "hash",
		Role:         "vendor",
		ShopName:     "Shop",
		City:         "Dallas",
		State:        "TX",
		CreatedAt:    time.Now().UTC(),
		UpdatedAt:    time.Now().UTC(),
	}
	if err := s.CreateUser(ctx, u); err != nil {
		t.Fatalf("create: %v", err)
	}

	got, err := s.GetUserByEmail(ctx, u.Email)
	if err != nil {
		t.Fatalf("get by email: %v", err)
	}
	if got.ID != u.ID || got.ShopName != "Shop" {
		t.Errorf("got %+v", got)
	}

	gotByID, err := s.GetUserByID(ctx, u.ID)
	if err != nil {
		t.Fatalf("get by id: %v", err)
	}
	if gotByID.Email != u.Email {
		t.Errorf("email: got %s want %s", gotByID.Email, u.Email)
	}

	updated, err := s.UpdateUserProfile(ctx, u.ID, "New Shop", "Austin", "TX")
	if err != nil {
		t.Fatalf("update: %v", err)
	}
	if updated.ShopName != "New Shop" || updated.City != "Austin" {
		t.Errorf("update: %+v", updated)
	}

	// Duplicate email → ErrEmailTaken.
	dup := u
	dup.ID = uuid.New()
	if err := s.CreateUser(ctx, dup); !errors.Is(err, ErrEmailTaken) {
		t.Errorf("dup email: got %v want ErrEmailTaken", err)
	}

	if _, err := s.GetUserByEmail(ctx, "missing@x.com"); !errors.Is(err, ErrNotFound) {
		t.Errorf("missing: got %v want ErrNotFound", err)
	}
}

func TestPostgres_RefreshTokenLifecycle(t *testing.T) {
	pool := setupDB(t)
	s := NewPostgres(pool)
	ctx := context.Background()

	u := User{ID: uuid.New(), Email: "rt@x.com", PasswordHash: "h", Role: "vendor", CreatedAt: time.Now(), UpdatedAt: time.Now()}
	if err := s.CreateUser(ctx, u); err != nil {
		t.Fatal(err)
	}

	rt := RefreshToken{
		ID:        uuid.New(),
		UserID:    u.ID,
		TokenHash: "hash1",
		ExpiresAt: time.Now().Add(time.Hour),
		CreatedAt: time.Now(),
	}
	if err := s.InsertRefreshToken(ctx, rt); err != nil {
		t.Fatalf("insert: %v", err)
	}

	got, err := s.GetRefreshTokenByHash(ctx, "hash1")
	if err != nil {
		t.Fatal(err)
	}
	if got.Revoked {
		t.Error("expected not revoked")
	}

	if err := s.RevokeRefreshToken(ctx, rt.ID); err != nil {
		t.Fatal(err)
	}
	got, _ = s.GetRefreshTokenByHash(ctx, "hash1")
	if !got.Revoked {
		t.Error("expected revoked")
	}
}

func TestPostgres_ConsumeRefreshToken_AtomicCAS(t *testing.T) {
	// The CAS UPDATE ... WHERE revoked = FALSE must serialize at the row
	// level — concurrent consume calls for the same token must produce
	// exactly one winner.
	pool := setupDB(t)
	s := NewPostgres(pool)
	ctx := context.Background()

	u := User{ID: uuid.New(), Email: "race@x.com", PasswordHash: "h", Role: "vendor", CreatedAt: time.Now(), UpdatedAt: time.Now()}
	if err := s.CreateUser(ctx, u); err != nil {
		t.Fatal(err)
	}
	rt := RefreshToken{
		ID:        uuid.New(),
		UserID:    u.ID,
		TokenHash: "race-hash",
		ExpiresAt: time.Now().Add(time.Hour),
		CreatedAt: time.Now(),
	}
	if err := s.InsertRefreshToken(ctx, rt); err != nil {
		t.Fatal(err)
	}

	const n = 16
	results := make(chan error, n)
	for i := 0; i < n; i++ {
		go func() {
			_, err := s.ConsumeRefreshToken(ctx, "race-hash")
			results <- err
		}()
	}
	successes, revoked, other := 0, 0, 0
	for i := 0; i < n; i++ {
		err := <-results
		switch {
		case err == nil:
			successes++
		case errors.Is(err, ErrTokenRevoked):
			revoked++
		default:
			other++
			t.Logf("unexpected: %v", err)
		}
	}
	if successes != 1 {
		t.Errorf("concurrent consume: got %d successes, want 1", successes)
	}
	if revoked != n-1 {
		t.Errorf("concurrent consume: got %d ErrTokenRevoked, want %d", revoked, n-1)
	}
	if other != 0 {
		t.Errorf("got %d unexpected errors", other)
	}
}

func TestPostgres_SigningKey_OneActive(t *testing.T) {
	pool := setupDB(t)
	s := NewPostgres(pool)
	ctx := context.Background()

	k1 := SigningKey{ID: uuid.New(), PublicKeyPEM: "pub1", PrivateKeyEncrypted: "priv1", Alg: "RS256", CreatedAt: time.Now()}
	if err := s.InsertSigningKey(ctx, k1); err != nil {
		t.Fatal(err)
	}

	// Inserting a second active key should fail due to the unique partial index.
	k2 := SigningKey{ID: uuid.New(), PublicKeyPEM: "pub2", PrivateKeyEncrypted: "priv2", Alg: "RS256", CreatedAt: time.Now()}
	if err := s.InsertSigningKey(ctx, k2); err == nil {
		t.Error("expected unique-violation when inserting a second active signing key")
	}

	// Rotate k1, then k2 should succeed.
	now := time.Now().UTC()
	if err := s.MarkSigningKeyRotated(ctx, k1.ID, now); err != nil {
		t.Fatal(err)
	}
	if err := s.InsertSigningKey(ctx, k2); err != nil {
		t.Fatalf("insert after rotation: %v", err)
	}

	active, err := s.GetActiveSigningKey(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if active.ID != k2.ID {
		t.Errorf("active: got %v want %v", active.ID, k2.ID)
	}

	jwks, err := s.ListJWKSKeys(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if len(jwks) != 2 {
		t.Errorf("jwks: got %d keys, want 2 (active + rotated)", len(jwks))
	}

	// Revoke k1 → JWKS drops to 1.
	if err := s.MarkSigningKeyRevoked(ctx, k1.ID, now); err != nil {
		t.Fatal(err)
	}
	jwks, _ = s.ListJWKSKeys(ctx)
	if len(jwks) != 1 {
		t.Errorf("jwks after revoke: got %d, want 1", len(jwks))
	}
}
