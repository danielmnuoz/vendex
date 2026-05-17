package config

import (
	"testing"
	"time"
)

func TestLoad_RequiresDatabaseURL(t *testing.T) {
	t.Setenv("AUTH_DATABASE_URL", "")
	t.Setenv("AUTH_KEY_ENCRYPTION_KEY", "key")
	if _, err := Load(); err == nil {
		t.Error("expected error when DATABASE_URL missing")
	}
}

func TestLoad_RequiresEncryptionKey(t *testing.T) {
	t.Setenv("AUTH_DATABASE_URL", "postgres://x")
	t.Setenv("AUTH_KEY_ENCRYPTION_KEY", "")
	if _, err := Load(); err == nil {
		t.Error("expected error when ENCRYPTION_KEY missing")
	}
}

func TestLoad_Defaults(t *testing.T) {
	t.Setenv("AUTH_DATABASE_URL", "postgres://x")
	t.Setenv("AUTH_KEY_ENCRYPTION_KEY", "key")
	t.Setenv("AUTH_GRPC_ADDR", "")
	t.Setenv("AUTH_ACCESS_TOKEN_TTL", "")
	t.Setenv("AUTH_REFRESH_TOKEN_TTL", "")
	t.Setenv("AUTH_BCRYPT_COST", "")
	cfg, err := Load()
	if err != nil {
		t.Fatal(err)
	}
	if cfg.GRPCAddr != ":50051" {
		t.Errorf("addr default: got %q", cfg.GRPCAddr)
	}
	if cfg.AccessTokenTTL != 15*time.Minute {
		t.Errorf("access ttl: got %v", cfg.AccessTokenTTL)
	}
	if cfg.RefreshTokenTTL != 7*24*time.Hour {
		t.Errorf("refresh ttl: got %v", cfg.RefreshTokenTTL)
	}
	if cfg.BcryptCost != 12 {
		t.Errorf("bcrypt cost: got %d", cfg.BcryptCost)
	}
}

func TestLoad_OverridesFromEnv(t *testing.T) {
	t.Setenv("AUTH_DATABASE_URL", "postgres://x")
	t.Setenv("AUTH_KEY_ENCRYPTION_KEY", "key")
	t.Setenv("AUTH_GRPC_ADDR", ":1234")
	t.Setenv("AUTH_ACCESS_TOKEN_TTL", "30m")
	t.Setenv("AUTH_REFRESH_TOKEN_TTL", "240h")
	t.Setenv("AUTH_BCRYPT_COST", "8")
	cfg, err := Load()
	if err != nil {
		t.Fatal(err)
	}
	if cfg.GRPCAddr != ":1234" {
		t.Errorf("addr: got %q", cfg.GRPCAddr)
	}
	if cfg.AccessTokenTTL != 30*time.Minute {
		t.Errorf("access ttl: got %v", cfg.AccessTokenTTL)
	}
	if cfg.BcryptCost != 8 {
		t.Errorf("bcrypt cost: got %d", cfg.BcryptCost)
	}
}
