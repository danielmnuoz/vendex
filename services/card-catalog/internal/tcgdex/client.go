// Package tcgdex is a thin HTTP client for the public TCGdex API
// (https://tcgdex.dev). It's used exclusively by the seed script and
// (eventually) the re-sync cron — never on the request path.
package tcgdex

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"
)

const DefaultBaseURL = "https://api.tcgdex.net/v2/en"

type Client struct {
	baseURL    string
	httpClient *http.Client
}

func New(baseURL string, httpClient *http.Client) *Client {
	if baseURL == "" {
		baseURL = DefaultBaseURL
	}
	if httpClient == nil {
		httpClient = &http.Client{Timeout: 30 * time.Second}
	}
	return &Client{baseURL: baseURL, httpClient: httpClient}
}

// SetSummary is the shape returned by GET /sets.
type SetSummary struct {
	ID        string `json:"id"`
	Name      string `json:"name"`
	CardCount struct {
		Total    int `json:"total"`
		Official int `json:"official"`
	} `json:"cardCount"`
}

// SetDetail is the shape returned by GET /sets/{id}. The cards slice
// contains stubs only (no rarity) — full card detail requires a
// per-card fetch.
type SetDetail struct {
	ID    string     `json:"id"`
	Name  string     `json:"name"`
	Cards []CardStub `json:"cards"`
}

type CardStub struct {
	ID      string `json:"id"` // e.g. "sv03-001"
	Name    string `json:"name"`
	Image   string `json:"image"` // base URL — append /low.webp or /high.webp for the actual image
	LocalID string `json:"localId"`
}

// SeriesSummary is the shape returned by GET /series.
type SeriesSummary struct {
	ID   string `json:"id"`
	Name string `json:"name"`
}

// SeriesDetail is the shape returned by GET /series/{id}. ReleaseDate
// applies to the series as a whole.
type SeriesDetail struct {
	ID          string             `json:"id"`
	Name        string             `json:"name"`
	ReleaseDate string             `json:"releaseDate"`
	Sets        []SeriesSetSummary `json:"sets"`
}

type SeriesSetSummary struct {
	ID   string `json:"id"`
	Name string `json:"name"`
}

func (c *Client) ListSets(ctx context.Context) ([]SetSummary, error) {
	var out []SetSummary
	if err := c.getJSON(ctx, "/sets", &out); err != nil {
		return nil, err
	}
	return out, nil
}

func (c *Client) GetSet(ctx context.Context, setID string) (SetDetail, error) {
	var out SetDetail
	if err := c.getJSON(ctx, "/sets/"+setID, &out); err != nil {
		return SetDetail{}, err
	}
	return out, nil
}

func (c *Client) ListSeries(ctx context.Context) ([]SeriesSummary, error) {
	var out []SeriesSummary
	if err := c.getJSON(ctx, "/series", &out); err != nil {
		return nil, err
	}
	return out, nil
}

func (c *Client) GetSeries(ctx context.Context, seriesID string) (SeriesDetail, error) {
	var out SeriesDetail
	if err := c.getJSON(ctx, "/series/"+seriesID, &out); err != nil {
		return SeriesDetail{}, err
	}
	return out, nil
}

func (c *Client) getJSON(ctx context.Context, path string, out any) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, c.baseURL+path, nil)
	if err != nil {
		return fmt.Errorf("build request %s: %w", path, err)
	}
	req.Header.Set("Accept", "application/json")
	resp, err := c.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("get %s: %w", path, err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 1024))
		return fmt.Errorf("get %s: status %d: %s", path, resp.StatusCode, string(body))
	}
	if err := json.NewDecoder(resp.Body).Decode(out); err != nil {
		return fmt.Errorf("decode %s: %w", path, err)
	}
	return nil
}
