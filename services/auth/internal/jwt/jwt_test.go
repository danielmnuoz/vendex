package jwt

import (
	"context"
	"crypto/rand"
	"encoding/base64"
	"testing"
	"time"

	"github.com/danielmnuoz/vendex/services/auth/internal/crypto"
	"github.com/danielmnuoz/vendex/services/auth/internal/store"
	"github.com/google/uuid"
)

func newAEAD(t *testing.T) *crypto.AEAD {
	t.Helper()
	b := make([]byte, 32)
	_, _ = rand.Read(b)
	a, err := crypto.NewAEAD(base64.StdEncoding.EncodeToString(b))
	if err != nil {
		t.Fatal(err)
	}
	return a
}

func TestSignVerify_RoundTrip(t *testing.T) {
	ctx := context.Background()
	s := store.NewFake()
	a := newAEAD(t)
	if _, err := GenerateAndStoreKey(ctx, s, a); err != nil {
		t.Fatal(err)
	}
	signer := NewSigner(s, a)
	userID := uuid.New()
	tok, exp, err := signer.Sign(ctx, userID, "vendor", time.Hour)
	if err != nil {
		t.Fatalf("sign: %v", err)
	}
	if exp.Before(time.Now()) {
		t.Errorf("expected exp in future, got %v", exp)
	}
	claims, err := signer.Verify(ctx, tok)
	if err != nil {
		t.Fatalf("verify: %v", err)
	}
	if claims.UserID != userID {
		t.Errorf("user_id: got %v want %v", claims.UserID, userID)
	}
	if claims.Role != "vendor" {
		t.Errorf("role: got %q want vendor", claims.Role)
	}
}

func TestVerify_RejectsRevokedKey(t *testing.T) {
	ctx := context.Background()
	s := store.NewFake()
	a := newAEAD(t)
	k, _ := GenerateAndStoreKey(ctx, s, a)
	signer := NewSigner(s, a)
	tok, _, _ := signer.Sign(ctx, uuid.New(), "vendor", time.Hour)

	// Revoke the key after the token was signed.
	_ = s.MarkSigningKeyRevoked(ctx, k.ID, time.Now())

	if _, err := signer.Verify(ctx, tok); err == nil {
		t.Error("expected verify to reject token signed by revoked key")
	}
}

func TestVerify_RejectsExpiredToken(t *testing.T) {
	ctx := context.Background()
	s := store.NewFake()
	a := newAEAD(t)
	_, _ = GenerateAndStoreKey(ctx, s, a)
	signer := NewSigner(s, a)
	tok, _, _ := signer.Sign(ctx, uuid.New(), "vendor", -time.Second) // already expired
	if _, err := signer.Verify(ctx, tok); err == nil {
		t.Error("expected verify to reject expired token")
	}
}

func TestVerify_RejectsGarbage(t *testing.T) {
	ctx := context.Background()
	s := store.NewFake()
	a := newAEAD(t)
	_, _ = GenerateAndStoreKey(ctx, s, a)
	signer := NewSigner(s, a)
	if _, err := signer.Verify(ctx, "not.a.jwt"); err == nil {
		t.Error("expected verify to reject garbage")
	}
}

func TestGenerateAndStoreKey_RotatesPrevious(t *testing.T) {
	ctx := context.Background()
	s := store.NewFake()
	a := newAEAD(t)
	first, err := GenerateAndStoreKey(ctx, s, a)
	if err != nil {
		t.Fatal(err)
	}
	second, err := GenerateAndStoreKey(ctx, s, a)
	if err != nil {
		t.Fatal(err)
	}
	if first.ID == second.ID {
		t.Fatal("expected new key id")
	}
	active, err := s.GetActiveSigningKey(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if active.ID != second.ID {
		t.Errorf("active key: got %v want %v", active.ID, second.ID)
	}
	rotated, err := s.GetSigningKeyByID(ctx, first.ID)
	if err != nil {
		t.Fatal(err)
	}
	if rotated.RotatedAt == nil {
		t.Error("expected previous key to be marked rotated")
	}
}

func TestVerify_AcceptsRotatedButNotRevokedKey(t *testing.T) {
	// A token signed by a now-rotated (but not revoked) key must still verify
	// during the 7-day overlap window. This is the entire point of JWKS rotation.
	ctx := context.Background()
	s := store.NewFake()
	a := newAEAD(t)
	_, _ = GenerateAndStoreKey(ctx, s, a)
	signer := NewSigner(s, a)
	tok, _, _ := signer.Sign(ctx, uuid.New(), "vendor", time.Hour)

	// Rotate by inserting a new key.
	_, _ = GenerateAndStoreKey(ctx, s, a)

	if _, err := signer.Verify(ctx, tok); err != nil {
		t.Errorf("expected token signed by rotated-but-not-revoked key to verify: %v", err)
	}
}
