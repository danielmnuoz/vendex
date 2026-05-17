package service

import (
	"context"
	"crypto/rand"
	"encoding/base64"
	"errors"
	"testing"
	"time"

	"github.com/danielmnuoz/vendex/services/auth/internal/crypto"
	"github.com/danielmnuoz/vendex/services/auth/internal/jwt"
	"github.com/danielmnuoz/vendex/services/auth/internal/store"
	"github.com/google/uuid"
)

func newSvc(t *testing.T) (*Service, *store.Fake) {
	t.Helper()
	ctx := context.Background()
	s := store.NewFake()
	b := make([]byte, 32)
	_, _ = rand.Read(b)
	a, err := crypto.NewAEAD(base64.StdEncoding.EncodeToString(b))
	if err != nil {
		t.Fatal(err)
	}
	if _, err := jwt.GenerateAndStoreKey(ctx, s, a); err != nil {
		t.Fatal(err)
	}
	signer := jwt.NewSigner(s, a)
	svc := New(s, signer, Config{
		BcryptCost:      4, // minimum for fast tests
		AccessTokenTTL:  time.Hour,
		RefreshTokenTTL: 24 * time.Hour,
	})
	return svc, s
}

func TestRegister(t *testing.T) {
	cases := []struct {
		name    string
		params  RegisterParams
		wantErr error
	}{
		{
			name:   "ok_vendor",
			params: RegisterParams{Email: "a@b.com", Password: "password1", Role: "vendor"},
		},
		{
			name:   "ok_attendee",
			params: RegisterParams{Email: "x@y.com", Password: "password1", Role: "attendee"},
		},
		{
			name:    "bad_email",
			params:  RegisterParams{Email: "not-an-email", Password: "password1", Role: "vendor"},
			wantErr: ErrInvalidEmail,
		},
		{
			name:    "weak_password",
			params:  RegisterParams{Email: "a@b.com", Password: "short", Role: "vendor"},
			wantErr: ErrWeakPassword,
		},
		{
			name:    "bad_role",
			params:  RegisterParams{Email: "a@b.com", Password: "password1", Role: "admin"},
			wantErr: ErrInvalidRole,
		},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			svc, _ := newSvc(t)
			_, err := svc.Register(context.Background(), tc.params)
			if !errors.Is(err, tc.wantErr) {
				t.Errorf("err: got %v want %v", err, tc.wantErr)
			}
		})
	}
}

func TestRegister_DuplicateEmail(t *testing.T) {
	svc, _ := newSvc(t)
	ctx := context.Background()
	params := RegisterParams{Email: "a@b.com", Password: "password1", Role: "vendor"}
	if _, err := svc.Register(ctx, params); err != nil {
		t.Fatal(err)
	}
	_, err := svc.Register(ctx, params)
	if !errors.Is(err, store.ErrEmailTaken) {
		t.Errorf("err: got %v want ErrEmailTaken", err)
	}
}

func TestRegister_NormalizesEmail(t *testing.T) {
	svc, fake := newSvc(t)
	ctx := context.Background()
	_, err := svc.Register(ctx, RegisterParams{Email: "  Foo@BAR.com  ", Password: "password1", Role: "vendor"})
	if err != nil {
		t.Fatal(err)
	}
	if _, err := fake.GetUserByEmail(ctx, "foo@bar.com"); err != nil {
		t.Errorf("expected stored email to be normalized: %v", err)
	}
}

func TestLogin(t *testing.T) {
	svc, _ := newSvc(t)
	ctx := context.Background()
	_, err := svc.Register(ctx, RegisterParams{Email: "a@b.com", Password: "password1", Role: "vendor"})
	if err != nil {
		t.Fatal(err)
	}

	cases := []struct {
		name     string
		email    string
		password string
		wantErr  error
	}{
		{"ok", "a@b.com", "password1", nil},
		{"case_insensitive_email", "A@B.com", "password1", nil},
		{"wrong_password", "a@b.com", "wrong", ErrInvalidCredentials},
		{"unknown_email", "no@one.com", "password1", ErrInvalidCredentials},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			tp, err := svc.Login(ctx, tc.email, tc.password)
			if !errors.Is(err, tc.wantErr) {
				t.Errorf("err: got %v want %v", err, tc.wantErr)
			}
			if err == nil && (tp.AccessToken == "" || tp.RefreshToken == "") {
				t.Error("expected non-empty tokens")
			}
		})
	}
}

