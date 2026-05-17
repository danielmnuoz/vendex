package main

import (
	"context"
	"log"
	"net"
	"os"
	"os/signal"
	"syscall"
	"time"

	authv1 "github.com/danielmnuoz/vendex/proto/gen/go/auth/v1"
	"github.com/danielmnuoz/vendex/services/auth/internal/config"
	"github.com/danielmnuoz/vendex/services/auth/internal/crypto"
	"github.com/danielmnuoz/vendex/services/auth/internal/jwt"
	"github.com/danielmnuoz/vendex/services/auth/internal/server"
	"github.com/danielmnuoz/vendex/services/auth/internal/service"
	"github.com/danielmnuoz/vendex/services/auth/internal/store"
	"github.com/jackc/pgx/v5/pgxpool"
	"google.golang.org/grpc"
	"google.golang.org/grpc/health"
	healthpb "google.golang.org/grpc/health/grpc_health_v1"
	"google.golang.org/grpc/reflection"
)

func main() {
	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("config: %v", err)
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	pool, err := pgxpool.New(ctx, cfg.DatabaseURL)
	if err != nil {
		log.Fatalf("connect db: %v", err)
	}
	defer pool.Close()
	if err := pool.Ping(ctx); err != nil {
		log.Fatalf("ping db: %v", err)
	}

	aead, err := crypto.NewAEAD(cfg.KeyEncryptionKeyB64)
	if err != nil {
		log.Fatalf("init aead: %v", err)
	}

	st := store.NewPostgres(pool)
	signer := jwt.NewSigner(st, aead)
	svc := service.New(st, signer, service.Config{
		BcryptCost:      cfg.BcryptCost,
		AccessTokenTTL:  cfg.AccessTokenTTL,
		RefreshTokenTTL: cfg.RefreshTokenTTL,
	})

	// Ensure an active signing key exists. First-boot bootstrap.
	if _, err := st.GetActiveSigningKey(ctx); err != nil {
		log.Printf("no active signing key found, generating initial key")
		if _, err := jwt.GenerateAndStoreKey(ctx, st, aead); err != nil {
			log.Fatalf("bootstrap signing key: %v", err)
		}
	}

	lis, err := net.Listen("tcp", cfg.GRPCAddr)
	if err != nil {
		log.Fatalf("listen: %v", err)
	}

	srv := grpc.NewServer()
	authv1.RegisterAuthServiceServer(srv, server.New(svc))
	healthpb.RegisterHealthServer(srv, health.NewServer())
	reflection.Register(srv)

	go func() {
		log.Printf("auth gRPC server listening on %s", cfg.GRPCAddr)
		if err := srv.Serve(lis); err != nil {
			log.Fatalf("serve: %v", err)
		}
	}()

	sig := make(chan os.Signal, 1)
	signal.Notify(sig, syscall.SIGINT, syscall.SIGTERM)
	<-sig
	log.Println("shutting down")

	stopped := make(chan struct{})
	go func() {
		srv.GracefulStop()
		close(stopped)
	}()
	select {
	case <-stopped:
	case <-time.After(10 * time.Second):
		log.Println("graceful stop timed out, forcing")
		srv.Stop()
	}
}
