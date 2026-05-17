package store

import (
	"context"
	"sync"
	"time"

	"github.com/google/uuid"
)

// Fake is an in-memory Store implementation for unit tests.
// Safe for concurrent use within a single test.
type Fake struct {
	mu             sync.Mutex
	users          map[uuid.UUID]User
	usersByEmail   map[string]uuid.UUID
	refreshTokens  map[string]RefreshToken
	refreshByID    map[uuid.UUID]string
	signingKeys    map[uuid.UUID]SigningKey
	signingKeyList []uuid.UUID
}

func NewFake() *Fake {
	return &Fake{
		users:         map[uuid.UUID]User{},
		usersByEmail:  map[string]uuid.UUID{},
		refreshTokens: map[string]RefreshToken{},
		refreshByID:   map[uuid.UUID]string{},
		signingKeys:   map[uuid.UUID]SigningKey{},
	}
}

func (f *Fake) CreateUser(_ context.Context, u User) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	if _, ok := f.usersByEmail[u.Email]; ok {
		return ErrEmailTaken
	}
	f.users[u.ID] = u
	f.usersByEmail[u.Email] = u.ID
	return nil
}

func (f *Fake) GetUserByEmail(_ context.Context, email string) (User, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	id, ok := f.usersByEmail[email]
	if !ok {
		return User{}, ErrNotFound
	}
	return f.users[id], nil
}

func (f *Fake) GetUserByID(_ context.Context, id uuid.UUID) (User, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	u, ok := f.users[id]
	if !ok {
		return User{}, ErrNotFound
	}
	return u, nil
}

func (f *Fake) UpdateUserProfile(_ context.Context, id uuid.UUID, shopName, city, state string) (User, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	u, ok := f.users[id]
	if !ok {
		return User{}, ErrNotFound
	}
	u.ShopName = shopName
	u.City = city
	u.State = state
	u.UpdatedAt = time.Now().UTC()
	f.users[id] = u
	return u, nil
}

func (f *Fake) InsertRefreshToken(_ context.Context, t RefreshToken) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.refreshTokens[t.TokenHash] = t
	f.refreshByID[t.ID] = t.TokenHash
	return nil
}

func (f *Fake) GetRefreshTokenByHash(_ context.Context, hash string) (RefreshToken, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	t, ok := f.refreshTokens[hash]
	if !ok {
		return RefreshToken{}, ErrNotFound
	}
	return t, nil
}

func (f *Fake) RevokeRefreshToken(_ context.Context, id uuid.UUID) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	hash, ok := f.refreshByID[id]
	if !ok {
		return ErrNotFound
	}
	t := f.refreshTokens[hash]
	t.Revoked = true
	f.refreshTokens[hash] = t
	return nil
}

func (f *Fake) GetActiveSigningKey(_ context.Context) (SigningKey, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	for _, id := range f.signingKeyList {
		k := f.signingKeys[id]
		if k.RotatedAt == nil && k.RevokedAt == nil {
			return k, nil
		}
	}
	return SigningKey{}, ErrNotFound
}

func (f *Fake) ListJWKSKeys(_ context.Context) ([]SigningKey, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	out := make([]SigningKey, 0, len(f.signingKeyList))
	for _, id := range f.signingKeyList {
		k := f.signingKeys[id]
		if k.RevokedAt == nil {
			out = append(out, k)
		}
	}
	return out, nil
}

func (f *Fake) GetSigningKeyByID(_ context.Context, id uuid.UUID) (SigningKey, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	k, ok := f.signingKeys[id]
	if !ok {
		return SigningKey{}, ErrNotFound
	}
	return k, nil
}

func (f *Fake) InsertSigningKey(_ context.Context, k SigningKey) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.signingKeys[k.ID] = k
	f.signingKeyList = append(f.signingKeyList, k.ID)
	return nil
}

func (f *Fake) MarkSigningKeyRotated(_ context.Context, id uuid.UUID, at time.Time) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	k, ok := f.signingKeys[id]
	if !ok {
		return ErrNotFound
	}
	k.RotatedAt = &at
	f.signingKeys[id] = k
	return nil
}

// RefreshTokenHashes returns all stored refresh-token hashes. Test-only helper.
func (f *Fake) RefreshTokenHashes() []string {
	f.mu.Lock()
	defer f.mu.Unlock()
	out := make([]string, 0, len(f.refreshTokens))
	for h := range f.refreshTokens {
		out = append(out, h)
	}
	return out
}

// MustGetRefreshToken returns the stored RefreshToken for a given hash, or panics
// if it doesn't exist. Test-only helper.
func (f *Fake) MustGetRefreshToken(hash string) RefreshToken {
	f.mu.Lock()
	defer f.mu.Unlock()
	t, ok := f.refreshTokens[hash]
	if !ok {
		panic("refresh token not found: " + hash)
	}
	return t
}

// MustReplaceRefreshToken overwrites the stored RefreshToken with the given one,
// keyed by token_hash. Test-only helper for simulating clock/expiry edge cases.
func (f *Fake) MustReplaceRefreshToken(t RefreshToken) {
	f.mu.Lock()
	defer f.mu.Unlock()
	if _, ok := f.refreshTokens[t.TokenHash]; !ok {
		panic("refresh token not found: " + t.TokenHash)
	}
	f.refreshTokens[t.TokenHash] = t
}

func (f *Fake) MarkSigningKeyRevoked(_ context.Context, id uuid.UUID, at time.Time) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	k, ok := f.signingKeys[id]
	if !ok {
		return ErrNotFound
	}
	k.RevokedAt = &at
	f.signingKeys[id] = k
	return nil
}
