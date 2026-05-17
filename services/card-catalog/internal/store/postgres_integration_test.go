//go:build integration

// Integration tests for the card-catalog Postgres store. Run with:
//
//	make test-integration
//
// or directly:
//
//	CARD_CATALOG_TEST_DATABASE_URL=postgres://... go test -tags integration ./services/card-catalog/internal/store/
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
	v := os.Getenv("CARD_CATALOG_TEST_DATABASE_URL")
	if v == "" {
		t.Skip("CARD_CATALOG_TEST_DATABASE_URL not set; skipping integration test")
	}
	return v
}

func setupDB(t *testing.T) *pgxpool.Pool {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

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

func mkCard(externalID, name, setID, setName string) Card {
	now := time.Now().UTC()
	return Card{
		ID:         uuid.New(),
		ExternalID: externalID,
		Name:       name,
		SetID:      setID,
		SetName:    setName,
		Rarity:     "Common",
		CreatedAt:  now,
		UpdatedAt:  now,
	}
}

func TestPostgres_UpsertAndGet(t *testing.T) {
	pool := setupDB(t)
	s := NewPostgres(pool)
	ctx := context.Background()

	c := mkCard("sv03-001", "Oddish", "sv03", "Obsidian Flames")
	if err := s.UpsertCard(ctx, c); err != nil {
		t.Fatal(err)
	}

	got, err := s.GetCardByID(ctx, c.ID)
	if err != nil {
		t.Fatal(err)
	}
	if got.Name != "Oddish" || got.SetID != "sv03" {
		t.Errorf("got %+v", got)
	}

	byExt, err := s.GetCardByExternalID(ctx, "sv03-001")
	if err != nil {
		t.Fatal(err)
	}
	if byExt.ID != c.ID {
		t.Errorf("id: got %v want %v", byExt.ID, c.ID)
	}

	if _, err := s.GetCardByID(ctx, uuid.New()); !errors.Is(err, ErrNotFound) {
		t.Errorf("missing: got %v want ErrNotFound", err)
	}
}

func TestPostgres_UpsertPreservesIDAndNullables(t *testing.T) {
	// Re-upserting an existing external_id should not duplicate. Also,
	// nullable fields (rarity, set_series) should not be wiped on re-upsert
	// when the new value is empty — COALESCE pattern.
	pool := setupDB(t)
	s := NewPostgres(pool)
	ctx := context.Background()

	original := mkCard("sv03-002", "Gloom", "sv03", "Obsidian Flames")
	original.SetSeries = "Scarlet & Violet"
	original.Rarity = "Uncommon"
	if err := s.UpsertCard(ctx, original); err != nil {
		t.Fatal(err)
	}

	// Second upsert from a thinner source (e.g., set-stub seed without rarity).
	updated := mkCard("sv03-002", "Gloom (updated)", "sv03", "Obsidian Flames")
	updated.SetSeries = ""
	updated.Rarity = ""
	if err := s.UpsertCard(ctx, updated); err != nil {
		t.Fatal(err)
	}

	got, err := s.GetCardByExternalID(ctx, "sv03-002")
	if err != nil {
		t.Fatal(err)
	}
	if got.ID != original.ID {
		t.Errorf("id changed: got %v want %v", got.ID, original.ID)
	}
	if got.Name != "Gloom (updated)" {
		t.Errorf("name: got %q want updated", got.Name)
	}
	if got.Rarity != "Uncommon" {
		t.Errorf("rarity wiped on thin re-upsert: got %q want Uncommon", got.Rarity)
	}
	if got.SetSeries != "Scarlet & Violet" {
		t.Errorf("series wiped on thin re-upsert: got %q", got.SetSeries)
	}
}

func TestPostgres_GetCardsByIDs(t *testing.T) {
	pool := setupDB(t)
	s := NewPostgres(pool)
	ctx := context.Background()

	a := mkCard("a-1", "A", "sv01", "First")
	b := mkCard("b-1", "B", "sv02", "Second")
	c := mkCard("c-1", "C", "sv03", "Third")
	for _, card := range []Card{a, b, c} {
		if err := s.UpsertCard(ctx, card); err != nil {
			t.Fatal(err)
		}
	}

	got, err := s.GetCardsByIDs(ctx, []uuid.UUID{a.ID, c.ID, uuid.New()})
	if err != nil {
		t.Fatal(err)
	}
	if len(got) != 2 {
		t.Errorf("got %d, want 2", len(got))
	}
}

func TestPostgres_SearchCards(t *testing.T) {
	pool := setupDB(t)
	s := NewPostgres(pool)
	ctx := context.Background()

	cards := []Card{
		mkCard("sv03-001", "Pikachu", "sv03", "Obsidian Flames"),
		mkCard("sv03-002", "Pikachu VMAX", "sv03", "Obsidian Flames"),
		mkCard("sv03-003", "Bulbasaur", "sv03", "Obsidian Flames"),
		mkCard("sv01-001", "Pikachu Promo", "sv01", "Scarlet & Violet"),
	}
	for _, c := range cards {
		if err := s.UpsertCard(ctx, c); err != nil {
			t.Fatal(err)
		}
	}

	t.Run("by_query", func(t *testing.T) {
		got, err := s.SearchCards(ctx, SearchParams{Query: "Pikachu", Limit: 10})
		if err != nil {
			t.Fatal(err)
		}
		if len(got) != 3 {
			t.Errorf("got %d, want 3 Pikachu cards", len(got))
		}
	})
	t.Run("by_set_filter", func(t *testing.T) {
		got, err := s.SearchCards(ctx, SearchParams{Query: "Pikachu", SetIDFilter: "sv03", Limit: 10})
		if err != nil {
			t.Fatal(err)
		}
		if len(got) != 2 {
			t.Errorf("got %d, want 2 Pikachu cards in sv03", len(got))
		}
	})
	t.Run("pagination", func(t *testing.T) {
		first, err := s.SearchCards(ctx, SearchParams{Limit: 2, Offset: 0})
		if err != nil {
			t.Fatal(err)
		}
		if len(first) != 2 {
			t.Errorf("first: got %d, want 2", len(first))
		}
		second, err := s.SearchCards(ctx, SearchParams{Limit: 2, Offset: 2})
		if err != nil {
			t.Fatal(err)
		}
		if len(second) != 2 {
			t.Errorf("second: got %d, want 2", len(second))
		}
		if first[0].ID == second[0].ID {
			t.Error("expected different rows across pages")
		}
	})
}

func TestPostgres_ListSets(t *testing.T) {
	pool := setupDB(t)
	s := NewPostgres(pool)
	ctx := context.Background()

	cards := []Card{
		mkCard("sv01-1", "x", "sv01", "Scarlet & Violet"),
		mkCard("sv01-2", "y", "sv01", "Scarlet & Violet"),
		mkCard("sv03-1", "z", "sv03", "Obsidian Flames"),
	}
	for _, c := range cards {
		if err := s.UpsertCard(ctx, c); err != nil {
			t.Fatal(err)
		}
	}

	sets, err := s.ListSets(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if len(sets) != 2 {
		t.Fatalf("got %d sets, want 2: %+v", len(sets), sets)
	}
	bySetID := map[string]SetSummary{}
	for _, s := range sets {
		bySetID[s.ID] = s
	}
	if bySetID["sv01"].CardCount != 2 || bySetID["sv03"].CardCount != 1 {
		t.Errorf("counts wrong: %+v", bySetID)
	}
}
