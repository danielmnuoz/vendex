package tcgdex

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func newTestServer(t *testing.T, handler http.HandlerFunc) (*Client, func()) {
	t.Helper()
	srv := httptest.NewServer(handler)
	return New(srv.URL, srv.Client()), srv.Close
}

func TestListSets(t *testing.T) {
	c, cleanup := newTestServer(t, func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/sets" {
			t.Errorf("path: got %s", r.URL.Path)
		}
		_, _ = w.Write([]byte(`[{"id":"sv03","name":"Obsidian Flames","cardCount":{"total":230,"official":197}}]`))
	})
	defer cleanup()

	sets, err := c.ListSets(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	if len(sets) != 1 || sets[0].ID != "sv03" || sets[0].CardCount.Total != 230 {
		t.Errorf("unexpected sets: %+v", sets)
	}
}

func TestGetSet(t *testing.T) {
	c, cleanup := newTestServer(t, func(w http.ResponseWriter, r *http.Request) {
		if !strings.HasPrefix(r.URL.Path, "/sets/") {
			t.Errorf("path: got %s", r.URL.Path)
		}
		_, _ = w.Write([]byte(`{"id":"sv03","name":"Obsidian Flames","cards":[{"id":"sv03-001","name":"Oddish","image":"https://example/sv/sv03/001","localId":"001"}]}`))
	})
	defer cleanup()

	set, err := c.GetSet(context.Background(), "sv03")
	if err != nil {
		t.Fatal(err)
	}
	if set.ID != "sv03" || len(set.Cards) != 1 || set.Cards[0].Name != "Oddish" {
		t.Errorf("unexpected set: %+v", set)
	}
}

func TestListSeries(t *testing.T) {
	c, cleanup := newTestServer(t, func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`[{"id":"sv","name":"Scarlet & Violet"}]`))
	})
	defer cleanup()

	series, err := c.ListSeries(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	if len(series) != 1 || series[0].Name != "Scarlet & Violet" {
		t.Errorf("unexpected: %+v", series)
	}
}

func TestGetSeries(t *testing.T) {
	c, cleanup := newTestServer(t, func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`{"id":"sv","name":"Scarlet & Violet","releaseDate":"2023-03-31","sets":[{"id":"sv01","name":"Scarlet & Violet"},{"id":"sv03","name":"Obsidian Flames"}]}`))
	})
	defer cleanup()

	detail, err := c.GetSeries(context.Background(), "sv")
	if err != nil {
		t.Fatal(err)
	}
	if detail.ReleaseDate != "2023-03-31" || len(detail.Sets) != 2 {
		t.Errorf("unexpected: %+v", detail)
	}
}

func TestGetJSON_NonOKStatus(t *testing.T) {
	c, cleanup := newTestServer(t, func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
		_, _ = w.Write([]byte("missing"))
	})
	defer cleanup()

	if _, err := c.GetSet(context.Background(), "nope"); err == nil {
		t.Error("expected error on 404")
	}
}

func TestGetJSON_BadJSON(t *testing.T) {
	c, cleanup := newTestServer(t, func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte("not json"))
	})
	defer cleanup()

	if _, err := c.ListSets(context.Background()); err == nil {
		t.Error("expected decode error")
	}
}

func TestNew_Defaults(t *testing.T) {
	c := New("", nil)
	if c.baseURL != DefaultBaseURL {
		t.Errorf("baseURL: got %q want default", c.baseURL)
	}
	if c.httpClient == nil {
		t.Error("expected non-nil http client")
	}
}
