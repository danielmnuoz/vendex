# VenDex

VenDex helps Pokémon TCG vendors coordinate inventory around real conventions.
Vendors register for the same event, publish the cards they are bringing and the
cards they want, and receive private, event-scoped overlap opportunities before
the show. The goal is to replace repeated booth walks and fragmented Discord or
Instagram coordination with an actionable event plan—without becoming a global
inventory marketplace.

The repository is also a hands-on distributed-systems learning project. It uses
independently owned PostgreSQL schemas, gRPC service contracts, Kafka/Redpanda
events, Redis-backed matching and rate limiting, a REST API gateway, and a
responsive Next.js vendor application. Each increment is recorded under
`docs/learning/` so the architectural evolution remains reviewable after the
implementation moves faster.

## Current product surface

- Vendor registration, login, profile, and rotating server-managed sessions
- Automatically refreshed canonical Pokémon card catalog, search, and batched hydration
- Inventory CRUD and reviewed CSV import with fuzzy-match corrections
- Buy-list CRUD
- Event browse, vendor registration, and authorized roster views
- Event-scoped overlap discovery and saved event plans
- Notification feed and preferences
- Responsive desktop/mobile vendor UI based on `ui/vendex.pen`

Booth/contact reveal, attendee listings/offers, organizer UI, production
deployment, and the soft-launch validation cohort remain later increments.

## Run locally

Start the backend stack with Docker Compose, then run the Next.js app from
`frontend/`:

```bash
docker compose up --build
cd frontend
npm install
npm run dev
```

Backend validation is `mvn verify`. Frontend validation is `npm run lint`,
`npm test`, and `npm run build` from `frontend/`.
