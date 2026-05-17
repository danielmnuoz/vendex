package server

import (
	"context"
	"crypto/rand"
	"encoding/base64"
	"net"
	"testing"
	"time"

	authv1 "github.com/danielmnuoz/vendex/proto/gen/go/auth/v1"
	"github.com/danielmnuoz/vendex/services/auth/internal/crypto"
	"github.com/danielmnuoz/vendex/services/auth/internal/jwt"
	"github.com/danielmnuoz/vendex/services/auth/internal/service"
	"github.com/danielmnuoz/vendex/services/auth/internal/store"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
	"google.golang.org/grpc/test/bufconn"
)

const bufSize = 1024 * 1024

func newClient(t *testing.T) (authv1.AuthServiceClient, func()) {
	t.Helper()
	ctx := context.Background()
	s := store.NewFake()
	b := make([]byte, 32)
	_, _ = rand.Read(b)
	a, _ := crypto.NewAEAD(base64.StdEncoding.EncodeToString(b))
	_, _ = jwt.GenerateAndStoreKey(ctx, s, a)
	signer := jwt.NewSigner(s, a)
	svc := service.New(s, signer, service.Config{
		BcryptCost:      4,
		AccessTokenTTL:  time.Hour,
		RefreshTokenTTL: 24 * time.Hour,
	})

	lis := bufconn.Listen(bufSize)
	srv := grpc.NewServer()
	authv1.RegisterAuthServiceServer(srv, New(svc))
	go func() {
		_ = srv.Serve(lis)
	}()

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
	return authv1.NewAuthServiceClient(conn), cleanup
}

func TestServer_RegisterAndLogin(t *testing.T) {
	c, cleanup := newClient(t)
	defer cleanup()
	ctx := context.Background()

	reg, err := c.Register(ctx, &authv1.RegisterRequest{
		Email:    "vendor@x.com",
		Password: "password1",
		Role:     authv1.Role_ROLE_VENDOR,
	})
	if err != nil {
		t.Fatalf("register: %v", err)
	}
	if reg.GetUserId() == "" {
		t.Error("expected user_id")
	}

	login, err := c.Login(ctx, &authv1.LoginRequest{Email: "vendor@x.com", Password: "password1"})
	if err != nil {
		t.Fatalf("login: %v", err)
	}
	if login.GetAccessToken() == "" || login.GetRefreshToken() == "" {
		t.Error("expected tokens")
	}
}

func TestServer_RegisterValidation(t *testing.T) {
	c, cleanup := newClient(t)
	defer cleanup()

	cases := []struct {
		name string
		req  *authv1.RegisterRequest
		code codes.Code
	}{
		{"bad_email", &authv1.RegisterRequest{Email: "x", Password: "password1", Role: authv1.Role_ROLE_VENDOR}, codes.InvalidArgument},
		{"weak_password", &authv1.RegisterRequest{Email: "a@b.com", Password: "x", Role: authv1.Role_ROLE_VENDOR}, codes.InvalidArgument},
		{"unspecified_role", &authv1.RegisterRequest{Email: "a@b.com", Password: "password1", Role: authv1.Role_ROLE_UNSPECIFIED}, codes.InvalidArgument},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			_, err := c.Register(context.Background(), tc.req)
			if status.Code(err) != tc.code {
				t.Errorf("code: got %v want %v (err=%v)", status.Code(err), tc.code, err)
			}
		})
	}
}

func TestServer_ValidateToken(t *testing.T) {
	c, cleanup := newClient(t)
	defer cleanup()
	ctx := context.Background()
	_, _ = c.Register(ctx, &authv1.RegisterRequest{Email: "a@b.com", Password: "password1", Role: authv1.Role_ROLE_VENDOR})
	login, _ := c.Login(ctx, &authv1.LoginRequest{Email: "a@b.com", Password: "password1"})

	resp, err := c.ValidateToken(ctx, &authv1.ValidateTokenRequest{AccessToken: login.GetAccessToken()})
	if err != nil {
		t.Fatalf("validate: %v", err)
	}
	if resp.GetRole() != authv1.Role_ROLE_VENDOR {
		t.Errorf("role: got %v want vendor", resp.GetRole())
	}

	if _, err := c.ValidateToken(ctx, &authv1.ValidateTokenRequest{AccessToken: "garbage"}); status.Code(err) != codes.Unauthenticated {
		t.Errorf("garbage token should be unauthenticated, got %v", err)
	}
}

func TestServer_GetJWKS(t *testing.T) {
	c, cleanup := newClient(t)
	defer cleanup()
	resp, err := c.GetJWKS(context.Background(), &authv1.GetJWKSRequest{})
	if err != nil {
		t.Fatal(err)
	}
	if len(resp.GetKeys()) != 1 {
		t.Errorf("expected 1 key, got %d", len(resp.GetKeys()))
	}
	if resp.GetKeys()[0].GetAlg() != "RS256" {
		t.Errorf("alg: got %s want RS256", resp.GetKeys()[0].GetAlg())
	}
}

func TestServer_UpdateProfile_RequiresAuth(t *testing.T) {
	c, cleanup := newClient(t)
	defer cleanup()
	if _, err := c.UpdateProfile(context.Background(), &authv1.UpdateProfileRequest{ShopName: "x"}); status.Code(err) != codes.Unauthenticated {
		t.Errorf("expected Unauthenticated without token, got %v", err)
	}
}

func TestServer_UpdateProfile_WithBearer(t *testing.T) {
	c, cleanup := newClient(t)
	defer cleanup()
	ctx := context.Background()
	_, _ = c.Register(ctx, &authv1.RegisterRequest{Email: "a@b.com", Password: "password1", Role: authv1.Role_ROLE_VENDOR, ShopName: "Old"})
	login, _ := c.Login(ctx, &authv1.LoginRequest{Email: "a@b.com", Password: "password1"})

	authCtx := metadata.AppendToOutgoingContext(ctx, "authorization", "Bearer "+login.GetAccessToken())
	resp, err := c.UpdateProfile(authCtx, &authv1.UpdateProfileRequest{ShopName: "New", City: "Dallas", State: "TX"})
	if err != nil {
		t.Fatalf("update: %v", err)
	}
	if resp.GetProfile().GetShopName() != "New" {
		t.Errorf("shop_name: got %q want New", resp.GetProfile().GetShopName())
	}
}

func TestServer_RefreshToken_Rotation(t *testing.T) {
	c, cleanup := newClient(t)
	defer cleanup()
	ctx := context.Background()
	_, _ = c.Register(ctx, &authv1.RegisterRequest{Email: "a@b.com", Password: "password1", Role: authv1.Role_ROLE_VENDOR})
	login, _ := c.Login(ctx, &authv1.LoginRequest{Email: "a@b.com", Password: "password1"})

	first, err := c.RefreshToken(ctx, &authv1.RefreshTokenRequest{RefreshToken: login.GetRefreshToken()})
	if err != nil {
		t.Fatal(err)
	}
	if first.GetRefreshToken() == login.GetRefreshToken() {
		t.Error("expected refresh token rotation")
	}

	// Reusing the original refresh token must now be unauthenticated.
	if _, err := c.RefreshToken(ctx, &authv1.RefreshTokenRequest{RefreshToken: login.GetRefreshToken()}); status.Code(err) != codes.Unauthenticated {
		t.Errorf("expected Unauthenticated for reused refresh token, got %v", err)
	}
}
