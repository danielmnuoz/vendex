// Package service is the card-catalog business logic, decoupled from gRPC.
package service

import (
	"context"
	"encoding/base64"
	"errors"
	"fmt"
	"strconv"

	"github.com/danielmnuoz/vendex/services/card-catalog/internal/store"
	"github.com/google/uuid"
)

var ErrInvalidPageToken = errors.New("invalid page_token")

// Cache is the minimal cache interface the service depends on. The real
// implementation lives in the cache package; tests inject a fake.
type Cache interface {
	Get(ctx context.Context, id uuid.UUID) (store.Card, bool, error)
	Set(ctx context.Context, card store.Card)
}

type Service struct {
	store store.Store
	cache Cache // may be nil — service degrades gracefully
}

func New(s store.Store, c Cache) *Service {
	return &Service{store: s, cache: c}
}

type SearchParams struct {
	Query        string
	SetIDFilter  string
	RarityFilter string
	PageSize     int
	PageToken    string
}

type SearchResult struct {
	Cards         []store.Card
	NextPageToken string
}

const (
	defaultPageSize = 25
	maxPageSize     = 100
)

func (s *Service) SearchCards(ctx context.Context, p SearchParams) (SearchResult, error) {
	pageSize := p.PageSize
	if pageSize <= 0 {
		pageSize = defaultPageSize
	}
	if pageSize > maxPageSize {
		pageSize = maxPageSize
	}
	offset, err := decodePageToken(p.PageToken)
	if err != nil {
		return SearchResult{}, err
	}
	// Fetch one extra row to detect "there's a next page" without a count query.
	rows, err := s.store.SearchCards(ctx, store.SearchParams{
		Query:        p.Query,
		SetIDFilter:  p.SetIDFilter,
		RarityFilter: p.RarityFilter,
		Limit:        pageSize + 1,
		Offset:       offset,
	})
	if err != nil {
		return SearchResult{}, err
	}
	var next string
	if len(rows) > pageSize {
		rows = rows[:pageSize]
		next = encodePageToken(offset + pageSize)
	}
	return SearchResult{Cards: rows, NextPageToken: next}, nil
}

func (s *Service) GetCardByID(ctx context.Context, id uuid.UUID) (store.Card, error) {
	if s.cache != nil {
		if c, hit, _ := s.cache.Get(ctx, id); hit {
			return c, nil
		}
	}
	c, err := s.store.GetCardByID(ctx, id)
	if err != nil {
		return store.Card{}, err
	}
	if s.cache != nil {
		s.cache.Set(ctx, c)
	}
	return c, nil
}

func (s *Service) GetCardsByIDs(ctx context.Context, ids []uuid.UUID) ([]store.Card, error) {
	if len(ids) == 0 {
		return nil, nil
	}
	if s.cache == nil {
		return s.store.GetCardsByIDs(ctx, ids)
	}
	// Cache-aside per ID. The cache wins what it can; the misses become a
	// single ANY($1) round-trip to Postgres.
	out := make([]store.Card, 0, len(ids))
	var misses []uuid.UUID
	for _, id := range ids {
		if c, hit, _ := s.cache.Get(ctx, id); hit {
			out = append(out, c)
			continue
		}
		misses = append(misses, id)
	}
	if len(misses) > 0 {
		fetched, err := s.store.GetCardsByIDs(ctx, misses)
		if err != nil {
			return nil, err
		}
		for _, c := range fetched {
			s.cache.Set(ctx, c)
			out = append(out, c)
		}
	}
	return out, nil
}

func (s *Service) ListSets(ctx context.Context) ([]store.SetSummary, error) {
	return s.store.ListSets(ctx)
}

// Opaque page-token encoding. Offset-as-base64 is sufficient for catalog-size
// search results (~20k rows worst case). If listings ever need real-time
// stability against concurrent inserts we'd switch to (set_id, external_id)
// keyset cursors, but the catalog only mutates during sync, so this is fine.

func encodePageToken(offset int) string {
	return base64.RawURLEncoding.EncodeToString([]byte(strconv.Itoa(offset)))
}

func decodePageToken(tok string) (int, error) {
	if tok == "" {
		return 0, nil
	}
	raw, err := base64.RawURLEncoding.DecodeString(tok)
	if err != nil {
		return 0, fmt.Errorf("%w: %v", ErrInvalidPageToken, err)
	}
	n, err := strconv.Atoi(string(raw))
	if err != nil || n < 0 {
		return 0, ErrInvalidPageToken
	}
	return n, nil
}
