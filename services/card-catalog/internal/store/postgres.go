package store

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type Postgres struct {
	pool *pgxpool.Pool
}

func NewPostgres(pool *pgxpool.Pool) *Postgres {
	return &Postgres{pool: pool}
}

func (p *Postgres) UpsertCard(ctx context.Context, c Card) error {
	now := time.Now().UTC()
	_, err := p.pool.Exec(ctx, `
		INSERT INTO cards (
			id, external_id, name, set_id, set_name, set_series, rarity,
			image_url, image_url_large, release_date, created_at, updated_at
		) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12)
		ON CONFLICT (external_id) DO UPDATE SET
			name            = EXCLUDED.name,
			set_id          = EXCLUDED.set_id,
			set_name        = EXCLUDED.set_name,
			set_series      = COALESCE(EXCLUDED.set_series, cards.set_series),
			rarity          = COALESCE(EXCLUDED.rarity, cards.rarity),
			image_url       = EXCLUDED.image_url,
			image_url_large = EXCLUDED.image_url_large,
			release_date    = COALESCE(EXCLUDED.release_date, cards.release_date),
			updated_at      = $12
	`,
		c.ID, c.ExternalID, c.Name, c.SetID, c.SetName,
		nullable(c.SetSeries), nullable(c.Rarity),
		nullable(c.ImageURL), nullable(c.ImageURLLarge),
		c.ReleaseDate, c.CreatedAt, now,
	)
	if err != nil {
		return fmt.Errorf("upsert card: %w", err)
	}
	return nil
}

const selectCardCols = `id, external_id, name, set_id, set_name, set_series, rarity, image_url, image_url_large, release_date, created_at, updated_at`

func scanCard(row pgx.Row) (Card, error) {
	var c Card
	var series, rarity, image, imageLarge *string
	var releaseDate *time.Time
	err := row.Scan(
		&c.ID, &c.ExternalID, &c.Name, &c.SetID, &c.SetName,
		&series, &rarity, &image, &imageLarge,
		&releaseDate, &c.CreatedAt, &c.UpdatedAt,
	)
	if err != nil {
		return Card{}, err
	}
	c.SetSeries = deref(series)
	c.Rarity = deref(rarity)
	c.ImageURL = deref(image)
	c.ImageURLLarge = deref(imageLarge)
	c.ReleaseDate = releaseDate
	return c, nil
}

func (p *Postgres) GetCardByID(ctx context.Context, id uuid.UUID) (Card, error) {
	row := p.pool.QueryRow(ctx, `SELECT `+selectCardCols+` FROM cards WHERE id = $1`, id)
	c, err := scanCard(row)
	if errors.Is(err, pgx.ErrNoRows) {
		return Card{}, ErrNotFound
	}
	if err != nil {
		return Card{}, fmt.Errorf("get card by id: %w", err)
	}
	return c, nil
}

func (p *Postgres) GetCardByExternalID(ctx context.Context, externalID string) (Card, error) {
	row := p.pool.QueryRow(ctx, `SELECT `+selectCardCols+` FROM cards WHERE external_id = $1`, externalID)
	c, err := scanCard(row)
	if errors.Is(err, pgx.ErrNoRows) {
		return Card{}, ErrNotFound
	}
	if err != nil {
		return Card{}, fmt.Errorf("get card by external id: %w", err)
	}
	return c, nil
}

func (p *Postgres) GetCardsByIDs(ctx context.Context, ids []uuid.UUID) ([]Card, error) {
	if len(ids) == 0 {
		return nil, nil
	}
	rows, err := p.pool.Query(ctx, `SELECT `+selectCardCols+` FROM cards WHERE id = ANY($1)`, ids)
	if err != nil {
		return nil, fmt.Errorf("get cards by ids: %w", err)
	}
	defer rows.Close()
	out := make([]Card, 0, len(ids))
	for rows.Next() {
		c, err := scanCard(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, c)
	}
	return out, rows.Err()
}

func (p *Postgres) SearchCards(ctx context.Context, params SearchParams) ([]Card, error) {
	var (
		clauses []string
		args    []any
	)
	if q := strings.TrimSpace(params.Query); q != "" {
		args = append(args, "%"+q+"%")
		clauses = append(clauses, fmt.Sprintf("name ILIKE $%d", len(args)))
	}
	if params.SetIDFilter != "" {
		args = append(args, params.SetIDFilter)
		clauses = append(clauses, fmt.Sprintf("set_id = $%d", len(args)))
	}
	if params.RarityFilter != "" {
		args = append(args, params.RarityFilter)
		clauses = append(clauses, fmt.Sprintf("rarity = $%d", len(args)))
	}

	where := ""
	if len(clauses) > 0 {
		where = "WHERE " + strings.Join(clauses, " AND ")
	}

	limit := params.Limit
	if limit <= 0 {
		limit = 25
	}
	args = append(args, limit)
	limitPos := len(args)
	args = append(args, params.Offset)
	offsetPos := len(args)

	query := fmt.Sprintf(`
		SELECT %s FROM cards
		%s
		ORDER BY set_id, external_id
		LIMIT $%d OFFSET $%d
	`, selectCardCols, where, limitPos, offsetPos)

	rows, err := p.pool.Query(ctx, query, args...)
	if err != nil {
		return nil, fmt.Errorf("search cards: %w", err)
	}
	defer rows.Close()
	var out []Card
	for rows.Next() {
		c, err := scanCard(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, c)
	}
	return out, rows.Err()
}

func (p *Postgres) ListSets(ctx context.Context) ([]SetSummary, error) {
	rows, err := p.pool.Query(ctx, `
		SELECT set_id, set_name, COALESCE(MAX(set_series), '') AS series, COUNT(*)::int AS card_count
		FROM cards
		GROUP BY set_id, set_name
		ORDER BY set_id
	`)
	if err != nil {
		return nil, fmt.Errorf("list sets: %w", err)
	}
	defer rows.Close()
	var out []SetSummary
	for rows.Next() {
		var s SetSummary
		if err := rows.Scan(&s.ID, &s.Name, &s.Series, &s.CardCount); err != nil {
			return nil, err
		}
		out = append(out, s)
	}
	return out, rows.Err()
}

func (p *Postgres) CountCards(ctx context.Context) (int, error) {
	var n int
	if err := p.pool.QueryRow(ctx, `SELECT COUNT(*)::int FROM cards`).Scan(&n); err != nil {
		return 0, fmt.Errorf("count cards: %w", err)
	}
	return n, nil
}

func nullable(s string) *string {
	if s == "" {
		return nil
	}
	return &s
}

func deref(s *string) string {
	if s == nil {
		return ""
	}
	return *s
}