func TestRefreshToken(t *testing.T) {
	svc, _ := newSvc(t)
	ctx := context.Background()
	_, _ = svc.Register(ctx, RegisterParams{Email: "a@b.com", Password: "password1", Role: "vendor"})
	tp, _ := svc.Login(ctx, "a@b.com", "password1")

	t.Run("rotates_token", func(t *testing.T) {
		newTP, err := svc.RefreshToken(ctx, tp.RefreshToken)
		if err != nil {
			t.Fatal(err)
		}
		if newTP.RefreshToken == tp.RefreshToken {
			t.Error("expected refresh token to rotate")
		}
		if newTP.AccessToken == "" {
			t.Error("expected new access token")
		}
		// Old refresh token must now be rejected.
		if _, err := svc.RefreshToken(ctx, tp.RefreshToken); !errors.Is(err, store.ErrTokenRevoked) {
			t.Errorf("expected old refresh token to be revoked, got %v", err)
		}
	})

	t.Run("unknown_token", func(t *testing.T) {
		if _, err := svc.RefreshToken(ctx, "deadbeef"); !errors.Is(err, ErrUnauthenticated) {
			t.Errorf("err: got %v want ErrUnauthenticated", err)
		}
	})
}

func TestRefreshToken_Expired(t *testing.T) {
	ctx := context.Background()
	svc, fake := newSvc(t)
	_, _ = svc.Register(ctx, RegisterParams{Email: "a@b.com", Password: "password1", Role: "vendor"})
	tp, _ := svc.Login(ctx, "a@b.com", "password1")

	// Find and rewrite expires_at on the stored token. Walk fake.refreshTokens.
	for _, hashed := range fake.RefreshTokenHashes() {
		t := fake.MustGetRefreshToken(hashed)
		t.ExpiresAt = time.Now().Add(-time.Hour)
		fake.MustReplaceRefreshToken(t)
	}

	if _, err := svc.RefreshToken(ctx, tp.RefreshToken); !errors.Is(err, store.ErrTokenExpired) {
		t.Errorf("err: got %v want ErrTokenExpired", err)
	}
}

func TestValidateToken(t *testing.T) {
	svc, _ := newSvc(t)
	ctx := context.Background()
	_, _ = svc.Register(ctx, RegisterParams{Email: "a@b.com", Password: "password1", Role: "vendor"})
	tp, _ := svc.Login(ctx, "a@b.com", "password1")

	userID, role, _, err := svc.ValidateToken(ctx, tp.AccessToken)
	if err != nil {
		t.Fatalf("validate: %v", err)
	}
	if role != "vendor" {
		t.Errorf("role: got %q want vendor", role)
	}
	if userID == uuid.Nil {
		t.Error("expected non-nil user id")
	}

	if _, _, _, err := svc.ValidateToken(ctx, "garbage"); !errors.Is(err, ErrUnauthenticated) {
		t.Errorf("err: got %v want ErrUnauthenticated", err)
	}
}

func TestGetJWKS(t *testing.T) {
	svc, _ := newSvc(t)
	keys, err := svc.GetJWKS(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	if len(keys) != 1 {
		t.Errorf("expected 1 key, got %d", len(keys))
	}
}

func TestUpdateProfile(t *testing.T) {
	svc, _ := newSvc(t)
	ctx := context.Background()
	id, _ := svc.Register(ctx, RegisterParams{Email: "a@b.com", Password: "password1", Role: "vendor", ShopName: "Old"})
	u, err := svc.UpdateProfile(ctx, id, "New Shop", "Dallas", "TX")
	if err != nil {
		t.Fatal(err)
	}
	if u.ShopName != "New Shop" || u.City != "Dallas" || u.State != "TX" {
		t.Errorf("profile not updated: %+v", u)
	}
}

func TestGetVendorProfile_RejectsNonVendor(t *testing.T) {
	svc, _ := newSvc(t)
	ctx := context.Background()
	id, _ := svc.Register(ctx, RegisterParams{Email: "a@b.com", Password: "password1", Role: "attendee"})
	if _, err := svc.GetVendorProfile(ctx, id); err == nil {
		t.Error("expected error fetching vendor profile of attendee")
	}
}
