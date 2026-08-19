# Card Catalog scheduled sync: keeping a local source of truth current

The Phase 1 Card Catalog copied TCGdex into PostgreSQL through an explicit seed
profile. That removed an external dependency from every product request, but it also
made new sets an operator task. This increment keeps the same local-first read path
and makes refresh automatic, transactional, observable, and safe across replicas.

TCGdex's current API is REST V2, read-only, and served over HTTPS. Its set-list
response represents `cardCount` as an object rather than a number, so the local DTO
now follows that contract before automated production traffic begins. See the
[REST overview](https://tcgdex.dev/rest), [sets endpoint](https://tcgdex.dev/rest/sets),
and [Set schema](https://tcgdex.dev/reference/set).

## Cumulative system view

**Legend:** blue is already on `main`; orange is added or materially changed by
this increment; gray is explicitly future.

```mermaid
flowchart LR
    TCGdex[TCGdex REST V2]
    Seed[One-off seed profile]
    Scheduler[Spring fixed-delay scheduler]
    Sync[CatalogSyncService]
    Lease[(catalog_sync_state<br/>lease + run outcome)]
    Snapshot[Validated in-memory snapshot]
    Cards[(card_catalog_db.cards)]
    Catalog[Card Catalog gRPC reads]
    Redis[(Redis card cache)]
    Gateway[API Gateway]
    Frontend[Vendor application]
    Trigger[Admin-gated manual trigger]
    Alerts[Production sync alerts / dashboard]

    Seed -->|operator run| TCGdex
    Seed -->|idempotent upsert| Cards
    Scheduler --> Sync
    Sync -->|acquire crash-expiring lease| Lease
    Sync -->|bounded HTTPS reads| TCGdex
    TCGdex --> Snapshot
    Snapshot -->|one transaction| Cards
    Sync -->|success or failure| Lease
    Cards --> Catalog
    Catalog --> Redis
    Catalog --> Gateway
    Gateway --> Frontend
    Trigger -. explicit refresh .-> Sync
    Lease -. metrics / alert source .-> Alerts

    classDef current fill:#4C78A8,stroke:#2F4B6C,color:#fff;
    classDef added fill:#F28E2B,stroke:#9A5200,color:#fff;
    classDef future fill:#B0B7C3,stroke:#68717D,color:#1F2933;
    class TCGdex,Seed,Cards,Catalog,Redis,Gateway,Frontend current;
    class Scheduler,Sync,Lease,Snapshot added;
    class Trigger,Alerts future;
```

The orange flow is entirely outside the user request path. Search, detail, batch
hydration, and set-list reads still use the existing blue PostgreSQL/Redis path.
Refresh therefore improves freshness without turning TCGdex availability into
product availability.

## Refresh and failure flow

```mermaid
sequenceDiagram
    participant S as Scheduled replica
    participant L as PostgreSQL sync state
    participant T as TCGdex
    participant C as PostgreSQL cards

    S->>L: acquire lease if absent or expired
    alt another replica owns the lease
        L-->>S: no owner token
        S-->>S: skip this interval
    else lease acquired
        L-->>S: unique owner token
        S->>T: GET series, sets, then every set detail
        alt timeout, invalid response, missing set, or empty snapshot
            T-->>S: failure
            S->>L: record failure and release lease
            S-->>S: emit error with stack trace
        else complete snapshot
            T-->>S: all normalized card rows
            S->>C: transactional UPSERT of every row
            C-->>S: commit
            S->>L: record count/success and release lease
        end
    end
```

The service deliberately downloads and validates the complete snapshot before
writing. A missing set response, blank required ID/name, timeout, database failure,
or empty snapshot aborts the run. `CardRepository.upsertAll` is transactional, so a
bad row rolls back every write from that refresh. The failure is then stored in
`catalog_sync_state` and logged at error level; the next interval can retry safely.

The lease is stored in the same service-owned database rather than memory. Only the
replica holding the random owner token may complete or fail a run. If that replica
crashes, the lease expires after the configured duration and another replica can
continue. The duration is intentionally longer than the HTTP-bounded refresh; it is
not a renewable distributed lock.

## Idempotence and cache behavior

Cards retain their canonical UUID because the upsert conflicts on TCGdex
`external_id` and updates mutable metadata in place. New cards are inserted; known
cards are refreshed. Cards removed upstream are not deleted automatically because
other services may already reference their canonical UUIDs.

Redis remains cache-aside with a one-hour default TTL. A refreshed card may therefore
serve its previous cached metadata until that bounded TTL expires; no downstream ID
or transactional guarantee depends on immediate cache invalidation.

## Major entities introduced or modified

| Entity | Kind | Change | Responsibility |
|---|---|---|---|
| `CatalogSyncService` | service | Added | Schedules refreshes, owns the lease workflow, validates non-empty snapshots, and records outcomes |
| `catalog_sync_state` | PostgreSQL table | Added | Coordinates replicas and preserves last-run operational evidence |
| `CatalogSyncStateRepository` | repository | Added | Atomically acquires owner-token leases and guards completion/failure transitions |
| `CardRepository.upsertAll` | repository operation | Modified | Commits a complete snapshot atomically or rolls it all back |
| `TcgDexClient` | external adapter | Modified | Uses bounded requests, current `cardCount` shape, and strict all-set failure semantics |
| `CardCatalogProperties.Sync` | configuration | Added | Controls enablement, initial delay, fixed delay, and lease duration |
| `SeedRunner` / `seed` profile | operator workflow | Modified | Remains available for explicit one-off runs while disabling the scheduler |

## Deliberate follow-ups

- An authenticated, admin-only manual trigger is not exposed yet. Scheduled refresh
  solves freshness without adding a new public trust boundary.
- The state table and error logs provide evidence, but production metrics, alerting,
  and a sync-status dashboard belong with the deployment/observability increment.
- Upstream deletions require an explicit retention policy because hard deletion can
  break canonical references in inventory, buy lists, overlaps, and notifications.
