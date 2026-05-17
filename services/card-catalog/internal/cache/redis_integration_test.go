//go:build integration

package cache

import (
	"context"
	"os"
	"testing"
	"time"

	"github.com/danielmnuoz/vendex/services/card-catalog/internal/store"
	"github.com/google/uuid"
)

func TestRedisCache_RoundTrip(t *testing.T) {
	addr := os.Getenv("CARD_CATALOG_TEST_REDIS_ADDR")
	if addr == "" {
		// Default to docker-compose redis if available.
		addr = "localhost:6379"
	}
	c := New(addr, time.Minute)
	defer c.Close()

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if err := c.Ping(ctx); err != nil {
		t.Skipf("redis not reachable at %s: %v", addr, err)
	}

	card := store.Card{
		ID:         uuid.New(),
		ExternalID: "sv03-001",
		Name:       "Oddish",
		SetID:      "sv03",
		SetName:    "Obsidian Flames",
	}

	// Miss → not found.
	if _, hit, err := c.Get(ctx, card.ID); err != nil || hit {
		t.Errorf("expected miss for fresh ID, got hit=%v err=%v", hit, err)
	}

	// Set then Get → hit.
	c.Set(ctx, card)
	got, hit, err := c.Get(ctx, card.ID)
	if err != nil || !hit {
		t.Fatalf("expected hit after set, got hit=%v err=%v", hit, err)
	}
	if got.Name != "Oddish" || got.SetID != "sv03" {
		t.Errorf("round-tripped card differs: %+v", got)
	}
}

func TestRedisCache_NilSafe(t *testing.T) {
	// Calling methods on a nil *Cache must not panic — this is how the
	// service degrades when Redis is unreachable at startup.
	var c *Cache
	ctx := context.Background()
	if _, hit, err := c.Get(ctx, uuid.New()); hit || err != nil {
		t.Errorf("nil Get should miss, got hit=%v err=%v", hit, err)
	}
	c.Set(ctx, store.Card{}) // must not panic
	if err := c.Close(); err != nil {
		t.Errorf("nil Close: %v", err)
	}
}
