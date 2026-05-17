package store

import (
	"context"
	"sort"
	"strings"
	"sync"

	"github.com/google/uuid"
)

// Fake is an in-memory Store implementation for unit tests.
type Fake struct {
	mu           sync.Mutex
	cards        map[uuid.UUID]Card
	cardsByExtID map[string]uuid.UUID
}

func NewFake() *Fake {
	return &Fake{
		cards:        map[uuid.UUID]Card{},
		cardsByExtID: map[string]uuid.UUID{},
	}
}

func (f *Fake) UpsertCard(_ context.Context, c Card) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	// If a card with this external_id already exists, preserve its UUID
	// so re-syncs don't churn IDs (mirrors the postgres ON CONFLICT path).
	if existingID, ok := f.cardsByExtID[c.ExternalID]; ok {
		c.ID = existingID
	}
	f.cards[c.ID] = c
	f.cardsByExtID[c.ExternalID] = c.ID
	return nil
}

func (f *Fake) GetCardByID(_ context.Context, id uuid.UUID) (Card, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	c, ok := f.cards[id]
	if !ok {
		return Card{}, ErrNotFound
	}
	return c, nil
}

func (f *Fake) GetCardByExternalID(_ context.Context, externalID string) (Card, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	id, ok := f.cardsByExtID[externalID]
	if !ok {
		return Card{}, ErrNotFound
	}
	return f.cards[id], nil
}

func (f *Fake) GetCardsByIDs(_ context.Context, ids []uuid.UUID) ([]Card, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	out := make([]Card, 0, len(ids))
	for _, id := range ids {
		if c, ok := f.cards[id]; ok {
			out = append(out, c)
		}
	}
	return out, nil
}

func (f *Fake) SearchCards(_ context.Context, p SearchParams) ([]Card, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	limit := p.Limit
	if limit <= 0 {
		limit = 25
	}
	all := make([]Card, 0, len(f.cards))
	q := strings.ToLower(strings.TrimSpace(p.Query))
	for _, c := range f.cards {
		if q != "" && !strings.Contains(strings.ToLower(c.Name), q) {
			continue
		}
		if p.SetIDFilter != "" && c.SetID != p.SetIDFilter {
			continue
		}
		if p.RarityFilter != "" && c.Rarity != p.RarityFilter {
			continue
		}
		all = append(all, c)
	}
	sort.Slice(all, func(i, j int) bool {
		if all[i].SetID != all[j].SetID {
			return all[i].SetID < all[j].SetID
		}
		return all[i].ExternalID < all[j].ExternalID
	})
	start := p.Offset
	if start > len(all) {
		start = len(all)
	}
	end := start + limit
	if end > len(all) {
		end = len(all)
	}
	return all[start:end], nil
}

func (f *Fake) ListSets(_ context.Context) ([]SetSummary, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	bySet := map[string]*SetSummary{}
	for _, c := range f.cards {
		s, ok := bySet[c.SetID]
		if !ok {
			s = &SetSummary{ID: c.SetID, Name: c.SetName, Series: c.SetSeries}
			bySet[c.SetID] = s
		}
		s.CardCount++
	}
	out := make([]SetSummary, 0, len(bySet))
	for _, s := range bySet {
		out = append(out, *s)
	}
	sort.Slice(out, func(i, j int) bool { return out[i].ID < out[j].ID })
	return out, nil
}

func (f *Fake) CountCards(_ context.Context) (int, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	return len(f.cards), nil
}
