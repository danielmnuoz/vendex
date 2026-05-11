# VenDex — Technical Specification

## Technical Goals

1. Build a production-grade distributed system using Go, gRPC, Kafka, Redis, PostgreSQL, Docker, and Kubernetes.
2. Learn microservice architecture through a real domain problem, not a toy example.
3. Develop agentic engineering workflows — automated PR creation, issue generation, code review, and architectural documentation using Claude Code and custom tooling.
4. Establish professional development practices: testing pipelines, CI/CD, observability, and structured code review.
5. Self-managed JWT authentication to understand token-based auth from scratch.

---

## Tech Stack

| Layer | Technology | Notes |
|---|---|---|
| Backend services | Go | One binary per service |
| Inter-service communication | gRPC + Protocol Buffers | Shared proto definitions |
| Event streaming | Kafka (or Redpanda locally) | Async inter-service events |
| Caching / overlap detection | Redis | Sorted sets, key-value lookups |
| Primary data store | PostgreSQL | Each service owns its schema |
| Frontend | React (Next.js) | Hosted on Vercel free tier |
| API Gateway | Go | REST-to-gRPC translation |
| Auth | Self-managed JWT (Go) | No third-party auth provider |
| Containerization | Docker | Multi-service Docker Compose |
| Orchestration | Kubernetes (Kind locally) | Production deployment target |
| Card data source | Pokemon TCG API (pokemontcg.io) | Free, no API key required |
| CI/CD | GitHub Actions | Build, test, deploy pipeline |
| Container registry | GitHub Container Registry | Free for public repos |
| Hosting (eventual) | Vercel (frontend) + VPS (backend) | Deferred until needed |

---

## Service Architecture

```
┌─────────────┐     ┌──────────────┐     ┌────────────────┐
│  API Gateway │────▶│ Auth Service  │     │  Card Catalog  │
│   (REST)     │     └──────────────┘     │   Service      │
└──────┬──────┘                           └───────┬────────┘
       │                                          │
       ├──────────────┬───────────────┬───────────┘
       ▼              ▼               ▼
┌──────────────┐ ┌──────────┐ ┌────────────────────┐
│  Inventory   │ │ Buy List │ │  Overlap Detection │
│  Service     │ │ Service  │ │  Engine            │
└──────┬───────┘ └────┬─────┘ └───────┬────────────┘
       │              │               │
       ▼              ▼               ▼
   ┌───────────────────────────────────────┐
   │              Kafka                     │
   │  (inventory.updated, buylist.updated, │
   │   event.created, overlap.found,       │
   │   offer.submitted)                    │
   └───────────────────┬───────────────────┘
                       │
          ┌────────────┼────────────┐
          ▼            ▼            ▼
   ┌─────────────┐ ┌────────┐ ┌───────────────┐
   │ Notification│ │ Event  │ │  Offer        │
   │ Service     │ │ Service│ │  Service      │
   └─────────────┘ └────────┘ └───────────────┘
```

Each service:
- Owns its own PostgreSQL schema (no shared databases)
- Has its own Dockerfile
- Communicates synchronously via gRPC, asynchronously via Kafka
- Has independent test suites
- Is deployed as a separate Kubernetes pod

---

## Phase 0 — Developer Tooling & Agentic Engineering Setup

**Goal:** Before writing any application code, establish the development workflow, testing infrastructure, and agentic engineering skills that will be used throughout the project. This phase is about building the tools that make every subsequent phase faster and more educational.

**What you learn:** Claude Code custom commands, GitHub CLI automation, CI/CD pipeline design, testing strategy, agentic workflow patterns.

### Deliverables

**Repository Structure**
```
vendex/
├── .github/
│   ├── workflows/
│   │   ├── ci.yml              # Build + test on every PR
│   │   ├── lint.yml             # Linting and formatting checks
│   │   └── release.yml          # Build and push Docker images on merge to main
│   └── PULL_REQUEST_TEMPLATE.md
├── proto/                       # Shared protobuf definitions
├── services/
│   ├── auth/
│   ├── card-catalog/
│   ├── inventory/
│   ├── buylist/
│   ├── event/
│   ├── overlap-detection/
│   ├── notification/
│   └── offer/
├── gateway/
├── frontend/
├── .skills/                     # Claude Code custom command skills
│   ├── pr/
│   ├── issue/
│   ├── review/
│   ├── explain/
│   ├── test/
│   └── scaffold/
├── scripts/                     # Developer utility scripts
├── docker-compose.yml
├── Makefile
└── README.md
```

