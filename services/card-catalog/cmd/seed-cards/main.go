// seed-cards walks TCGdex and populates the card_catalog_db.cards table.
//
// Idempotent: re-running upserts on (external_id), so existing UUIDs are
// preserved and existing nullable fields (rarity, series, release_date)
// are not clobbered if upstream still doesn't have them.
//
// Usage:
//
//	CARD_CATALOG_DATABASE_URL=postgres://... go run ./scripts/seed-cards
//
// Optional env: TCGDEX_BASE_URL (default https://api.tcgdex.net/v2/en)
package main

import (
	"context"
	"flag"
	"log"
	"os"
	"time"

	"github.com/danielmnuoz/vendex/services/card-catalog/internal/store"
	"github.com/danielmnuoz/vendex/services/card-catalog/internal/tcgdex"
	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"
)

func main() {
	dryRun := flag.Bool("dry-run", false, "fetch but don't write")
	maxSets := flag.Int("max-sets", 0, "if >0, only process the first N sets (useful for smoke tests)")
	flag.Parse()

	dbURL := os.Getenv("CARD_CATALOG_DATABASE_URL")
	if dbURL == "" {
		log.Fatal("CARD_CATALOG_DATABASE_URL is required")
	}
	baseURL := os.Getenv("TCGDEX_BASE_URL")

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Minute)
	defer cancel()

	pool, err := pgxpool.New(ctx, dbURL)
	if err != nil {
		log.Fatalf("connect db: %v", err)
	}
	defer pool.Close()

	client := tcgdex.New(baseURL, nil)
	s := store.NewPostgres(pool)

	// Build set_id -> series_name mapping from the series endpoint. Best-effort
	// — if the call fails we still seed cards, just without series metadata.
	seriesBySetID := loadSeriesMapping(ctx, client)

	sets, err := client.ListSets(ctx)
	if err != nil {
		log.Fatalf("list sets: %v", err)
	}
	log.Printf("seed: %d sets to process", len(sets))
	if *maxSets > 0 && len(sets) > *maxSets {
		sets = sets[:*maxSets]
		log.Printf("seed: limiting to first %d sets", *maxSets)
	}

	var totalCards int
	for i, summary := range sets {
		setDetail, err := client.GetSet(ctx, summary.ID)
		if err != nil {
			log.Printf("seed: get set %s failed (%v) — skipping", summary.ID, err)
			continue
		}
		series := seriesBySetID[summary.ID]
		for _, stub := range setDetail.Cards {
			card := store.Card{
				ID:            uuid.New(),
				ExternalID:    stub.ID,
				Name:          stub.Name,
				SetID:         setDetail.ID,
				SetName:       setDetail.Name,
				SetSeries:     series,
				ImageURL:      stub.Image + "/low.webp",
				ImageURLLarge: stub.Image + "/high.webp",
				CreatedAt:     time.Now().UTC(),
			}
			if *dryRun {
				continue
			}
			if err := s.UpsertCard(ctx, card); err != nil {
				log.Printf("seed: upsert %s failed: %v", stub.ID, err)
				continue
			}
			totalCards++
		}
		if (i+1)%10 == 0 {
			log.Printf("seed: processed %d/%d sets (%d cards so far)", i+1, len(sets), totalCards)
		}
	}

	count, _ := s.CountCards(ctx)
	log.Printf("seed: done. upserted=%d cards, total in db=%d", totalCards, count)
}

func loadSeriesMapping(ctx context.Context, c *tcgdex.Client) map[string]string {
	series, err := c.ListSeries(ctx)
	if err != nil {
		log.Printf("seed: list series failed (%v) — series metadata will be empty", err)
		return map[string]string{}
	}
	out := map[string]string{}
	for _, s := range series {
		detail, err := c.GetSeries(ctx, s.ID)
		if err != nil {
			log.Printf("seed: get series %s failed: %v", s.ID, err)
			continue
		}
		for _, set := range detail.Sets {
			out[set.ID] = detail.Name
		}
	}
	return out
}
