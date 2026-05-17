package store

import (
	"context"
	"errors"
	"time"

	"github.com/google/uuid"
)

var ErrNotFound = errors.New("not found")

type Card struct {
	ID            uuid.UUID
	ExternalID    string
	Name          string
	SetID         string
	SetName       string
	SetSeries     string
	Rarity        string
	ImageURL      string
	ImageURLLarge string
	ReleaseDate   *time.Time
	CreatedAt     time.Time
	UpdatedAt     time.Time
}

type SetSummary struct {
	ID        string
	Name      string
	Series    string
	CardCount int
}

type SearchParams struct {
	Query        string
	SetIDFilter  string
	RarityFilter string
	Limit        int
	// Offset cursor — opaque to callers. The service translates page tokens
	// to and from this value.
	Offset int
}

// Store is the persistence boundary for the card catalog.
type Store interface {
	UpsertCard(ctx context.Context, c Card) error
	GetCardByID(ctx context.Context, id uuid.UUID) (Card, error)
	GetCardsByIDs(ctx context.Context, ids []uuid.UUID) ([]Card, error)
	GetCardByExternalID(ctx context.Context, externalID string) (Card, error)
	SearchCards(ctx context.Context, p SearchParams) ([]Card, error)
	ListSets(ctx context.Context) ([]SetSummary, error)
	CountCards(ctx context.Context) (int, error)
}
