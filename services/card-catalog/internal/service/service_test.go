package service

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/danielmnuoz/vendex/services/card-catalog/internal/store"
	"github.com/google/uuid"
)

func seedFake(t *testing.T, n int) (*store.Fake, []store.Card) {
	t.Helper()
	f := store.NewFake()
	ctx := context.Background()
	cards := make([]store.Card, 0, n)
	for i := 0; i < n; i++ {
		c := store.Card{
			ID:         uuid.New(),
			ExternalID: pad("sv03-", i+1),
			Name:       randomName(i),
			SetID:      "sv03",
			SetName:    "Obsidian Flames",
			Rarity:     "Common",
			CreatedAt:  time.Now().UTC(),
		}
		if err := f.UpsertCard(ctx, c); err != nil {
			t.Fatal(err)
		}
		cards = append(cards, c)
	}
	return f, cards
}

func pad(prefix string, n int) string {
	s := "000" + itoa(n)
	return prefix + s[len(s)-3:]
}

func itoa(n int) string {
	if n == 0 {
		return "0"
	}
	digits := []byte{}
	for n > 0 {
		digits = append([]byte{byte('0' + n%10)}, digits...)
		n /= 10
	}
	return string(digits)
}

func randomName(i int) string {
	names := []string{"Oddish", "Pikachu", "Charizard", "Bulbasaur", "Mewtwo", "Eevee", "Squirtle", "Snorlax"}
	return names[i%len(names)]
}

func TestSearchCards_Pagination(t *testing.T) {
	f, _ := seedFake(t, 30)
	svc := New(f, nil)
	ctx := context.Background()

	first, err := svc.SearchCards(ctx, SearchParams{PageSize: 10})
	if err != nil {
		t.Fatal(err)
	}
	if len(first.Cards) != 10 {
		t.Errorf("first page: got %d, want 10", len(first.Cards))
	}
	if first.NextPageToken == "" {
		t.Error("expected NextPageToken on first page")
	}

	second, err := svc.SearchCards(ctx, SearchParams{PageSize: 10, PageToken: first.NextPageToken})
	if err != nil {
		t.Fatal(err)
	}
	if len(second.Cards) != 10 {
		t.Errorf("second page: got %d, want 10", len(second.Cards))
	}

	// Third page should be the last 10 with no next token.
	third, err := svc.SearchCards(ctx, SearchParams{PageSize: 10, PageToken: second.NextPageToken})
	if err != nil {
		t.Fatal(err)
	}
	if len(third.Cards) != 10 {
		t.Errorf("third page: got %d, want 10", len(third.Cards))
	}
	if third.NextPageToken != "" {
		t.Errorf("third page should not have next token, got %q", third.NextPageToken)
	}
}

func TestSearchCards_PageSizeClamped(t *testing.T) {
	f, _ := seedFake(t, 50)
	svc := New(f, nil)
	ctx := context.Background()

	res, err := svc.SearchCards(ctx, SearchParams{PageSize: 1000})
	if err != nil {
		t.Fatal(err)
	}
	if len(res.Cards) > 100 {
		t.Errorf("page size should be clamped to 100, got %d", len(res.Cards))
	}
}

func TestSearchCards_Filter(t *testing.T) {
	f, _ := seedFake(t, 16)
	svc := New(f, nil)
	res, err := svc.SearchCards(context.Background(), SearchParams{Query: "Pikachu"})
	if err != nil {
		t.Fatal(err)
	}
	for _, c := range res.Cards {
		if c.Name != "Pikachu" {
			t.Errorf("query=Pikachu returned %q", c.Name)
		}
	}
}

func TestSearchCards_InvalidPageToken(t *testing.T) {
	f, _ := seedFake(t, 1)
	svc := New(f, nil)

	cases := []string{"not-base64!!", "AAAA", "LTU"} // garbage, "" decoded, "-5"
	for _, tok := range cases {
		_, err := svc.SearchCards(context.Background(), SearchParams{PageToken: tok})
		if !errors.Is(err, ErrInvalidPageToken) {
			t.Errorf("token=%q: got %v want ErrInvalidPageToken", tok, err)
		}
	}
}

func TestGetCardByID(t *testing.T) {
	f, cards := seedFake(t, 3)
	svc := New(f, nil)
	got, err := svc.GetCardByID(context.Background(), cards[0].ID)
	if err != nil {
		t.Fatal(err)
	}
	if got.ID != cards[0].ID {
		t.Errorf("id: got %v want %v", got.ID, cards[0].ID)
	}

	if _, err := svc.GetCardByID(context.Background(), uuid.New()); !errors.Is(err, store.ErrNotFound) {
		t.Errorf("missing: got %v want ErrNotFound", err)
	}
}

func TestGetCardsByIDs(t *testing.T) {
	f, cards := seedFake(t, 5)
	svc := New(f, nil)
	ids := []uuid.UUID{cards[0].ID, cards[2].ID, uuid.New()} // last is missing
	got, err := svc.GetCardsByIDs(context.Background(), ids)
	if err != nil {
		t.Fatal(err)
	}
	if len(got) != 2 {
		t.Errorf("got %d cards, want 2 (missing id should be dropped)", len(got))
	}
}

