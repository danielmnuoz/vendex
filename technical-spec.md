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
| Card data source | TCGdex (tcgdex.net) | Free, open-source, no API key, no rate limit. (Note: pokemontcg.io was absorbed into the commercial Scrydex platform — TCGdex is now the durable free option for Pokemon card identity data.) |
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
   ┌─────────────┐ ┌────────┐ ┌──────────────────────────┐
   │ Notification│ │ Event  │ │  Offer Service           │
   │ Service     │ │ Service│ │  (owns attendee_listings)│
   └─────────────┘ └────────┘ └──────────────────────────┘
```

> **Note on service ownership:** The Offer Service owns the full lifecycle of anonymous attendee listings (creation, query, expiration, identity reveal). The Inventory Service is exclusively for vendor inventory and never sees attendee data. Vendor-to-vendor booth reveal (from a Phase 3 overlap) is also handled by the Offer Service — it is the single service that ever returns booth or contact information across an identity boundary.

Each service:
- Owns its own PostgreSQL schema (no shared databases)
- Has its own Dockerfile
- Communicates synchronously via gRPC, asynchronously via Kafka
- Has independent test suites
- Is deployed as a separate Kubernetes pod

---

## Phase 0 — Design, Specs & Agentic Workflow Setup

**Goal:** Establish the things that exist *independently* of application code — visual design, written specs, collaboration rules, and the Claude Code skills needed to operate on the repo. Anything that needs real Go code to be useful (linters, build tooling, docker-compose, service scaffolding) is deferred to Phase 1, where it ships alongside the first consumer. This keeps Phase 0 from accumulating placeholder scaffolds that drift before they're used.

**What you learn:** Claude Code custom commands, GitHub CLI automation, design-token workflows, spec-driven development.

### Deliverables

**Repository skeleton (Phase 0 scope only)**
```
vendex/
├── .github/
│   ├── workflows/
│   │   └── ci.yml              # Placeholder skeleton — real steps added in Phase 1
│   └── PULL_REQUEST_TEMPLATE.md
├── .claude/
│   └── skills/                  # Skills usable today, against specs/markdown/PRs
│       ├── pr/
│       ├── issue/
│       ├── review/
│       ├── explain-code-changes/
│       └── learn/
├── ui/
│   ├── vendex.pen              # Canonical mockups (encrypted; access via pencil MCP)
│   ├── tokens.css              # Design tokens — single source of truth for Phase 4+
│   └── color-palette.md        # Palette reference doc
├── CLAUDE.md                   # Agent collaboration rules
├── product-spec.md
├── technical-spec.md
└── README.md
```

Application directories (`proto/`, `services/`, `gateway/`, `frontend/`, `scripts/`) are created in Phase 1 when they have something to hold.

**CI skeleton (`ci.yml`)**
A placeholder workflow that runs on every PR with `continue-on-error: true`. It checks out the repo and sets up Go but performs no real validation — its job is to exist so Phase 1 can flip on real steps (`go vet`, `golangci-lint`, unit + integration tests) without restructuring the workflow. `lint.yml` and `release.yml` are deferred to Phase 1.

**PR Template (`.github/PULL_REQUEST_TEMPLATE.md`)**
Sections matching the `/pr` skill's output (Summary, What changed and why, Testing, Related issues) plus a project-specific checklist: specs updated if behavior changed, UI consumes `ui/tokens.css` rather than hex literals, mockups in `ui/vendex.pen` updated via pencil MCP if visual design changed.

**Visual design source**
`ui/vendex.pen` contains the canonical mockups for the vendor and attendee flows (Phases 0–5). `ui/tokens.css` is the single source of truth for color and font variables — Phase 4+ React code consumes these directly. The pen file still has hex literals embedded; a `replace_all_matching_properties` pass against the token names is owed before the Phase 4 component library is built.

**Claude Code Custom Commands (Phase 0 scope)**

Only the skills that can operate on what exists today (specs, markdown, PRs) ship in Phase 0. Code-aware skills (`/test`) move to Phase 1.

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

---

## Phase 1 — Card Catalog & Auth Services

**Goal:** Build the first two microservices, establish gRPC communication patterns, implement self-managed JWT authentication from scratch, and bring online the build/test tooling that was deferred from Phase 0 now that there is real code for it to operate on.

**What you learn:** Go project structure, Protocol Buffers / gRPC implementation, JWT token lifecycle (signing, validation, refresh, expiry), Docker multi-service setup, PostgreSQL schema design, Redis caching, CI/CD pipeline design, testing strategy.

### Tooling brought online with the first services

These were originally listed under Phase 0 but ship in Phase 1 because they need real Go code (or running services) to be useful. They land in the same batch as the first service skeleton.

**Application directory skeleton**
```
vendex/
├── proto/                       # Shared protobuf definitions
├── services/
│   ├── auth/
│   └── card-catalog/
├── scripts/                     # Developer utility scripts
└── ...                          # gateway/, frontend/, additional services added in later phases
```

**CI/CD Pipeline (GitHub Actions)** — replaces the Phase 0 `ci.yml` placeholder
- `ci.yml`: On every PR — run `go vet`, `golangci-lint`, unit tests, integration tests (with Dockerized Postgres + Redis in Phase 1; Redpanda added when Phase 2 services land), generate coverage report.
- `lint.yml`: Enforce `gofmt`, protobuf linting, commit message format.
- `release.yml`: On merge to `main` — build Docker images for each service, push to GitHub Container Registry, tag with commit SHA.

**Testing Strategy** — applies from Phase 1 onward
- **Unit tests:** Every service has unit tests for business logic. No external dependencies — use interfaces and mocks.
- **Integration tests:** Test gRPC endpoints with a real database (Dockerized Postgres spun up in CI). Test Kafka producers/consumers with an embedded or Dockerized Kafka.
- **End-to-end tests:** After Phase 5, test full workflows through the API Gateway.
- **Test coverage target:** 70%+ per service. Not a vanity metric — the goal is to catch regressions as services evolve.
- **Table-driven tests:** Follow Go convention of table-driven test cases for comprehensive input coverage.

**Makefile (top-level)**
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
```

