// Package service contains the auth business logic, decoupled from gRPC.
// The gRPC handler in services/auth/internal/server translates protobuf
// messages to these calls.
package service

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/danielmnuoz/vendex/services/auth/internal/jwt"
	"github.com/danielmnuoz/vendex/services/auth/internal/store"
	"github.com/google/uuid"
	"golang.org/x/crypto/bcrypt"
)

var (
	ErrInvalidCredentials = errors.New("invalid credentials")
	ErrInvalidRole        = errors.New("invalid role")
	ErrInvalidEmail       = errors.New("invalid email")
	ErrWeakPassword       = errors.New("password must be at least 8 characters")
	ErrUnauthenticated    = errors.New("unauthenticated")
)

// Service is the auth business logic. It is the unit under test for the
// service-layer unit tests; gRPC translation lives in the server package.
type Service struct {
	store           store.Store
	signer          *jwt.Signer
	now             func() time.Time
	bcryptCost      int
	accessTokenTTL  time.Duration
	refreshTokenTTL time.Duration
}

type Config struct {
	BcryptCost      int
	AccessTokenTTL  time.Duration
	RefreshTokenTTL time.Duration
	Now             func() time.Time
}

func New(s store.Store, signer *jwt.Signer, cfg Config) *Service {
	if cfg.Now == nil {
		cfg.Now = func() time.Time { return time.Now().UTC() }
	}
	if cfg.BcryptCost == 0 {
		cfg.BcryptCost = bcrypt.DefaultCost
	}
	if cfg.AccessTokenTTL == 0 {
		cfg.AccessTokenTTL = 15 * time.Minute
	}
	if cfg.RefreshTokenTTL == 0 {
		cfg.RefreshTokenTTL = 7 * 24 * time.Hour
	}
	return &Service{
		store:           s,
		signer:          signer,
		now:             cfg.Now,
		bcryptCost:      cfg.BcryptCost,
		accessTokenTTL:  cfg.AccessTokenTTL,
		refreshTokenTTL: cfg.RefreshTokenTTL,
	}
}

type RegisterParams struct {
	Email    string
	Password string
	Role     string
	ShopName string
	City     string
	State    string
}

func (s *Service) Register(ctx context.Context, p RegisterParams) (uuid.UUID, error) {
	email := normalizeEmail(p.Email)
	if !looksLikeEmail(email) {
		return uuid.Nil, ErrInvalidEmail
	}
	if len(p.Password) < 8 {
		return uuid.Nil, ErrWeakPassword
	}
	if !isValidRole(p.Role) {
		return uuid.Nil, ErrInvalidRole
	}
	hash, err := bcrypt.GenerateFromPassword([]byte(p.Password), s.bcryptCost)
	if err != nil {
		return uuid.Nil, fmt.Errorf("hash password: %w", err)
	}
	now := s.now()
	u := store.User{
		ID:           uuid.New(),
		Email:        email,
		PasswordHash: string(hash),
		Role:         p.Role,
		ShopName:     p.ShopName,
		City:         p.City,
		State:        p.State,
		CreatedAt:    now,
		UpdatedAt:    now,
	}
	if err := s.store.CreateUser(ctx, u); err != nil {
		return uuid.Nil, err
	}
	return u.ID, nil
}

type TokenPair struct {
	AccessToken           string
	RefreshToken          string
	AccessTokenExpiresAt  time.Time
	RefreshTokenExpiresAt time.Time
}

func (s *Service) Login(ctx context.Context, email, password string) (TokenPair, error) {
	u, err := s.store.GetUserByEmail(ctx, normalizeEmail(email))
	if errors.Is(err, store.ErrNotFound) {
		// Run bcrypt anyway to keep timing roughly constant — protects against
		// user-enumeration via response timing.
		_ = bcrypt.CompareHashAndPassword([]byte("$2a$10$invalidinvalidinvalidinvalidinvalidinvalidinvalidinvali"), []byte(password))
		return TokenPair{}, ErrInvalidCredentials
	}
	if err != nil {
		return TokenPair{}, err
	}
	if err := bcrypt.CompareHashAndPassword([]byte(u.PasswordHash), []byte(password)); err != nil {
		return TokenPair{}, ErrInvalidCredentials
	}
	return s.issueTokens(ctx, u.ID, u.Role)
}

