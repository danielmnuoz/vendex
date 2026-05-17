// Package server adapts the card-catalog Service to the gRPC interface
// generated from cards.proto. Translation only — no business logic here.
package server

import (
	"context"
	"errors"
	"log"

	cardsv1 "github.com/danielmnuoz/vendex/proto/gen/go/cards/v1"
	"github.com/danielmnuoz/vendex/services/card-catalog/internal/service"
	"github.com/danielmnuoz/vendex/services/card-catalog/internal/store"
	"github.com/google/uuid"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

type Server struct {
	cardsv1.UnimplementedCardCatalogServiceServer
	svc *service.Service
}

func New(svc *service.Service) *Server {
	return &Server{svc: svc}
}

func (s *Server) SearchCards(ctx context.Context, req *cardsv1.SearchCardsRequest) (*cardsv1.SearchCardsResponse, error) {
	res, err := s.svc.SearchCards(ctx, service.SearchParams{
		Query:        req.GetQuery(),
		SetIDFilter:  req.GetSetIdFilter(),
		RarityFilter: req.GetRarityFilter(),
		PageSize:     int(req.GetPageSize()),
		PageToken:    req.GetPageToken(),
	})
	if err != nil {
		return nil, mapError(err)
	}
	return &cardsv1.SearchCardsResponse{
		Cards:         cardsToProto(res.Cards),
		NextPageToken: res.NextPageToken,
	}, nil
}

func (s *Server) GetCardById(ctx context.Context, req *cardsv1.GetCardByIdRequest) (*cardsv1.GetCardByIdResponse, error) {
	id, err := uuid.Parse(req.GetCardId())
	if err != nil {
		return nil, status.Error(codes.InvalidArgument, "invalid card_id")
	}
	c, err := s.svc.GetCardByID(ctx, id)
	if err != nil {
		return nil, mapError(err)
	}
	return &cardsv1.GetCardByIdResponse{Card: cardToProto(c)}, nil
}

func (s *Server) GetCardsByIds(ctx context.Context, req *cardsv1.GetCardsByIdsRequest) (*cardsv1.GetCardsByIdsResponse, error) {
	ids := make([]uuid.UUID, 0, len(req.GetCardIds()))
	for _, raw := range req.GetCardIds() {
		id, err := uuid.Parse(raw)
		if err != nil {
			return nil, status.Errorf(codes.InvalidArgument, "invalid card_id: %s", raw)
		}
		ids = append(ids, id)
	}
	cards, err := s.svc.GetCardsByIDs(ctx, ids)
	if err != nil {
		return nil, mapError(err)
	}
	return &cardsv1.GetCardsByIdsResponse{Cards: cardsToProto(cards)}, nil
}

func (s *Server) ListSets(ctx context.Context, _ *cardsv1.ListSetsRequest) (*cardsv1.ListSetsResponse, error) {
	sets, err := s.svc.ListSets(ctx)
	if err != nil {
		return nil, mapError(err)
	}
	out := make([]*cardsv1.SetSummary, 0, len(sets))
	for _, s := range sets {
		out = append(out, &cardsv1.SetSummary{
			Id:        s.ID,
			Name:      s.Name,
			Series:    s.Series,
			CardCount: int32(s.CardCount),
		})
	}
	return &cardsv1.ListSetsResponse{Sets: out}, nil
}

func cardToProto(c store.Card) *cardsv1.Card {
	releaseDate := ""
	if c.ReleaseDate != nil {
		releaseDate = c.ReleaseDate.Format("2006-01-02")
	}
	return &cardsv1.Card{
		Id:            c.ID.String(),
		ExternalId:    c.ExternalID,
		Name:          c.Name,
		SetId:         c.SetID,
		SetName:       c.SetName,
		SetSeries:     c.SetSeries,
		Rarity:        c.Rarity,
		ImageUrl:      c.ImageURL,
		ImageUrlLarge: c.ImageURLLarge,
		ReleaseDate:   releaseDate,
	}
}

func cardsToProto(cs []store.Card) []*cardsv1.Card {
	out := make([]*cardsv1.Card, 0, len(cs))
	for _, c := range cs {
		out = append(out, cardToProto(c))
	}
	return out
}

func mapError(err error) error {
	switch {
	case errors.Is(err, store.ErrNotFound):
		return status.Error(codes.NotFound, err.Error())
	case errors.Is(err, service.ErrInvalidPageToken):
		return status.Error(codes.InvalidArgument, err.Error())
	default:
		log.Printf("card-catalog: internal error: %v", err)
		return status.Error(codes.Internal, "internal error")
	}
}