**CI/CD Pipeline (GitHub Actions)**
- `ci.yml`: On every PR — run `go vet`, `golangci-lint`, unit tests, integration tests (with Dockerized Postgres/Kafka/Redis), generate coverage report.
- `lint.yml`: Enforce `gofmt`, protobuf linting, commit message format.
- `release.yml`: On merge to `main` — build Docker images for each service, push to GitHub Container Registry, tag with commit SHA.

**Testing Strategy**
- **Unit tests:** Every service has unit tests for business logic. No external dependencies — use interfaces and mocks.
- **Integration tests:** Test gRPC endpoints with a real database (Dockerized Postgres spun up in CI). Test Kafka producers/consumers with an embedded or Dockerized Kafka.
- **End-to-end tests:** After Phase 5, test full workflows through the API Gateway.
- **Test coverage target:** 70%+ per service from Phase 1 onward. Not a vanity metric — the goal is to catch regressions as services evolve.
- **Table-driven tests:** Follow Go convention of table-driven test cases for comprehensive input coverage.

**Claude Code Custom Commands (Agentic Skills)**

These are custom slash commands or prompt templates for Claude Code that standardize your development workflow. Each one is a reusable skill.

`/pr` — **PR Creation Skill**
- Analyzes staged changes (git diff)
- Generates a PR title following conventional commit format
- Writes a structured PR description: summary, what changed and why, testing done, related issues
- Creates the PR via GitHub CLI (`gh pr create`)
- Links to relevant GitHub issues if referenced in commits

`/issue` — **GitHub Issue Creation Skill**
- Takes a feature description or bug report as input
- Generates a structured GitHub issue: title, description, acceptance criteria, technical notes, labels
- Creates the issue via `gh issue create`
- Optionally breaks a large feature into sub-issues

`/review` — **Code Review Skill**
- Reads a PR diff
- Analyzes for: correctness, error handling, test coverage gaps, naming conventions, Go idioms, gRPC best practices, potential concurrency issues
- Produces a structured review with line-specific comments
- Flags severity levels: critical, suggestion, nit

`/explain` — **Architecture Decision Explainer**
- After writing a piece of code, generates an explanation of what was built and why
- Covers: what pattern was used, what alternatives were considered, why this approach was chosen, trade-offs accepted
- Outputs as a markdown file in a `docs/decisions/` directory (lightweight ADR format)
- Example: "Why we used Redis sorted sets for overlap detection instead of PostgreSQL joins"

`/test` — **Test Generation Skill**
- Reads a Go source file
- Generates table-driven test cases covering: happy path, edge cases, error conditions
- Creates mock implementations for interfaces
- Outputs a `_test.go` file ready to run

`/scaffold` — **Service Scaffolding Skill**
- Given a service name, generates the boilerplate: main.go, server setup, gRPC server registration, Dockerfile, database migration files, Makefile targets, health check endpoint
- Ensures consistency across all services

**Makefile Targets**
```makefile
make proto          # Regenerate Go code from .proto files
make test           # Run all unit tests
make test-integration  # Run integration tests (requires Docker)
make lint           # Run linters
make build          # Build all service binaries
make docker-build   # Build all Docker images
make up             # docker-compose up (full local environment)
make down           # docker-compose down
make migrate        # Run database migrations for all services
make new-service    # Scaffold a new service (calls /scaffold)
```

---

## Phase 1 — Card Catalog & Auth Services

**Goal:** Build the first two microservices, establish gRPC communication patterns, and implement self-managed JWT authentication from scratch.

**What you learn:** Go project structure, Protocol Buffers / gRPC implementation, JWT token lifecycle (signing, validation, refresh, expiry), Docker multi-service setup, PostgreSQL schema design, Redis caching.

### Deliverables