**Additional Claude Code skills** — code-aware, deferred from Phase 0

`/test` — **Test Generation Skill**
- Reads a Go source file
- Generates table-driven test cases covering: happy path, edge cases, error conditions
- Creates mock implementations for interfaces
- Outputs a `_test.go` file ready to run

(A `/scaffold` skill was considered but dropped — with only two services in Phase 1, codifying "service boilerplate" would encode the wrong patterns prematurely. Revisit in Phase 2+ once we have three or more services and can see what's actually repeated.)

### Deliverables

**Card Catalog Service (Go + gRPC)**
- Syncs card data from TCGdex into a local PostgreSQL table via a one-time seed script (`scripts/seed-cards.go`) run per environment. All user-facing reads go through our local Postgres + Redis — the external API is never on the request path.
- Normalizes cards into a canonical schema: card ID, name, set, set series, rarity, image URL, release date.
- Redis cache layer for frequent lookups (cards don't change often — high cache hit rate).
- gRPC endpoints:
  - `SearchCards(query, set_filter, rarity_filter)` → paginated results
  - `GetCardById(card_id)` → single card
  - `GetCardsByIds(card_ids)` → batch lookup
  - `ListSets()` → all available sets
- Re-sync (when new Pokemon sets release, ~quarterly) is deferred. Phase 1 ships the seed script only; the cron job or admin-gated `SyncCards` endpoint is tracked as a follow-up issue.

**Auth Service (Go + gRPC + Self-Managed JWT)**
- Registration: email + password. Password hashed with bcrypt.
- Login: validate credentials, issue signed JWT access token (short-lived, 15 min) + refresh token (long-lived, 7 days, stored in DB).
- Token validation: middleware-compatible endpoint that other services call to verify JWTs.
- Token refresh: exchange valid refresh token for new access token.
- Role assignment: vendor, attendee, organizer.
- Vendor profile: shop name, location (city/state), description.
- JWT implementation details:
  - RS256 signing (asymmetric — private key signs, public key verifies)
  - Claims: user_id, role, issued_at, expires_at, kid (key ID used for signing)
  - Public keys exposed via a JWKS-style gRPC endpoint so other services can validate tokens independently without calling Auth Service on every request
  - Key rotation: at any given time exactly one signing key is active for new tokens; previously-active keys remain in the JWKS response until their issued tokens expire. With a 15-minute access token + 7-day refresh token, a rotated key must remain published for the full 7-day overlap window before it can be removed.
- gRPC endpoints:
  - `Register(email, password, role)` → user_id
  - `Login(email, password)` → access_token, refresh_token
  - `RefreshToken(refresh_token)` → new access_token
  - `ValidateToken(access_token)` → user_id, role
  - `GetJWKS()` → array of `{kid, public_key, alg}` for independent validation
  - `GetVendorProfile(vendor_id)` → profile details
  - `UpdateProfile(vendor_id, fields)` → updated profile

**Docker Compose (Local Development)**
```yaml
services:
  postgres:       # Shared Postgres instance, separate databases per service
  redis:          # Shared Redis instance
  card-catalog:   # Card Catalog Service
  auth:           # Auth Service
```

The message broker (Redpanda, Kafka API-compatible, single-binary, no Zookeeper) is deferred to Phase 2 when the first producer/consumer services land. Adding it in Phase 1 would mean running a broker no service talks to.

### Database Schemas

**card_catalog_db.cards**
| Column | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| external_id | VARCHAR | TCGdex card ID (e.g., "sv03-100") |
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

**auth_db.signing_keys**
| Column | Type | Notes |
|---|---|---|
| id | UUID | Primary key, used as the JWT `kid` claim |
| public_key | TEXT | PEM-encoded RSA public key |
| private_key_encrypted | TEXT | PEM-encoded RSA private key, encrypted at rest |
| alg | VARCHAR | Always `RS256` for now |
| created_at | TIMESTAMP | |
| rotated_at | TIMESTAMP | Nullable — set when this key stops being the active signer |
| revoked_at | TIMESTAMP | Nullable — set when this key is removed from JWKS |

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
- gRPC endpoints: `AddInventory`, `BulkImportCSV`, `UpdateItem`, `RemoveItem`, `ListInventory`, `ListInventoryByEvent`, `SearchInventoryForEvent(event_id, card_id, filters)` (single-card lookup across all vendors at the event — caller must specify a card_id, no "list everything at this event" mode exists by design; returns vendor display name + booth + asking price + condition; callable by both vendors and authenticated attendees registered for the event; never exposes vendor contact info beyond display name and booth).

**Buy List Service (Go + gRPC + Kafka producer)**
- Vendors maintain a list of cards they want to acquire.
- Fields: card reference, minimum acceptable condition, max buy price, quantity wanted.
- Publishes `buylist.updated` events to Kafka.
- gRPC endpoints: `AddWantedCard`, `RemoveWantedCard`, `UpdateWantedCard`, `ListBuyList`, `ListBuyListsForEvent(event_id, filters)` (paginated card-level view across all vendors at the event, callable by both vendors and authenticated attendees registered for the event; returns vendor display name + booth + max-buy price + minimum acceptable condition; never exposes vendor contact info beyond display name and booth).

**Asymmetric visibility — "browse demand, query supply":** The two event-scoped read endpoints are deliberately asymmetric, enforcing the product's design principle by the same name. `ListBuyListsForEvent` is a **browseable** paginated list — callers can scroll through every card vendors want at this event. `SearchInventoryForEvent` is **query-only** — callers must specify a `card_id` and only ever get inventory matches for that one card. There is no "list all inventory at this event" endpoint, by design: a browseable vendor-inventory catalog would let attendees skip the convention floor entirely, which violates the product's core thesis. Both endpoints return vendor display name + booth only — never email, phone, address, or any other contact data. Attendee identity is never exposed to other attendees on either endpoint.

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

**Recompute is Kafka-driven only.** Overlap recomputation is triggered exclusively by `inventory.updated`, `buylist.updated`, and `event.vendor_registered` Kafka events. The gRPC endpoint `GetOverlapsForVendor` is a pure read from precomputed Redis sets and PostgreSQL — it never triggers recomputation. This guarantees read latency stays bounded regardless of inventory size.

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
- gRPC endpoints:
  - `GetOverlapsForVendor(vendor_id, event_id)` → list of opportunities with scores.
  - `SaveOverlap(vendor_id, overlap_id)` → marks an overlap as saved to a vendor's event plan. Pre-event action — re-surfaces as a notification at booth-setup time on event day.
  - `ListSavedOverlaps(vendor_id, event_id)` → vendor's saved-for-later list.

**Conversation State — all-can-bid, seller chooses**

When a vendor takes action on an overlap (Save to Event Plan pre-event, or Express Interest in-event), an "interest" record is created. **Interests are not locks.** Multiple vendors can register interest on the same single-copy overlap. The seller sees all interested vendors ranked by overlap score and chooses one or more to reveal booth/contact info to (see Offer Service in Phase 4 for `RevealBoothToVendor`). This mirrors the attendee offer flow — the supply-holder picks among the demand-holders, with no stale lock risk if a vendor abandons the app.

**Notification Service (Go + Kafka consumer + PostgreSQL)**
- Consumes `overlap.found` events.
- Stores notifications per vendor in PostgreSQL.
- Deduplicates: if the same overlap is found again (e.g., after an inventory update), update existing notification rather than creating a duplicate.
- Re-surfaces saved overlaps as notifications when the corresponding event reaches its start time (T-0).
- Honors per-vendor notification preferences: trigger toggles, channel toggles (in-app, email), per-event mutes, and digest mode (real-time / daily / event-only).
- gRPC endpoints: `GetNotifications(vendor_id)`, `MarkAsRead(notification_id)`, `GetUnreadCount(vendor_id)`, `GetNotificationPreferences(user_id)`, `UpdateNotificationPreferences(user_id, fields)`.
- Future: WebSocket for real-time push, push notifications. Deferred.

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

### Phase 3 Database Schemas

**notification_db.notifications**
| Column | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| vendor_id | UUID | Recipient |
| event_id | UUID | Event the notification is scoped to (nullable for global notifications) |
| trigger_type | VARCHAR | overlap_buylist / overlap_liquidate / attendee_listing_match / price_drop / event_reminder / saved_overlap_active |
| payload | JSONB | Trigger-specific data (overlap_id, card_id, vendor_id of counterparty, etc.) |
| read | BOOLEAN | Default false |
| created_at | TIMESTAMP | |

**notification_db.notification_preferences**
| Column | Type | Notes |
|---|---|---|
| user_id | UUID | Primary key |
| channels | JSONB | `{"in_app": true, "email": true}` |
| triggers | JSONB | Per-trigger toggle map, e.g. `{"overlap_buylist": true, "price_drop": false, ...}` |
| digest_mode | VARCHAR | real_time / daily / event_only |
| event_mutes | JSONB | Array of event_ids the user has muted |
| updated_at | TIMESTAMP | |

**notification_db.overlap_interests**
| Column | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| overlap_id | UUID | Identifier of the overlap (composite of event_id + buyer_vendor_id + seller_vendor_id + card_id) |
| interested_vendor_id | UUID | The vendor who clicked Save / Express Interest |
| counterparty_vendor_id | UUID | The vendor on the other side of the overlap |
| event_id | UUID | |
| score | DECIMAL | Snapshot of the overlap score at time of interest |
| status | VARCHAR | pending / revealed / declined / expired |
| created_at | TIMESTAMP | |
| updated_at | TIMESTAMP | |

**notification_db.saved_overlaps**
| Column | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| vendor_id | UUID | The vendor who saved the overlap |
| overlap_id | UUID | Identifier of the overlap |
| event_id | UUID | |
| activated_at | TIMESTAMP | Nullable — set when the event reaches T-0 and the saved overlap becomes an active notification |
| created_at | TIMESTAMP | |

---

## Phase 4 — API Gateway & Vendor Frontend

**Goal:** Make the vendor-vendor product usable end-to-end. Ship a real web app vendors can sign up for, import inventory into, register for events, and act on overlaps in. **This is the validation phase** — if vendor-vendor doesn't earn its keep with real users, attendee flow doesn't ship.

**What you learn:** REST-to-gRPC translation patterns, API gateway design, JWT middleware with JWKS, rate limiting, Next.js architecture, mobile-first responsive design, iterating on UX with real users.

### Deliverables

**API Gateway (Go)**
- Single REST entry point that translates HTTP requests to gRPC calls to backend services.
- JWT validation middleware that calls `GetJWKS()` on the Auth Service and picks the right public key based on the token's `kid` claim.
- Rate limiting via Redis (token bucket or sliding window).
- Request logging and error normalization.
- Route structure (Phase 4 — vendor-only; attendee routes added in Phase 5):
  - `POST /api/v1/auth/register`, `/login`, `/refresh`
  - `GET /api/v1/cards/search`, `/cards/:id`, `/sets`
  - `GET/POST /api/v1/inventory`, `POST /api/v1/inventory/import`
  - `GET /api/v1/inventory/event/:event_id/search?card_id=…` (query-only; no list-all)
  - `GET/POST /api/v1/buylist`, `GET /api/v1/buylist/event/:event_id` (browseable)
  - `GET/POST /api/v1/events`, `POST /api/v1/events/:id/register`
  - `GET /api/v1/overlaps/:event_id`, `POST /api/v1/overlaps/:id/save`
  - `GET /api/v1/notifications`, `GET/PATCH /api/v1/notifications/preferences`
  - `GET/PATCH /api/v1/profile`

**Vendor Frontend (Next.js on Vercel)**
- Mobile-first responsive design — conventions are a phone-in-hand context. Both desktop and mobile vendor layouts ship in this phase.
- **Vendor signup + profile setup:** email, password, shop name, display handle, city/state. Onboarding pitches "register for an event" and "import inventory" as the two primary first actions.
- **Vendor dashboard:** stat tiles, opportunities panel, upcoming events, notification feed; event-scoped views.
- **Inventory manager:** CRUD UI + the four-step CSV import flow (file picker → fuzzy-match resolution → preview → commit).
- **Buy list manager:** CRUD interface.
- **Events:** browse upcoming events, register/unregister, view per-event vendor roster + counts.
- **Overlap detail:** event-scoped view with the all-can-bid conversation state. Pre-event CTA is "Save to Event Plan"; in-event CTA is "Reveal Booth & Contact" via the Offer Service's `RevealBoothToVendor` endpoint.
- **Notifications:** feed view and preferences page (channels, triggers, digest mode, per-event mutes).
- **No attendee flow yet.** No anonymous listings, no offer submission, no attendee mobile screens. Those land in Phase 5.
- Hosted on Vercel free tier (Hobby plan).

### Soft-Launch Validation Goals

End of Phase 4 is a real soft-launch with a handful of vendors before any attendee complexity is built. Concrete bars to clear before opening Phase 5:

- **≥ 5 real vendors actively using the platform** (not just signed up).
- **≥ 1 convention's worth of inventory imported** via CSV.
- **≥ 1 overlap surfaced that resulted in a real in-person meeting at a booth.**
- **Qualitative feedback** from those vendors: is overlap-driven coordination meaningfully faster and more reliable than the current Instagram/Discord/booth-walk status quo?

If those bars are not cleared, the right call is to iterate on Phase 4 (or revisit the product thesis) rather than continue to Phase 5.

---

## Phase 5 — Attendee Backend & Frontend

**Goal:** Add the attendee flow on top of the validated vendor product. Let convention attendees post anonymous temporary listings that vendors can discover and bid on, and let them browse/search vendor-side data at events they're registered for. Build the attendee mobile UI.

**What you learn:** Anonymity and privacy patterns, temporary data lifecycle management, offer state machines, Kafka-driven workflows, mobile-web patterns for a non-power-user audience.

### Attendee Backend Deliverables

**Attendee role added to Auth Service**
- Lightweight registration (display name, email, password — no shop profile).
- Role assignment as `attendee`.
- JWT tokens follow the same RS256/JWKS pattern as vendor tokens.

**Attendee Listing Flow (owned end-to-end by the Offer Service)**
- Attendees register for an event and enable "event seller mode."
- Upload cards: card reference, condition, rough price expectation, preference (cash / trade / both).
- Listings are anonymous — vendors see card details, condition, and price expectation but not who posted them.
- Listings are temporary: auto-expire when the event ends (background job or Kafka-driven TTL).
- Listings are only visible to verified vendors registered at the same event.

The Offer Service owns attendee listings end-to-end — creation, query, expiration, identity reveal — so the `attendee_id ↔ listing_id` mapping never leaves a single service boundary. The Inventory Service is exclusively for vendor inventory and has no knowledge of attendee listings.

**Offer Service (Go + gRPC + Kafka)**
- Owns the `attendee_listings` schema and the full lifecycle of an anonymous listing.
- Vendors submit interest signals or preliminary price ranges on anonymous listings.
- Offer states: `pending` → `viewed` → `accepted` / `declined` / `expired`.
- Attendee receives aggregated interest: "3 vendors interested, offers ranging $40–$48."
- Attendee accepts an offer, which triggers identity reveal (first name + general location).
- Vendor-to-vendor booth reveal: when a seller chooses an interested vendor from the Phase 3 overlap conversation state, the Offer Service exposes booth + on-floor contact handle to both parties and logs a reveal event for audit. (The endpoint exists from Phase 5 forward; in Phase 4 the vendor frontend hits a stub that returns booth + contact directly from vendor profiles.)
- Publishes `listing.created`, `listing.expired`, `offer.submitted`, `offer.viewed`, `offer.accepted`, `offer.declined`, `booth.revealed` to Kafka.
- gRPC endpoints: `CreateAttendeeListing`, `ListAttendeeListingsForEvent`, `ExpireListing`, `SubmitOffer`, `ListOffersForListing`, `ListOffersForVendor`, `AcceptOffer`, `DeclineOffer`, `RevealIdentity`, `RevealBoothToVendor(event_id, requesting_vendor_id, receiving_vendor_id)`.

**Privacy Rules (Enforced at Service Level)**
- Attendee user_id is never included in any response to vendors until `AcceptOffer` is called.
- Listing queries for vendors return a listing_id but no user information.
- The Offer Service is the only service that handles identity reveal, and only on explicit attendee action.
- Listings cannot be queried outside of their event scope.
- Attendees cannot read other attendees' listings — no cross-attendee endpoint exists.

### Database Schemas

**offer_db.attendee_listings** (owned by Offer Service)
| Column | Type | Notes |
|---|---|---|
| id | UUID | Primary key (this is the anonymous listing_id) |
| attendee_id | UUID | Owner — never exposed to vendors. Lives inside the Offer Service boundary so the attendee↔listing mapping cannot leak to other services. |
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

### Attendee Frontend Deliverables

Attendee web app (mobile-first; this is the audience that's actually on a phone at a card show):

- **Signup / login.**
- **Event home:** event header (name, dates, venue), three counters (vendors, cards, offers), three primary actions: Browse the Event, Post Cards to Sell, My Listings.
- **Browse the Event:** segmented view with two tabs:
  - **"Vendors want" tab** — browseable list of buy-list entries at this event, grouped by card. Shows max-buy prices and which vendors are interested.
  - **"Find a card" tab** — search-only. The attendee types a card name; the app returns which vendors at this event have it and at what asking price. **There is no "show all inventory" list view — by design.** This is the UI enforcement of the "browse demand, query supply" principle.
- **Post a Card:** form for anonymous listing creation. Reinforces "your identity is hidden until you accept an offer."
- **My Listings & Offers:** per-listing view of incoming offers, aggregated interest summary.
- **Offer Detail / Accept:** explicit identity-reveal confirmation screen ("This cannot be undone").
- Reuses existing pen-file components: Status Bar, Tab Bar.

### API Gateway Route Extensions

Routes added to the API Gateway in this phase:

- `GET/POST /api/v1/listings` (attendee creates, attendee + vendor read with privacy rules)
- `GET/POST /api/v1/offers`
- `POST /api/v1/offers/:id/accept` (triggers identity reveal)
- Rate limiting updated for the higher request volume of mobile attendee browsing.

---

## Phase 6 — Kubernetes Deployment, Observability & Organizer UX

**Goal:** Deploy the full system on Kubernetes with proper observability. Deepen infrastructure and operations skills. Build the organizer-facing web UI that was deferred from Phase 5.

**What you learn:** Kubernetes manifests and Helm charts, resource management, distributed logging and tracing, horizontal scaling, CI/CD deployment pipelines.

### Organizer UX Deliverables (deferred from Phase 5)

- Organizer signup + role assignment.
- Event creation form: name, location (venue + city + state), date range, description.
- Vendor roster view per event: who's registered, when they registered.
- Basic aggregate stats per event: number of vendors, number of inventory items at the event, number of overlaps detected, number of attendee listings posted.
- These views consume the existing Event Service gRPC endpoints from Phase 2; no new backend services are required.

### Infrastructure Deliverables

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
| TCGdex card data | Free |
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
| 1 | Card Catalog (TCGdex seed), Auth (JWT/JWKS), foundational tooling (Makefile, docker-compose, real CI) | Go, gRPC, Protobuf, Docker, PostgreSQL, Redis, JWT/RSA |
| 2 | Inventory, Buy Lists, Events, Kafka | Go, Kafka, CSV ingestion, event-driven architecture |
| 3 | Overlap Detection, Notifications | Redis set operations, Kafka consumers, scoring algorithms |
| 4 | **API Gateway + Vendor Frontend** (vendor-vendor product goes live; soft-launch validation gate) | Next.js, REST-to-gRPC, Vercel, mobile-first design |
| 5 | **Attendee Backend + Frontend** (anonymous listings, offers, attendee mobile flow) | Privacy patterns, state machines, data lifecycle, mobile-web |
| 6 | Kubernetes & Observability + Organizer UX | K8s, Helm, OpenTelemetry, Jaeger, Loki, Prometheus, organizer dashboard + event creation form |
| 7 | Intelligence Layer | Analytics, trend detection, pricing data |