func (s *Service) RefreshToken(ctx context.Context, refreshToken string) (TokenPair, error) {
	hash := hashRefreshToken(refreshToken)
	t, err := s.store.GetRefreshTokenByHash(ctx, hash)
	if errors.Is(err, store.ErrNotFound) {
		return TokenPair{}, ErrUnauthenticated
	}
	if err != nil {
		return TokenPair{}, err
	}
	if t.Revoked {
		return TokenPair{}, store.ErrTokenRevoked
	}
	if s.now().After(t.ExpiresAt) {
		return TokenPair{}, store.ErrTokenExpired
	}
	// Rotate: revoke the used refresh token, issue a fresh pair.
	if err := s.store.RevokeRefreshToken(ctx, t.ID); err != nil {
		return TokenPair{}, err
	}
	u, err := s.store.GetUserByID(ctx, t.UserID)
	if err != nil {
		return TokenPair{}, err
	}
	return s.issueTokens(ctx, u.ID, u.Role)
}

func (s *Service) ValidateToken(ctx context.Context, accessToken string) (uuid.UUID, string, time.Time, error) {
	claims, err := s.signer.Verify(ctx, accessToken)
	if err != nil {
		return uuid.Nil, "", time.Time{}, ErrUnauthenticated
	}
	exp := time.Time{}
	if claims.ExpiresAt != nil {
		exp = claims.ExpiresAt.Time
	}
	return claims.UserID, claims.Role, exp, nil
}

type JWK struct {
	Kid          string
	Alg          string
	PublicKeyPEM string
}

func (s *Service) GetJWKS(ctx context.Context) ([]JWK, error) {
	keys, err := s.store.ListJWKSKeys(ctx)
	if err != nil {
		return nil, err
	}
	out := make([]JWK, 0, len(keys))
	for _, k := range keys {
		out = append(out, JWK{
			Kid:          k.ID.String(),
			Alg:          k.Alg,
			PublicKeyPEM: k.PublicKeyPEM,
		})
	}
	return out, nil
}

func (s *Service) GetVendorProfile(ctx context.Context, vendorID uuid.UUID) (store.User, error) {
	u, err := s.store.GetUserByID(ctx, vendorID)
	if err != nil {
		return store.User{}, err
	}
	if u.Role != "vendor" {
		return store.User{}, fmt.Errorf("user %s is not a vendor", vendorID)
	}
	return u, nil
}

func (s *Service) UpdateProfile(ctx context.Context, userID uuid.UUID, shopName, city, state string) (store.User, error) {
	return s.store.UpdateUserProfile(ctx, userID, shopName, city, state)
}

func (s *Service) issueTokens(ctx context.Context, userID uuid.UUID, role string) (TokenPair, error) {
	access, accessExp, err := s.signer.Sign(ctx, userID, role, s.accessTokenTTL)
	if err != nil {
		return TokenPair{}, err
	}
	refresh, err := generateRefreshToken()
	if err != nil {
		return TokenPair{}, err
	}
	refreshExp := s.now().Add(s.refreshTokenTTL)
	rt := store.RefreshToken{
		ID:        uuid.New(),
		UserID:    userID,
		TokenHash: hashRefreshToken(refresh),
		ExpiresAt: refreshExp,
		CreatedAt: s.now(),
	}
	if err := s.store.InsertRefreshToken(ctx, rt); err != nil {
		return TokenPair{}, err
	}
	return TokenPair{
		AccessToken:           access,
		RefreshToken:          refresh,
		AccessTokenExpiresAt:  accessExp,
		RefreshTokenExpiresAt: refreshExp,
	}, nil
}

func generateRefreshToken() (string, error) {
	b := make([]byte, 32)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return hex.EncodeToString(b), nil
}

func hashRefreshToken(t string) string {
	sum := sha256.Sum256([]byte(t))
	return hex.EncodeToString(sum[:])
}

func normalizeEmail(s string) string {
	return strings.ToLower(strings.TrimSpace(s))
}

func looksLikeEmail(s string) bool {
	at := strings.IndexByte(s, '@')
	if at <= 0 || at == len(s)-1 {
		return false
	}
	dot := strings.LastIndexByte(s, '.')
	return dot > at && dot < len(s)-1
}

func isValidRole(r string) bool {
	switch r {
	case "vendor", "attendee", "organizer":
		return true
	default:
		return false
	}
}
