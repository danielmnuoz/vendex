# Phase 2 plumbing: from synchronous services to durable domain events

PR #10 is the bridge between VenDex's Phase 1 request/response services and the event-driven services planned for Phases 2 and 3. It does not add a user-facing service. It adds the shared infrastructure that lets later services persist a business change and reliably announce that change to Kafka-compatible consumers.

## Cumulative system view

**Legend:** blue is the system already present on `main`; orange is added by this increment; gray is planned but not built yet.

```mermaid
flowchart LR
    User[Vendor or attendee]
    Auth[Auth Service]
    Catalog[Card Catalog Service]
    Postgres[(PostgreSQL)]
    Redis[(Redis)]

    Contracts[Shared event contracts]
    Outbox[Transactional outbox library]
    Broker[Redpanda / Kafka API]
    Console[Redpanda Console]
    EventSvc[Event Service]
    InventorySvc[Inventory Service]
    BuyListSvc[Buy List Service]
    MatchSvc[Overlap Detection]

    User --> Auth
    User --> Catalog
    Auth --> Postgres
    Catalog --> Postgres
    Catalog --> Redis

    EventSvc -. depends on .-> Contracts
    InventorySvc -. depends on .-> Contracts
    BuyListSvc -. depends on .-> Contracts
    EventSvc -. writes through .-> Outbox
    InventorySvc -. writes through .-> Outbox
    BuyListSvc -. writes through .-> Outbox
    Outbox -->|publish after commit| Broker
    Broker --> Console
    Broker -. future events .-> MatchSvc

    classDef current fill:#4C78A8,stroke:#2F4B6C,color:#fff;
    classDef added fill:#F28E2B,stroke:#9A5200,color:#fff;
    classDef future fill:#B0B7C3,stroke:#68717D,color:#1F2933;
    class User,Auth,Catalog,Postgres,Redis current;
    class Contracts,Outbox,Broker,Console added;
    class EventSvc,InventorySvc,BuyListSvc,MatchSvc future;
```

The dashed connections show intended consumers of this plumbing. Those services are deliberately gray because their code is not part of PR #10.

## Major entities introduced

| Entity | Kind | Responsibility |
|---|---|---|
| `events` | Maven module | Gives producers and consumers one compiled definition of topic names and JSON payloads. |
| `InventoryUpdated`, `BuyListUpdated`, `EventCreated`, `EventVendorRegistered` | Event contracts | Define the initial Phase 2 facts placed on the broker. |
| `OutboxWriter` | Shared library class | Serializes a domain event and inserts it into the service's database transaction. |
| `OutboxRepository` | Shared library class | Persists, polls, and marks outbox records as published. |
| `OutboxRelay` | Scheduled worker | Publishes committed outbox records to Redpanda with at-least-once delivery. |
| `outbox` | PostgreSQL table | Stores pending event payloads durably beside each service's business data. |
| Redpanda | Kafka-compatible broker | Carries domain events from Phase 2 producers to Phase 3 consumers. |
| Redpanda Console | Development UI | Makes local topics and JSON payloads inspectable. |

## Concrete data flow

The future Inventory Service will use the shared plumbing like this:

1. A vendor changes an inventory item.
2. One database transaction updates `inventory_items` and calls `OutboxWriter`.
3. `OutboxWriter` inserts an `inventory.updated` JSON record into the same database's `outbox` table.
4. The transaction commits both records together. If it rolls back, neither record exists.
5. `OutboxRelay` polls the committed row, publishes it to Redpanda, waits for acknowledgement, and sets `published_at`.
6. A Phase 3 overlap consumer may receive the event more than once, so it must process events idempotently.

```mermaid
sequenceDiagram
    participant V as Vendor
    participant I as Future Inventory Service
    participant DB as inventory_db
    participant R as OutboxRelay
    participant K as Redpanda
    participant O as Future Overlap Service

    V->>I: Update inventory item
    I->>DB: BEGIN
    I->>DB: UPDATE inventory_items
    I->>DB: INSERT outbox event
    I->>DB: COMMIT
    R->>DB: Fetch unpublished rows
    R->>K: inventory.updated
    K-->>R: Broker acknowledgement
    R->>DB: Set published_at
    K-->>O: inventory.updated (at least once)
```

## Why the outbox exists

Publishing directly after a database commit creates a dual-write gap: the database can succeed while the broker call fails, leaving downstream matching permanently unaware of the change. Publishing before commit has the opposite problem: consumers can observe a change that later rolls back. The outbox makes the business record and the intent to publish atomic by storing both in one PostgreSQL transaction.

The tradeoff is operational complexity and at-least-once delivery. The polling relay adds latency, published rows need a future retention policy, and every consumer must tolerate duplicates. For VenDex, that cost is justified because overlap detection depends on not silently losing inventory or buy-list changes.

## What remains after this increment

The Event, Inventory, and Buy List services still need their own schemas, gRPC APIs, validation, and producer integration. The first producer PR should add a Testcontainers round-trip test proving `business write -> outbox -> Redpanda`. Phase 3 then adds idempotent consumers and the actual overlap engine.
