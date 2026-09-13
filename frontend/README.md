# VenDex frontend

The vendor web app is a Next.js 16 App Router project. It uses a small backend-for-frontend boundary so gateway access and refresh tokens remain in server-managed `HttpOnly` cookies instead of browser storage.

## Local development

1. Copy `.env.example` to `.env.local` and set `GATEWAY_BASE_URL=http://localhost:8081` for the Compose gateway. Port 8080 hosts the Redpanda console.
2. Install locked dependencies with `npm ci` (Node.js 24).
3. Start the app with `npm run dev`.

The frontend imports the repository-level `ui/tokens.css` file directly; do not duplicate or hardcode the product palette in component styles.

## Validation

- `npm run lint`
- `npm test`
- `npm run build`
