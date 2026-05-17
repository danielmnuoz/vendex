package main

import (
	"context"
	"log"
	"net"
	"os"
	"os/signal"
	"syscall"
	"time"

	cardsv1 "github.com/danielmnuoz/vendex/proto/gen/go/cards/v1"
	"github.com/danielmnuoz/vendex/services/card-catalog/internal/cache"
	"github.com/danielmnuoz/vendex/services/card-catalog/internal/config"
	"github.com/danielmnuoz/vendex/services/card-catalog/internal/server"
	"github.com/danielmnuoz/vendex/services/card-catalog/internal/service"
	"github.com/danielmnuoz/vendex/services/card-catalog/internal/store"
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

	var svcCache service.Cache
	if cfg.RedisAddr != "" {
		c := cache.New(cfg.RedisAddr, cfg.RedisCacheTTL)
		if err := c.Ping(ctx); err != nil {
			// Redis is best-effort — log and continue without it. Service
			// still works, every read just hits Postgres.
			log.Printf("redis ping failed (%v); running without cache", err)
			_ = c.Close()
		} else {
			svcCache = c
			defer c.Close()
		}
	}

	st := store.NewPostgres(pool)
	svc := service.New(st, svcCache)

	lis, err := net.Listen("tcp", cfg.GRPCAddr)
	if err != nil {
		log.Fatalf("listen: %v", err)
	}

	srv := grpc.NewServer()
	cardsv1.RegisterCardCatalogServiceServer(srv, server.New(svc))
	healthpb.RegisterHealthServer(srv, health.NewServer())
	reflection.Register(srv)

	go func() {
		log.Printf("card-catalog gRPC server listening on %s", cfg.GRPCAddr)
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
