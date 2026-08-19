# VenDex frontend

The vendor web app is a Next.js 16 App Router project. It uses a small backend-for-frontend boundary so gateway access and refresh tokens remain in server-managed `HttpOnly` cookies instead of browser storage.

## Local development

1. Copy `.env.example` to `.env.local` if the gateway is not available at `http://localhost:8080`.
2. Install dependencies with `npm install`.
3. Start the app with `npm run dev`.

The frontend imports the repository-level `ui/tokens.css` file directly; do not duplicate or hardcode the product palette in component styles.

## Validation

- `npm run lint`
- `npm run build`
