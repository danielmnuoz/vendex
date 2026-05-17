// Package server adapts the auth Service to the gRPC interface generated
// from auth.proto. Translation only — no business logic lives here.
package server

import (
	"context"
	"errors"
	"strings"

	authv1 "github.com/danielmnuoz/vendex/proto/gen/go/auth/v1"
	"github.com/danielmnuoz/vendex/services/auth/internal/service"
	"github.com/danielmnuoz/vendex/services/auth/internal/store"
	"github.com/google/uuid"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
)

type Server struct {
	authv1.UnimplementedAuthServiceServer
	svc *service.Service
}

func New(svc *service.Service) *Server {
	return &Server{svc: svc}
}

func (s *Server) Register(ctx context.Context, req *authv1.RegisterRequest) (*authv1.RegisterResponse, error) {
	id, err := s.svc.Register(ctx, service.RegisterParams{
		Email:    req.GetEmail(),
		Password: req.GetPassword(),
		Role:     roleProtoToString(req.GetRole()),
		ShopName: req.GetShopName(),
		City:     req.GetCity(),
		State:    req.GetState(),
	})
	if err != nil {
		return nil, mapError(err)
	}
	return &authv1.RegisterResponse{UserId: id.String()}, nil
}

func (s *Server) Login(ctx context.Context, req *authv1.LoginRequest) (*authv1.LoginResponse, error) {
	tp, err := s.svc.Login(ctx, req.GetEmail(), req.GetPassword())
	if err != nil {
		return nil, mapError(err)
	}
	return &authv1.LoginResponse{
		AccessToken:           tp.AccessToken,
		RefreshToken:          tp.RefreshToken,
		AccessTokenExpiresAt:  tp.AccessTokenExpiresAt.Unix(),
		RefreshTokenExpiresAt: tp.RefreshTokenExpiresAt.Unix(),
	}, nil
}

func (s *Server) RefreshToken(ctx context.Context, req *authv1.RefreshTokenRequest) (*authv1.RefreshTokenResponse, error) {
	tp, err := s.svc.RefreshToken(ctx, req.GetRefreshToken())
	if err != nil {
		return nil, mapError(err)
	}
	return &authv1.RefreshTokenResponse{
		AccessToken:           tp.AccessToken,
		RefreshToken:          tp.RefreshToken,
		AccessTokenExpiresAt:  tp.AccessTokenExpiresAt.Unix(),
		RefreshTokenExpiresAt: tp.RefreshTokenExpiresAt.Unix(),
	}, nil
}

func (s *Server) ValidateToken(ctx context.Context, req *authv1.ValidateTokenRequest) (*authv1.ValidateTokenResponse, error) {
	userID, role, exp, err := s.svc.ValidateToken(ctx, req.GetAccessToken())
	if err != nil {
		return nil, mapError(err)
	}
	return &authv1.ValidateTokenResponse{
		UserId:    userID.String(),
		Role:      roleStringToProto(role),
		ExpiresAt: exp.Unix(),
	}, nil
}

func (s *Server) GetJWKS(ctx context.Context, _ *authv1.GetJWKSRequest) (*authv1.GetJWKSResponse, error) {
	keys, err := s.svc.GetJWKS(ctx)
	if err != nil {
		return nil, mapError(err)
	}
	out := make([]*authv1.JWK, 0, len(keys))
	for _, k := range keys {
		out = append(out, &authv1.JWK{
			Kid:          k.Kid,
			Alg:          k.Alg,
			PublicKeyPem: k.PublicKeyPEM,
		})
	}
	return &authv1.GetJWKSResponse{Keys: out}, nil
}

func (s *Server) GetVendorProfile(ctx context.Context, req *authv1.GetVendorProfileRequest) (*authv1.GetVendorProfileResponse, error) {
	id, err := uuid.Parse(req.GetVendorId())
	if err != nil {
		return nil, status.Error(codes.InvalidArgument, "invalid vendor_id")
	}
	u, err := s.svc.GetVendorProfile(ctx, id)
	if err != nil {
		return nil, mapError(err)
	}
	return &authv1.GetVendorProfileResponse{Profile: userToProfile(u)}, nil
}

func (s *Server) UpdateProfile(ctx context.Context, req *authv1.UpdateProfileRequest) (*authv1.UpdateProfileResponse, error) {
	userID, err := userIDFromAccessToken(ctx, s.svc)
	if err != nil {
		return nil, err
	}
	u, err := s.svc.UpdateProfile(ctx, userID, req.GetShopName(), req.GetCity(), req.GetState())
	if err != nil {
		return nil, mapError(err)
	}
	return &authv1.UpdateProfileResponse{Profile: userToProfile(u)}, nil
}

// userIDFromAccessToken pulls the bearer token from gRPC metadata and
// validates it. UpdateProfile uses this to enforce "you can only update
// your own profile" without trusting client-supplied IDs.
func userIDFromAccessToken(ctx context.Context, svc *service.Service) (uuid.UUID, error) {
	md, ok := metadata.FromIncomingContext(ctx)
	if !ok {
		return uuid.Nil, status.Error(codes.Unauthenticated, "missing metadata")
	}
	auths := md.Get("authorization")
	if len(auths) == 0 {
		return uuid.Nil, status.Error(codes.Unauthenticated, "missing authorization header")
	}
	tok := strings.TrimPrefix(auths[0], "Bearer ")
	tok = strings.TrimPrefix(tok, "bearer ")
	if tok == "" {
		return uuid.Nil, status.Error(codes.Unauthenticated, "empty bearer token")
	}
	userID, _, _, err := svc.ValidateToken(ctx, tok)
	if err != nil {
		return uuid.Nil, status.Error(codes.Unauthenticated, "invalid access token")
	}
	return userID, nil
}

func userToProfile(u store.User) *authv1.VendorProfile {
	return &authv1.VendorProfile{
		UserId:   u.ID.String(),
		Email:    u.Email,
		ShopName: u.ShopName,
		City:     u.City,
		State:    u.State,
	}
}

func roleProtoToString(r authv1.Role) string {
	switch r {
	case authv1.Role_ROLE_VENDOR:
		return "vendor"
	case authv1.Role_ROLE_ATTENDEE:
		return "attendee"
	case authv1.Role_ROLE_ORGANIZER:
		return "organizer"
	default:
		return ""
	}
}

func roleStringToProto(r string) authv1.Role {
	switch r {
	case "vendor":
		return authv1.Role_ROLE_VENDOR
	case "attendee":
		return authv1.Role_ROLE_ATTENDEE
	case "organizer":
		return authv1.Role_ROLE_ORGANIZER
	default:
		return authv1.Role_ROLE_UNSPECIFIED
	}
}

func mapError(err error) error {
	switch {
	case errors.Is(err, service.ErrInvalidCredentials), errors.Is(err, service.ErrUnauthenticated):
		return status.Error(codes.Unauthenticated, err.Error())
	case errors.Is(err, service.ErrInvalidEmail), errors.Is(err, service.ErrWeakPassword), errors.Is(err, service.ErrInvalidRole):
		return status.Error(codes.InvalidArgument, err.Error())
	case errors.Is(err, store.ErrEmailTaken):
		return status.Error(codes.AlreadyExists, err.Error())
	case errors.Is(err, store.ErrNotFound):
		return status.Error(codes.NotFound, err.Error())
	case errors.Is(err, store.ErrTokenRevoked), errors.Is(err, store.ErrTokenExpired):
		return status.Error(codes.Unauthenticated, err.Error())
	default:
		return status.Error(codes.Internal, err.Error())
	}
}