**Card Catalog Service (Go + gRPC)**
- Syncs card data from the Pokemon TCG API into a local PostgreSQL table.
- Normalizes cards into a canonical schema: card ID, name, set, set series, rarity, image URL, release date.
- Redis cache layer for frequent lookups (cards don't change often — high cache hit rate).
- gRPC endpoints:
  - `SearchCards(query, set_filter, rarity_filter)` → paginated results
  - `GetCardById(card_id)` → single card
  - `GetCardsByIds(card_ids)` → batch lookup
  - `ListSets()` → all available sets
  - `SyncCards()` → trigger re-sync from Pokemon TCG API

**Auth Service (Go + gRPC + Self-Managed JWT)**
- Registration: email + password. Password hashed with bcrypt.
- Login: validate credentials, issue signed JWT access token (short-lived, 15 min) + refresh token (long-lived, 7 days, stored in DB).
- Token validation: middleware-compatible endpoint that other services call to verify JWTs.
- Token refresh: exchange valid refresh token for new access token.
- Role assignment: vendor, attendee, organizer.
- Vendor profile: shop name, location (city/state), description.
- JWT implementation details:
  - RS256 signing (asymmetric — private key signs, public key verifies)
  - Claims: user_id, role, issued_at, expires_at
  - Public key exposed via gRPC endpoint so other services can validate tokens independently without calling Auth Service on every request
- gRPC endpoints:
  - `Register(email, password, role)` → user_id
  - `Login(email, password)` → access_token, refresh_token
  - `RefreshToken(refresh_token)` → new access_token
  - `ValidateToken(access_token)` → user_id, role
  - `GetPublicKey()` → RSA public key for independent validation
  - `GetVendorProfile(vendor_id)` → profile details
  - `UpdateProfile(vendor_id, fields)` → updated profile

**Docker Compose (Local Development)**
```yaml
services:
  postgres:       # Shared Postgres instance, separate databases per service
  redis:          # Shared Redis instance
  kafka:          # Kafka broker (or Redpanda)
  zookeeper:      # If using Kafka (not needed with Redpanda)
  card-catalog:   # Card Catalog Service
  auth:           # Auth Service
```

### Database Schemas

**card_catalog_db.cards**
| Column | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| external_id | VARCHAR | Pokemon TCG API ID (e.g., "sv3-100") |
| name | VARCHAR | Card name |
| set_id | VARCHAR | Set identifier |
| set_name | VARCHAR | Human-readable set name |
| set_series | VARCHAR | e.g., Scarlet & Violet |
| rarity | VARCHAR | |
| image_url | VARCHAR | Small image URL |
| image_url_large | VARCHAR | High-res image URL |
| release_date | DATE | |
| created_at | TIMESTAMP | |
| updated_at | TIMESTAMP | |

**auth_db.users**
| Column | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| email | VARCHAR | Unique |
| password_hash | VARCHAR | bcrypt |
| role | VARCHAR | vendor / attendee / organizer |
| shop_name | VARCHAR | Nullable for non-vendors |
| city | VARCHAR | |
| state | VARCHAR | |
| created_at | TIMESTAMP | |
| updated_at | TIMESTAMP | |

**auth_db.refresh_tokens**
| Column | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| user_id | UUID | FK to users |
| token_hash | VARCHAR | Hashed refresh token |
| expires_at | TIMESTAMP | |
| revoked | BOOLEAN | For logout / token rotation |
| created_at | TIMESTAMP | |

---

## Phase 2 — Inventory, Buy Lists & Event Service

**Goal:** Enable vendors to upload what they have and what they want, scoped to specific events. Introduce Kafka as the event-driven communication backbone.

**What you learn:** Kafka producers/consumers in Go, CSV parsing and data ingestion pipelines, event-driven architecture patterns, data validation, fuzzy matching for card resolution.

### Deliverables

**Inventory Service (Go + gRPC + Kafka producer)**
- Vendors upload inventory via CSV or manual entry.
- Each inventory entry references a canonical card ID from the Card Catalog Service (resolved via gRPC call).
- Fields: card reference, quantity, condition (NM/LP/MP/HP/DMG), grading company + grade (optional), asking price, priority flag ("liquidate" / "normal").
- Inventory can be scoped to a specific event or marked as always-available.
- Publishes `inventory.updated` events to Kafka on every change.
- CSV import: fuzzy-matches card_name + set_name against the Card Catalog. Unresolvable rows flagged for manual review.
- gRPC endpoints: `AddInventory`, `BulkImportCSV`, `UpdateItem`, `RemoveItem`, `ListInventory`, `ListInventoryByEvent`.

**Buy List Service (Go + gRPC + Kafka producer)**
- Vendors maintain a list of cards they want to acquire.
- Fields: card reference, minimum acceptable condition, max buy price, quantity wanted.
- Publishes `buylist.updated` events to Kafka.
- gRPC endpoints: `AddWantedCard`, `RemoveWantedCard`, `UpdateWantedCard`, `ListBuyList`.

**Event Service (Go + gRPC + Kafka producer)**
- Organizers create events: name, location, date range, description.
- Vendors register attendance at events.
- Attendees register for events.
- Publishes `event.created`, `event.vendor_registered`, `event.attendee_registered` to Kafka.
- gRPC endpoints: `CreateEvent`, `UpdateEvent`, `RegisterForEvent`, `UnregisterFromEvent`, `ListEvents`, `GetEvent`, `GetEventVendors`, `GetEventAttendees`.

**Kafka Topics & Event Schemas**
```
inventory.updated     → { vendor_id, event_id, card_id, action: "added"|"removed"|"updated", timestamp }
buylist.updated       → { vendor_id, card_id, action: "added"|"removed"|"updated", timestamp }
event.created         → { event_id, organizer_id, name, dates, location, timestamp }
event.vendor_registered → { event_id, vendor_id, timestamp }
```

**CSV Import Format**
```
card_name,set_name,condition,quantity,price,priority
Charizard ex,Obsidian Flames,NM,3,45.00,normal
Pikachu VMAX,Vivid Voltage,LP,1,12.50,liquidate
```

### Database Schemas

**inventory_db.inventory_items**
| Column | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| vendor_id | UUID | Owner |
| card_id | UUID | FK to card catalog (logical, cross-service) |
| event_id | UUID | Nullable — null means always-available |
| condition | VARCHAR | NM/LP/MP/HP/DMG |
| grading_company | VARCHAR | Nullable (PSA/BGS/CGC) |
| grade | DECIMAL | Nullable |
| quantity | INT | |
| asking_price | DECIMAL | |
| priority | VARCHAR | normal / liquidate |
| created_at | TIMESTAMP | |
| updated_at | TIMESTAMP | |

**buylist_db.wanted_cards**
| Column | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| vendor_id | UUID | Owner |
| card_id | UUID | FK to card catalog (logical) |
| min_condition | VARCHAR | Minimum acceptable |
| max_price | DECIMAL | Maximum buy price |
| quantity_wanted | INT | |
| created_at | TIMESTAMP | |
| updated_at | TIMESTAMP | |

**event_db.events**
| Column | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| organizer_id | UUID | Creator |
| name | VARCHAR | |
| city | VARCHAR | |
| state | VARCHAR | |
| venue | VARCHAR | |
| start_date | DATE | |
| end_date | DATE | |
| description | TEXT | |
| created_at | TIMESTAMP | |

**event_db.event_registrations**
| Column | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| event_id | UUID | FK to events |
| user_id | UUID | Vendor or attendee |
| role | VARCHAR | vendor / attendee |
| registered_at | TIMESTAMP | |

---

## Phase 3 — Overlap Detection Engine & Notifications

**Goal:** Build the core value proposition — automatically detect when vendor inventories overlap with other vendors' buy lists within an event context. Surface actionable opportunities.

**What you learn:** Redis data structures for set operations, Kafka consumer group patterns, algorithm design for set intersection and scoring, notification system design.

### Deliverables

**Overlap Detection Engine (Go + Redis + Kafka consumer)**
- Consumes `inventory.updated`, `buylist.updated`, and `event.vendor_registered` events from Kafka.
- Maintains Redis data structures per event:
  - `event:{event_id}:vendor:{vendor_id}:inventory` → Set of card IDs this vendor has
  - `event:{event_id}:vendor:{vendor_id}:buylist` → Set of card IDs this vendor wants
  - `event:{event_id}:vendors` → Set of vendor IDs registered for this event
- On each relevant event, computes overlaps:
  - For each pair of vendors attending the same event: intersect Vendor A's inventory with Vendor B's buy list, and vice versa.
  - Only recompute affected pairs when inventory or buy lists change (not full recalculation).
- Overlap scoring:
  - Price alignment: is the asking price within the buyer's max price? Closer = higher score.
  - Condition match: does the card's condition meet the buyer's minimum?
  - Quantity: can the seller fulfill the buyer's quantity need?
  - Priority: seller marked "liquidate" boosts score (motivated seller).
- Publishes `overlap.found` events to Kafka with details and score.
- gRPC endpoint: `GetOverlapsForVendor(vendor_id, event_id)` → list of opportunities with scores.

**Notification Service (Go + Kafka consumer + PostgreSQL)**
- Consumes `overlap.found` events.
- Stores notifications per vendor in PostgreSQL.
- Deduplicates: if the same overlap is found again (e.g., after an inventory update), update existing notification rather than creating a duplicate.
- gRPC endpoints: `GetNotifications(vendor_id)`, `MarkAsRead(notification_id)`, `GetUnreadCount(vendor_id)`.
- Future: WebSocket for real-time push, email digests. Deferred.

**The core experience comes alive:**
- Vendor opens the app before a convention.
- Sees: "4 vendors at Collect-A-Con Dallas have cards on your buy list."
- Sees: "2 vendors are looking for cards you marked to liquidate."
- Drills into each opportunity to see card details, quantities, price alignment.

### Overlap Detection Logic

```
On inventory.updated or buylist.updated for vendor V at event E:
  For each other vendor W registered at event E:
    sell_overlaps = V.inventory ∩ W.buylist    (cards V can sell to W)
    buy_overlaps  = W.inventory ∩ V.buylist    (cards V can buy from W)

    For each overlapping card:
      score = compute_score(price_alignment, condition_match, quantity, priority)
      if score > threshold:
        publish overlap.found event
```

Redis `SINTER` handles the set intersection efficiently. Scoring is computed in Go after intersection results are retrieved.

---

## Phase 4 — Anonymous Attendee Listings & Offer Flow

**Goal:** Let convention attendees post anonymous temporary inventory that vendors can discover and express interest in. Build the offer/negotiation workflow.

**What you learn:** Anonymity and privacy patterns, temporary data lifecycle management, offer state machines, Kafka-driven workflows.

### Deliverables

**Attendee Listing Flow (extensions to Inventory + Event services)**
- Attendees register for an event and enable "event seller mode."
- Upload cards: card reference, condition, rough price expectation, preference (cash / trade / both).
- Listings are anonymous — vendors see card details, condition, and price expectation but not who posted them.
- Listings are temporary: auto-expire when the event ends (background job or Kafka-driven TTL).
- Listings are only visible to verified vendors registered at the same event.

**Offer Service (Go + gRPC + Kafka)**
- Vendors submit interest signals or preliminary price ranges on anonymous listings.
- Offer states: `pending` → `viewed` → `accepted` / `declined` / `expired`.
- Attendee receives aggregated interest: "3 vendors interested, offers ranging $40–$48."
- Attendee accepts an offer, which triggers identity reveal (first name + general location).
- Publishes `offer.submitted`, `offer.viewed`, `offer.accepted`, `offer.declined` to Kafka.
- gRPC endpoints: `SubmitOffer`, `ListOffersForListing`, `ListOffersForVendor`, `AcceptOffer`, `DeclineOffer`, `RevealIdentity`.

**Privacy Rules (Enforced at Service Level)**
- Attendee user_id is never included in any response to vendors until `AcceptOffer` is called.
- Listing queries for vendors return a listing_id but no user information.
- The Offer Service is the only service that handles identity reveal, and only on explicit attendee action.
- Listings cannot be queried outside of their event scope.

### Database Schemas

**inventory_db.attendee_listings** (extension to Inventory Service)
| Column | Type | Notes |
|---|---|---|
| id | UUID | Primary key (this is the anonymous listing_id) |
| attendee_id | UUID | Owner — never exposed to vendors |
| event_id | UUID | Scoping |
| card_id | UUID | FK to card catalog (logical) |
| condition | VARCHAR | |
| price_expectation | DECIMAL | Rough ask |
| preference | VARCHAR | cash / trade / both |
| status | VARCHAR | active / expired / sold |
| created_at | TIMESTAMP | |
| expires_at | TIMESTAMP | Set to event end date |

**offer_db.offers**
| Column | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| listing_id | UUID | FK to attendee_listings (logical) |
| vendor_id | UUID | Who submitted the offer |
| offered_price | DECIMAL | |
| message | TEXT | Optional short note |
| status | VARCHAR | pending / viewed / accepted / declined / expired |
| created_at | TIMESTAMP | |
| updated_at | TIMESTAMP | |

---

## Phase 5 — Frontend & API Gateway

**Goal:** Build the user-facing web application and unify the backend behind a single REST API gateway.

**What you learn:** React frontend architecture, REST-to-gRPC translation patterns, API gateway design, JWT middleware, mobile-first responsive design.

### Deliverables

**API Gateway (Go)**
- Single REST entry point that translates HTTP requests to gRPC calls to backend services.
- JWT validation middleware using the Auth Service's public key.
- Rate limiting via Redis (token bucket or sliding window).
- Request logging and error normalization.
- Route structure:
  - `POST /api/v1/auth/register`, `/login`, `/refresh`
  - `GET /api/v1/cards/search`, `/cards/:id`, `/sets`
  - `GET/POST /api/v1/inventory`, `POST /api/v1/inventory/import`
  - `GET/POST /api/v1/buylist`
  - `GET/POST /api/v1/events`, `POST /api/v1/events/:id/register`
  - `GET /api/v1/overlaps/:event_id`
  - `GET /api/v1/notifications`
  - `GET/POST /api/v1/listings` (attendee flow)
  - `GET/POST /api/v1/offers`

**Frontend (Next.js on Vercel)**
- **Vendor Dashboard:** upcoming events, active overlap opportunities, notification feed, inventory manager (CRUD + CSV upload), buy list manager.
- **Event View:** browse events, register, view event-scoped overlaps and opportunities.
- **Attendee Flow:** event registration, anonymous listing upload, incoming offers view, accept/reveal workflow.
- **Organizer View (minimal):** event creation form, vendor roster, basic aggregate stats.
- Mobile-first responsive design — conventions are a phone-in-hand context.
- Hosted on Vercel free tier (Hobby plan).

---

## Phase 6 — Kubernetes Deployment & Observability

**Goal:** Deploy the full system on Kubernetes with proper observability. Deepen infrastructure and operations skills.

**What you learn:** Kubernetes manifests and Helm charts, resource management, distributed logging and tracing, horizontal scaling, CI/CD deployment pipelines.

### Deliverables

- Kubernetes manifests or Helm charts for every service, plus PostgreSQL, Redis, and Kafka.
- Namespace separation: `vendex-prod`, `vendex-staging`.
- Resource requests and limits for each pod.
- Horizontal Pod Autoscaling for the Overlap Detection Engine (the most compute-variable service).
- Centralized logging: Grafana Loki (lightweight, free, self-hosted).
- Distributed tracing: OpenTelemetry SDK in each Go service → Jaeger backend.
- Health check and readiness probes on all services.
- CI/CD extension: GitHub Actions workflow deploys to K8s on merge to main (kubectl apply or Helm upgrade).
- Deploy target: local Kind cluster for development, small Hetzner/DigitalOcean VPS ($5–10/mo) for a live demo if desired.

### Observability Stack
```
Services (OpenTelemetry SDK)
    ├── Traces → Jaeger
    ├── Logs → Grafana Loki → Grafana Dashboard
    └── Metrics → Prometheus → Grafana Dashboard
```

---

## Phase 7 (Future) — Intelligence Layer

**Goal:** Turn accumulated data into vendor insights. Speculative — depends on having meaningful data volume.

### Potential Features
- Demand trend tracking: which cards appear on buy lists most frequently across events.
- Price convergence: seller asking price vs buyer max price over time.
- Event analytics for organizers: overlap volume, vendor engagement, top-traded cards.
- Inventory velocity: how quickly certain cards move through the system.
- Regional demand patterns.

Design the data model to support this from Phase 2 onward (timestamps, event scoping, price fields), even though the analytics features come later.

---

## Cost Summary

| Resource | Cost |
|---|---|
| Pokemon TCG API | Free |
| PostgreSQL | Free (Docker locally) |
| Kafka / Redpanda | Free (Docker locally) |
| Redis | Free (Docker locally) |
| Frontend hosting | Free (Vercel Hobby tier) |
| Backend hosting | Deferred — free locally, ~$5–20/mo VPS when needed |
| Container registry | Free (GitHub Container Registry) |
| CI/CD | Free (GitHub Actions) |
| Domain name | ~$10–15/year when needed |

**Total during development: $0**

---

## Phase Summary

| Phase | Focus | Key Technologies |
|---|---|---|
| 0 | Dev tooling, CI/CD, agentic skills | Claude Code, GitHub Actions, Make, testing frameworks |
| 1 | Card Catalog, Auth (JWT) | Go, gRPC, Protobuf, Docker, PostgreSQL, Redis, JWT/RSA |
| 2 | Inventory, Buy Lists, Events, Kafka | Go, Kafka, CSV ingestion, event-driven architecture |
| 3 | Overlap Detection, Notifications | Redis set operations, Kafka consumers, scoring algorithms |
| 4 | Anonymous Attendee Listings, Offers | Privacy patterns, state machines, data lifecycle |
| 5 | Frontend & API Gateway | Next.js, REST-to-gRPC, Vercel, mobile-first design |
| 6 | Kubernetes & Observability | K8s, Helm, OpenTelemetry, Jaeger, Loki, Prometheus |
| 7 | Intelligence Layer | Analytics, trend detection, pricing data |
