package store

import (
	"context"
	"errors"
	"time"

	"github.com/google/uuid"
)

var (
	ErrNotFound     = errors.New("not found")
	ErrEmailTaken   = errors.New("email already taken")
	ErrTokenRevoked = errors.New("refresh token revoked")
	ErrTokenExpired = errors.New("refresh token expired")
)

type User struct {
	ID           uuid.UUID
	Email        string
	PasswordHash string
	Role         string
	ShopName     string
	City         string
	State        string
	CreatedAt    time.Time
	UpdatedAt    time.Time
}

type RefreshToken struct {
	ID        uuid.UUID
	UserID    uuid.UUID
	TokenHash string
	ExpiresAt time.Time
	Revoked   bool
	CreatedAt time.Time
}

type SigningKey struct {
	ID                  uuid.UUID
	PublicKeyPEM        string
	PrivateKeyEncrypted string
	Alg                 string
	CreatedAt           time.Time
	RotatedAt           *time.Time
	RevokedAt           *time.Time
}

// Store is the persistence boundary. Tests inject a fake implementation.
type Store interface {
	CreateUser(ctx context.Context, u User) error
	GetUserByEmail(ctx context.Context, email string) (User, error)
	GetUserByID(ctx context.Context, id uuid.UUID) (User, error)
	UpdateUserProfile(ctx context.Context, id uuid.UUID, shopName, city, state string) (User, error)

	InsertRefreshToken(ctx context.Context, t RefreshToken) error
	GetRefreshTokenByHash(ctx context.Context, hash string) (RefreshToken, error)
	RevokeRefreshToken(ctx context.Context, id uuid.UUID) error
	// ConsumeRefreshToken atomically marks a refresh token revoked and returns
	// the row, but only if it was not already revoked. Returns ErrNotFound if
	// the hash doesn't exist; ErrTokenRevoked if it was already revoked
	// (concurrent rotation race — caller treats as replay).
	ConsumeRefreshToken(ctx context.Context, hash string) (RefreshToken, error)

	GetActiveSigningKey(ctx context.Context) (SigningKey, error)
	ListJWKSKeys(ctx context.Context) ([]SigningKey, error)
	GetSigningKeyByID(ctx context.Context, id uuid.UUID) (SigningKey, error)
	InsertSigningKey(ctx context.Context, k SigningKey) error
	MarkSigningKeyRotated(ctx context.Context, id uuid.UUID, at time.Time) error
	MarkSigningKeyRevoked(ctx context.Context, id uuid.UUID, at time.Time) error
}
