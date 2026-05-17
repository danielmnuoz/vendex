# VenDex top-level Makefile.
#
# Targets are intended to be runnable from a clean clone. Tool installs go to
# $(GOPATH)/bin via `go install`, so make sure that's on your PATH.

GO              ?= go
GOPATH          := $(shell $(GO) env GOPATH)
BIN             := $(GOPATH)/bin
BUF             := $(BIN)/buf
MIGRATE         := $(BIN)/migrate
GOLANGCI_LINT   := $(BIN)/golangci-lint

DOCKER_COMPOSE  ?= docker compose

AUTH_DATABASE_URL              ?= postgres://vendex:vendex@localhost:5432/auth_db?sslmode=disable
AUTH_TEST_DATABASE_URL         ?= postgres://vendex:vendex@localhost:5433/auth_test_db?sslmode=disable
CARD_CATALOG_DATABASE_URL      ?= postgres://vendex:vendex@localhost:5432/card_catalog_db?sslmode=disable
CARD_CATALOG_TEST_DATABASE_URL ?= postgres://vendex:vendex@localhost:5433/card_catalog_test_db?sslmode=disable

.PHONY: help
help: ## Show this help.
	@awk 'BEGIN {FS = ":.*##"; printf "Targets:\n"} /^[a-zA-Z_-]+:.*?##/ { printf "  \033[36m%-22s\033[0m %s\n", $$1, $$2 }' $(MAKEFILE_LIST)

# ---------- tooling ----------

.PHONY: tools
tools: $(BUF) $(MIGRATE) $(GOLANGCI_LINT) ## Install developer tools (buf, migrate, golangci-lint).

$(BUF):
	$(GO) install github.com/bufbuild/buf/cmd/buf@v1.69.0

$(MIGRATE):
	$(GO) install -tags 'postgres' github.com/golang-migrate/migrate/v4/cmd/migrate@v4.18.1

$(GOLANGCI_LINT):
	$(GO) install github.com/golangci/golangci-lint/cmd/golangci-lint@v1.62.0

# ---------- proto ----------

.PHONY: proto
proto: $(BUF) ## Regenerate protobuf Go code.
	cd proto && $(BUF) generate

.PHONY: proto-lint
proto-lint: $(BUF) ## Lint .proto files.
	cd proto && $(BUF) lint

# ---------- code quality ----------

.PHONY: lint
lint: $(GOLANGCI_LINT) ## Run linters.
	$(GOLANGCI_LINT) run ./...

.PHONY: vet
vet: ## Run go vet.
	$(GO) vet ./...

.PHONY: fmt
fmt: ## Run gofmt.
	gofmt -s -w .

.PHONY: fmt-check
fmt-check: ## Verify gofmt has been run.
	@unformatted=$$(gofmt -s -l .); \
	if [ -n "$$unformatted" ]; then \
		echo "Unformatted files:"; echo "$$unformatted"; exit 1; \
	fi

# ---------- tests ----------

.PHONY: test
test: ## Run unit tests.
	$(GO) test -race -count=1 ./...

.PHONY: test-coverage
test-coverage: ## Run unit tests with coverage.
	$(GO) test -race -count=1 -coverprofile=coverage.out ./...
	$(GO) tool cover -func=coverage.out | tail -1

.PHONY: test-integration
test-integration: ## Run integration tests (requires test-DB env vars; defaults point at the docker-compose postgres-test service).
	AUTH_TEST_DATABASE_URL=$(AUTH_TEST_DATABASE_URL) \
	CARD_CATALOG_TEST_DATABASE_URL=$(CARD_CATALOG_TEST_DATABASE_URL) \
	$(GO) test -race -count=1 -tags integration ./...

# ---------- build ----------

.PHONY: build
build: ## Build all service binaries into ./bin.
	mkdir -p bin
	$(GO) build -o bin/auth ./services/auth/cmd/server
	$(GO) build -o bin/card-catalog ./services/card-catalog/cmd/server
	$(GO) build -o bin/seed-cards ./services/card-catalog/cmd/seed-cards

.PHONY: docker-build
docker-build: ## Build all service Docker images.
	$(DOCKER_COMPOSE) build

# ---------- local environment ----------

.PHONY: up
up: ## Start the local dev environment (postgres, redis, auth).
	$(DOCKER_COMPOSE) up -d

.PHONY: down
down: ## Stop the local dev environment.
	$(DOCKER_COMPOSE) down

.PHONY: logs
logs: ## Tail logs from the local dev environment.
	$(DOCKER_COMPOSE) logs -f

# ---------- migrations ----------

.PHONY: migrate-up
migrate-up: $(MIGRATE) ## Apply all pending migrations for every service.
	$(MIGRATE) -path services/auth/migrations         -database "$(AUTH_DATABASE_URL)"         up
	$(MIGRATE) -path services/card-catalog/migrations -database "$(CARD_CATALOG_DATABASE_URL)" up

.PHONY: migrate-down
migrate-down: $(MIGRATE) ## Roll back the most recent migration for every service.
	$(MIGRATE) -path services/auth/migrations         -database "$(AUTH_DATABASE_URL)"         down 1
	$(MIGRATE) -path services/card-catalog/migrations -database "$(CARD_CATALOG_DATABASE_URL)" down 1

.PHONY: migrate
migrate: migrate-up ## Alias for migrate-up.

.PHONY: seed-cards
seed-cards: ## Populate card_catalog_db.cards from TCGdex (one-time per environment; idempotent on re-run).
	CARD_CATALOG_DATABASE_URL=$(CARD_CATALOG_DATABASE_URL) $(GO) run ./services/card-catalog/cmd/seed-cards

# ---------- secrets ----------

.PHONY: gen-encryption-key
gen-encryption-key: ## Generate a fresh base64 32-byte key for AUTH_KEY_ENCRYPTION_KEY.
	@openssl rand -base64 32