func TestGetCardsByIDs_Empty(t *testing.T) {
	f, _ := seedFake(t, 1)
	svc := New(f, nil)
	got, err := svc.GetCardsByIDs(context.Background(), nil)
	if err != nil {
		t.Fatal(err)
	}
	if len(got) != 0 {
		t.Errorf("expected empty, got %d", len(got))
	}
}

// fakeCache is a service.Cache implementation backed by a map. Records calls
// so tests can verify cache-aside behavior.
type fakeCache struct {
	store map[uuid.UUID]store.Card
	gets  int
	sets  int
}

func newFakeCache() *fakeCache { return &fakeCache{store: map[uuid.UUID]store.Card{}} }

func (f *fakeCache) Get(_ context.Context, id uuid.UUID) (store.Card, bool, error) {
	f.gets++
	c, ok := f.store[id]
	return c, ok, nil
}

func (f *fakeCache) Set(_ context.Context, c store.Card) {
	f.sets++
	f.store[c.ID] = c
}

func TestGetCardByID_CacheAside(t *testing.T) {
	f, cards := seedFake(t, 1)
	fc := newFakeCache()
	svc := New(f, fc)
	ctx := context.Background()

	// First call → miss, then populate.
	if _, err := svc.GetCardByID(ctx, cards[0].ID); err != nil {
		t.Fatal(err)
	}
	if fc.sets != 1 {
		t.Errorf("expected 1 cache Set after miss, got %d", fc.sets)
	}

	// Second call → cache hit, no extra store fetch needed.
	if _, err := svc.GetCardByID(ctx, cards[0].ID); err != nil {
		t.Fatal(err)
	}
	if fc.gets != 2 {
		t.Errorf("expected 2 cache Gets, got %d", fc.gets)
	}
	if fc.sets != 1 {
		t.Errorf("expected sets to stay at 1 (hit shouldn't repopulate), got %d", fc.sets)
	}
}

func TestGetCardsByIDs_CacheAside_PartialHits(t *testing.T) {
	f, cards := seedFake(t, 4)
	fc := newFakeCache()
	svc := New(f, fc)
	ctx := context.Background()

	// Warm cache with two of the four IDs.
	_, _ = svc.GetCardByID(ctx, cards[0].ID)
	_, _ = svc.GetCardByID(ctx, cards[1].ID)
	priorSets := fc.sets

	// Batch request includes the two cached + two cold IDs.
	ids := []uuid.UUID{cards[0].ID, cards[1].ID, cards[2].ID, cards[3].ID}
	got, err := svc.GetCardsByIDs(ctx, ids)
	if err != nil {
		t.Fatal(err)
	}
	if len(got) != 4 {
		t.Errorf("got %d cards, want 4", len(got))
	}
	// Two new cards must have been set into the cache.
	if fc.sets-priorSets != 2 {
		t.Errorf("expected 2 new cache Sets for misses, got %d", fc.sets-priorSets)
	}
}

func TestListSets(t *testing.T) {
	f := store.NewFake()
	ctx := context.Background()
	cards := []store.Card{
		{ID: uuid.New(), ExternalID: "a-1", Name: "X", SetID: "a", SetName: "A"},
		{ID: uuid.New(), ExternalID: "a-2", Name: "Y", SetID: "a", SetName: "A"},
		{ID: uuid.New(), ExternalID: "b-1", Name: "Z", SetID: "b", SetName: "B"},
	}
	for _, c := range cards {
		_ = f.UpsertCard(ctx, c)
	}
	svc := New(f, nil)
	sets, err := svc.ListSets(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if len(sets) != 2 {
		t.Errorf("got %d sets, want 2", len(sets))
	}
	var aSet store.SetSummary
	for _, s := range sets {
		if s.ID == "a" {
			aSet = s
		}
	}
	if aSet.CardCount != 2 {
		t.Errorf("set a count: got %d want 2", aSet.CardCount)
	}
}

func TestUpsertPreservesID(t *testing.T) {
	// Re-upserting a card with the same external_id but a fresh UUID should
	// keep the original UUID — this is how re-syncs avoid churning IDs.
	f := store.NewFake()
	ctx := context.Background()
	id := uuid.New()
	_ = f.UpsertCard(ctx, store.Card{ID: id, ExternalID: "sv03-001", Name: "Oddish", SetID: "sv03", SetName: "OF"})
	_ = f.UpsertCard(ctx, store.Card{ID: uuid.New(), ExternalID: "sv03-001", Name: "Oddish (updated)", SetID: "sv03", SetName: "OF"})

	got, err := f.GetCardByExternalID(ctx, "sv03-001")
	if err != nil {
		t.Fatal(err)
	}
	if got.ID != id {
		t.Errorf("id changed on re-upsert: got %v want %v", got.ID, id)
	}
	if got.Name != "Oddish (updated)" {
		t.Errorf("name: got %q want updated", got.Name)
	}
}
