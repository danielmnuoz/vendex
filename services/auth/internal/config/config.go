package config

import (
	"errors"
	"fmt"
	"os"
	"strconv"
	"time"
)

type Config struct {
	GRPCAddr            string
	DatabaseURL         string
	KeyEncryptionKeyB64 string
	AccessTokenTTL      time.Duration
	RefreshTokenTTL     time.Duration
	BcryptCost          int
}

func Load() (Config, error) {
	cfg := Config{
		GRPCAddr:            envOr("AUTH_GRPC_ADDR", ":50051"),
		DatabaseURL:         os.Getenv("AUTH_DATABASE_URL"),
		KeyEncryptionKeyB64: os.Getenv("AUTH_KEY_ENCRYPTION_KEY"),
		AccessTokenTTL:      mustDuration("AUTH_ACCESS_TOKEN_TTL", 15*time.Minute),
		RefreshTokenTTL:     mustDuration("AUTH_REFRESH_TOKEN_TTL", 7*24*time.Hour),
		BcryptCost:          mustInt("AUTH_BCRYPT_COST", 12),
	}
	if cfg.DatabaseURL == "" {
		return cfg, errors.New("AUTH_DATABASE_URL is required")
	}
	if cfg.KeyEncryptionKeyB64 == "" {
		return cfg, errors.New("AUTH_KEY_ENCRYPTION_KEY is required (base64-encoded 32 bytes)")
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

func mustInt(key string, fallback int) int {
	v := os.Getenv(key)
	if v == "" {
		return fallback
	}
	n, err := strconv.Atoi(v)
	if err != nil {
		panic(fmt.Sprintf("invalid int for %s: %v", key, err))
	}
	return n
}
