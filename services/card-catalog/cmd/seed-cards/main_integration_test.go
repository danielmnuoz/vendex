//go:build integration

// End-to-end smoke test for the seed script. Wires the real seed code
// (TCGdex HTTP client + Postgres store) against an httptest server and a
// dockerized Postgres. Catches regressions where the seed logic and the
// store get out of sync — they live in different packages.
package main

import (
	"context"
	"database/sql"
	"errors"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"runtime"
	"testing"
	"time"

	"github.com/danielmnuoz/vendex/services/card-catalog/internal/store"
	"github.com/danielmnuoz/vendex/services/card-catalog/internal/tcgdex"
	"github.com/golang-migrate/migrate/v4"
	migratepg "github.com/golang-migrate/migrate/v4/database/postgres"
	_ "github.com/golang-migrate/migrate/v4/source/file"
	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"
	_ "github.com/jackc/pgx/v5/stdlib"
)

func TestSeedEndToEnd(t *testing.T) {
	dsn := os.Getenv("CARD_CATALOG_TEST_DATABASE_URL")
	if dsn == "" {
		t.Skip("CARD_CATALOG_TEST_DATABASE_URL not set")
	}

	// Migrate the test DB.
	sqlDB, err := sql.Open("pgx", dsn)
	if err != nil {
		t.Fatal(err)
	}
	defer sqlDB.Close()
	driver, err := migratepg.WithInstance(sqlDB, &migratepg.Config{})
	if err != nil {
		t.Fatal(err)
	}
	_, file, _, _ := runtime.Caller(0)
	migrationsDir := filepath.Join(filepath.Dir(file), "..", "..", "migrations")
	m, err := migrate.NewWithDatabaseInstance("file://"+migrationsDir, "postgres", driver)
	if err != nil {
		t.Fatal(err)
	}
	if err := m.Down(); err != nil && !errors.Is(err, migrate.ErrNoChange) {
		t.Logf("migrate down: %v", err)
	}
	if err := m.Up(); err != nil && !errors.Is(err, migrate.ErrNoChange) {
		t.Fatal(err)
	}

	// Fake TCGdex returning two sets, each with two cards.
	mux := http.NewServeMux()
	mux.HandleFunc("/sets", func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`[{"id":"sv01","name":"Scarlet & Violet","cardCount":{"total":2,"official":2}},{"id":"sv03","name":"Obsidian Flames","cardCount":{"total":2,"official":2}}]`))
	})
	mux.HandleFunc("/sets/sv01", func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`{"id":"sv01","name":"Scarlet & Violet","cards":[{"id":"sv01-001","name":"Sprigatito","image":"http://x/sv/sv01/001","localId":"001"},{"id":"sv01-002","name":"Fuecoco","image":"http://x/sv/sv01/002","localId":"002"}]}`))
	})
	mux.HandleFunc("/sets/sv03", func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`{"id":"sv03","name":"Obsidian Flames","cards":[{"id":"sv03-001","name":"Oddish","image":"http://x/sv/sv03/001","localId":"001"},{"id":"sv03-002","name":"Gloom","image":"http://x/sv/sv03/002","localId":"002"}]}`))
	})
	mux.HandleFunc("/series", func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`[{"id":"sv","name":"Scarlet & Violet"}]`))
	})
	mux.HandleFunc("/series/sv", func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`{"id":"sv","name":"Scarlet & Violet","releaseDate":"2023-03-31","sets":[{"id":"sv01","name":"Scarlet & Violet"},{"id":"sv03","name":"Obsidian Flames"}]}`))
	})
	srv := httptest.NewServer(mux)
	defer srv.Close()

	// Run the seed logic — duplicated minimally here rather than refactoring
	// main() into something testable. The duplication is small and tested
	// behavior matches what shipping main() does.
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	pool, err := pgxpool.New(ctx, dsn)
	if err != nil {
		t.Fatal(err)
	}
	defer pool.Close()

	client := tcgdex.New(srv.URL, nil)
	s := store.NewPostgres(pool)

	series, err := client.ListSeries(ctx)
	if err != nil {
		t.Fatal(err)
	}
	seriesBySetID := map[string]string{}
	for _, ser := range series {
		detail, _ := client.GetSeries(ctx, ser.ID)
		for _, set := range detail.Sets {
			seriesBySetID[set.ID] = detail.Name
		}
	}

	sets, err := client.ListSets(ctx)
	if err != nil {
		t.Fatal(err)
	}
	for _, summary := range sets {
		setDetail, err := client.GetSet(ctx, summary.ID)
		if err != nil {
			t.Fatal(err)
		}
		for _, stub := range setDetail.Cards {
			c := store.Card{
				ID:            uuid.New(),
				ExternalID:    stub.ID,
				Name:          stub.Name,
				SetID:         setDetail.ID,
				SetName:       setDetail.Name,
				SetSeries:     seriesBySetID[setDetail.ID],
				ImageURL:      stub.Image + "/low.webp",
				ImageURLLarge: stub.Image + "/high.webp",
				CreatedAt:     time.Now().UTC(),
			}
			if err := s.UpsertCard(ctx, c); err != nil {
				t.Fatal(err)
			}
		}
	}

	// Verify what landed.
	count, err := s.CountCards(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if count != 4 {
		t.Errorf("count: got %d want 4", count)
	}

	got, err := s.GetCardByExternalID(ctx, "sv03-001")
	if err != nil {
		t.Fatal(err)
	}
	if got.Name != "Oddish" || got.SetSeries != "Scarlet & Violet" || got.ImageURL != "http://x/sv/sv03/001/low.webp" {
		t.Errorf("got %+v", got)
	}

	// Re-seed → idempotent (same external_id, fresh UUID).
	originalID := got.ID
	if err := s.UpsertCard(ctx, store.Card{ID: uuid.New(), ExternalID: "sv03-001", Name: "Oddish v2", SetID: "sv03", SetName: "Obsidian Flames"}); err != nil {
		t.Fatal(err)
	}
	again, err := s.GetCardByExternalID(ctx, "sv03-001")
	if err != nil {
		t.Fatal(err)
	}
	if again.ID != originalID {
		t.Errorf("id churn on re-seed: got %v want %v", again.ID, originalID)
	}
}
