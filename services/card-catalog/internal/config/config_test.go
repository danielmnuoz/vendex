package config

import (
	"testing"
	"time"
)

func TestLoad_RequiresDatabaseURL(t *testing.T) {
	t.Setenv("CARD_CATALOG_DATABASE_URL", "")
	if _, err := Load(); err == nil {
		t.Error("expected error when DATABASE_URL missing")
	}
}

func TestLoad_Defaults(t *testing.T) {
	t.Setenv("CARD_CATALOG_DATABASE_URL", "postgres://x")
	t.Setenv("CARD_CATALOG_GRPC_ADDR", "")
	t.Setenv("CARD_CATALOG_REDIS_ADDR", "")
	t.Setenv("CARD_CATALOG_REDIS_TTL", "")
	cfg, err := Load()
	if err != nil {
		t.Fatal(err)
	}
	if cfg.GRPCAddr != ":50052" {
		t.Errorf("addr default: got %q", cfg.GRPCAddr)
	}
	if cfg.RedisAddr != "localhost:6379" {
		t.Errorf("redis addr default: got %q", cfg.RedisAddr)
	}
	if cfg.RedisCacheTTL != time.Hour {
		t.Errorf("redis ttl default: got %v", cfg.RedisCacheTTL)
	}
}

func TestLoad_Overrides(t *testing.T) {
	t.Setenv("CARD_CATALOG_DATABASE_URL", "postgres://x")
	t.Setenv("CARD_CATALOG_GRPC_ADDR", ":9090")
	t.Setenv("CARD_CATALOG_REDIS_ADDR", "redis:6379")
	t.Setenv("CARD_CATALOG_REDIS_TTL", "5m")
	cfg, err := Load()
	if err != nil {
		t.Fatal(err)
	}
	if cfg.GRPCAddr != ":9090" {
		t.Errorf("addr: got %q", cfg.GRPCAddr)
	}
	if cfg.RedisAddr != "redis:6379" {
		t.Errorf("redis: got %q", cfg.RedisAddr)
	}
	if cfg.RedisCacheTTL != 5*time.Minute {
		t.Errorf("ttl: got %v", cfg.RedisCacheTTL)
	}
}
