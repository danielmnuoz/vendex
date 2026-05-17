package config

import (
	"errors"
	"fmt"
	"os"
	"time"
)

type Config struct {
	GRPCAddr      string
	DatabaseURL   string
	RedisAddr     string
	RedisCacheTTL time.Duration
}

func Load() (Config, error) {
	cfg := Config{
		GRPCAddr:      envOr("CARD_CATALOG_GRPC_ADDR", ":50052"),
		DatabaseURL:   os.Getenv("CARD_CATALOG_DATABASE_URL"),
		RedisAddr:     envOr("CARD_CATALOG_REDIS_ADDR", "localhost:6379"),
		RedisCacheTTL: mustDuration("CARD_CATALOG_REDIS_TTL", time.Hour),
	}
	if cfg.DatabaseURL == "" {
		return cfg, errors.New("CARD_CATALOG_DATABASE_URL is required")
	}
	return cfg, nil
}

func envOr(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func mustDuration(key string, fallback time.Duration) time.Duration {
	v := os.Getenv(key)
	if v == "" {
		return fallback
	}
	d, err := time.ParseDuration(v)
	if err != nil {
		panic(fmt.Sprintf("invalid duration for %s: %v", key, err))
	}
	return d
}
