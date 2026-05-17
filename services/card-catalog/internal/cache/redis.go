// Package cache wraps Redis to cache card lookups. The cache is
// best-effort — a miss falls through to Postgres, a Redis error never
// fails the request.
package cache

import (
	"context"
	"encoding/json"
	"errors"
	"log"
	"time"

	"github.com/danielmnuoz/vendex/services/card-catalog/internal/store"
	"github.com/google/uuid"
	"github.com/redis/go-redis/v9"
)

type Cache struct {
	client *redis.Client
	ttl    time.Duration
}

func New(addr string, ttl time.Duration) *Cache {
	return &Cache{
		client: redis.NewClient(&redis.Options{Addr: addr}),
		ttl:    ttl,
	}
}

func (c *Cache) Close() error {
	if c == nil || c.client == nil {
		return nil
	}
	return c.client.Close()
}

func keyForID(id uuid.UUID) string { return "card:id:" + id.String() }

// Get returns a card if it's in cache. A miss returns (Card{}, false, nil);
// only catastrophic Redis errors propagate (and even then the caller is
// expected to treat as a miss).
func (c *Cache) Get(ctx context.Context, id uuid.UUID) (store.Card, bool, error) {
	if c == nil || c.client == nil {
		return store.Card{}, false, nil
	}
	raw, err := c.client.Get(ctx, keyForID(id)).Bytes()
	if errors.Is(err, redis.Nil) {
		return store.Card{}, false, nil
	}
	if err != nil {
		return store.Card{}, false, err
	}
	var card store.Card
	if err := json.Unmarshal(raw, &card); err != nil {
		// Bad cache entry — treat as a miss and don't fail the caller.
		log.Printf("cache: bad json for %s: %v", id, err)
		return store.Card{}, false, nil
	}
	return card, true, nil
}

// Set stores a card. Errors are logged and swallowed — caching is best-effort.
func (c *Cache) Set(ctx context.Context, card store.Card) {
	if c == nil || c.client == nil {
		return
	}
	raw, err := json.Marshal(card)
	if err != nil {
		log.Printf("cache: marshal %s: %v", card.ID, err)
		return
	}
	if err := c.client.Set(ctx, keyForID(card.ID), raw, c.ttl).Err(); err != nil {
		log.Printf("cache: set %s: %v", card.ID, err)
	}
}

// Ping verifies Redis is reachable. Used at startup.
func (c *Cache) Ping(ctx context.Context) error {
	if c == nil || c.client == nil {
		return errors.New("cache not configured")
	}
	return c.client.Ping(ctx).Err()
}
