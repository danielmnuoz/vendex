package server

import (
	"context"
	"net"
	"testing"
	"time"

	cardsv1 "github.com/danielmnuoz/vendex/proto/gen/go/cards/v1"
	"github.com/danielmnuoz/vendex/services/card-catalog/internal/service"
	"github.com/danielmnuoz/vendex/services/card-catalog/internal/store"
	"github.com/google/uuid"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/status"
	"google.golang.org/grpc/test/bufconn"
)

const bufSize = 1024 * 1024

func newClient(t *testing.T) (cardsv1.CardCatalogServiceClient, *store.Fake, func()) {
	t.Helper()
	f := store.NewFake()
	svc := service.New(f, nil)
	srv := grpc.NewServer()
	cardsv1.RegisterCardCatalogServiceServer(srv, New(svc))

	lis := bufconn.Listen(bufSize)
	go func() { _ = srv.Serve(lis) }()

	conn, err := grpc.NewClient("passthrough://bufnet",
		grpc.WithContextDialer(func(_ context.Context, _ string) (net.Conn, error) {
			return lis.DialContext(context.Background())
		}),
		grpc.WithTransportCredentials(insecure.NewCredentials()),
	)
	if err != nil {
		t.Fatal(err)
	}
	cleanup := func() {
		_ = conn.Close()
		srv.Stop()
	}
	return cardsv1.NewCardCatalogServiceClient(conn), f, cleanup
}

func seedClientCards(t *testing.T, f *store.Fake, n int) []store.Card {
	t.Helper()
	ctx := context.Background()
	out := make([]store.Card, 0, n)
	for i := 0; i < n; i++ {
		c := store.Card{
			ID:         uuid.New(),
			ExternalID: pad("sv03-", i+1),
			Name:       "Card " + itoa(i+1),
			SetID:      "sv03",
			SetName:    "Obsidian Flames",
			Rarity:     "Common",
			CreatedAt:  time.Now().UTC(),
		}
		_ = f.UpsertCard(ctx, c)
		out = append(out, c)
	}
	return out
}

func pad(prefix string, n int) string {
	s := "000" + itoa(n)
	return prefix + s[len(s)-3:]
}

func itoa(n int) string {
	if n == 0 {
		return "0"
	}
	d := []byte{}
	for n > 0 {
		d = append([]byte{byte('0' + n%10)}, d...)
		n /= 10
	}
	return string(d)
}

func TestServer_SearchCards(t *testing.T) {
	c, f, cleanup := newClient(t)
	defer cleanup()
	seedClientCards(t, f, 5)

	resp, err := c.SearchCards(context.Background(), &cardsv1.SearchCardsRequest{PageSize: 10})
	if err != nil {
		t.Fatal(err)
	}
	if len(resp.GetCards()) != 5 {
		t.Errorf("got %d cards, want 5", len(resp.GetCards()))
	}
	if resp.GetNextPageToken() != "" {
		t.Error("expected no next page token")
	}
}

func TestServer_GetCardById(t *testing.T) {
	c, f, cleanup := newClient(t)
	defer cleanup()
	cards := seedClientCards(t, f, 2)

	resp, err := c.GetCardById(context.Background(), &cardsv1.GetCardByIdRequest{CardId: cards[0].ID.String()})
	if err != nil {
		t.Fatal(err)
	}
	if resp.GetCard().GetId() != cards[0].ID.String() {
		t.Errorf("id: got %s want %s", resp.GetCard().GetId(), cards[0].ID.String())
	}

	if _, err := c.GetCardById(context.Background(), &cardsv1.GetCardByIdRequest{CardId: "not-a-uuid"}); status.Code(err) != codes.InvalidArgument {
		t.Errorf("bad uuid: got %v want InvalidArgument", err)
	}

	if _, err := c.GetCardById(context.Background(), &cardsv1.GetCardByIdRequest{CardId: uuid.New().String()}); status.Code(err) != codes.NotFound {
		t.Errorf("missing: got %v want NotFound", err)
	}
}

func TestServer_GetCardsByIds(t *testing.T) {
	c, f, cleanup := newClient(t)
	defer cleanup()
	cards := seedClientCards(t, f, 4)

	resp, err := c.GetCardsByIds(context.Background(), &cardsv1.GetCardsByIdsRequest{
		CardIds: []string{cards[0].ID.String(), cards[2].ID.String()},
	})
	if err != nil {
		t.Fatal(err)
	}
	if len(resp.GetCards()) != 2 {
		t.Errorf("got %d cards, want 2", len(resp.GetCards()))
	}

	if _, err := c.GetCardsByIds(context.Background(), &cardsv1.GetCardsByIdsRequest{CardIds: []string{"bad"}}); status.Code(err) != codes.InvalidArgument {
		t.Errorf("bad id: got %v want InvalidArgument", err)
	}
}

func TestServer_ListSets(t *testing.T) {
	c, f, cleanup := newClient(t)
	defer cleanup()
	seedClientCards(t, f, 3)

	resp, err := c.ListSets(context.Background(), &cardsv1.ListSetsRequest{})
	if err != nil {
		t.Fatal(err)
	}
	if len(resp.GetSets()) != 1 || resp.GetSets()[0].GetId() != "sv03" || resp.GetSets()[0].GetCardCount() != 3 {
		t.Errorf("unexpected sets: %+v", resp.GetSets())
	}
}

func TestServer_SearchCards_InvalidPageToken(t *testing.T) {
	c, f, cleanup := newClient(t)
	defer cleanup()
	seedClientCards(t, f, 1)

	_, err := c.SearchCards(context.Background(), &cardsv1.SearchCardsRequest{PageToken: "not-base64!!"})
	if status.Code(err) != codes.InvalidArgument {
		t.Errorf("got %v want InvalidArgument", err)
	}
}
