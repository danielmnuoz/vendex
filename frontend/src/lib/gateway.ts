import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { z } from "zod";

export const ACCESS_COOKIE = "vendex_access";
export const REFRESH_COOKIE = "vendex_refresh";

const tokenPairSchema = z.object({
  accessToken: z.string().min(1),
  refreshToken: z.string().min(1),
  accessTokenExpiresAtEpochSeconds: z.number().int().positive(),
});

export type TokenPair = z.infer<typeof tokenPairSchema>;
type CookieStore = Awaited<ReturnType<typeof cookies>>;

const cookieBase = {
  httpOnly: true,
  secure: process.env.NODE_ENV === "production",
  sameSite: "lax" as const,
  path: "/",
  priority: "high" as const,
};

export function gatewayUrl(path: string) {
  const base = (process.env.GATEWAY_BASE_URL ?? "http://localhost:8080").replace(/\/$/, "");
  return `${base}${path}`;
}

export async function callGateway(path: string, init: RequestInit = {}) {
  try {
    return await fetch(gatewayUrl(path), {
      ...init,
      cache: "no-store",
      signal: AbortSignal.timeout(12_000),
    });
  } catch {
    return Response.json(
      {
        status: 503,
        code: "GATEWAY_UNAVAILABLE",
        message: "VenDex services are temporarily unavailable. Please try again.",
      },
      { status: 503 },
    );
  }
}

export async function parseTokenPair(response: Response) {
  if (!response.ok) return null;
  const payload: unknown = await response.clone().json().catch(() => null);
  const parsed = tokenPairSchema.safeParse(payload);
  return parsed.success ? parsed.data : null;
}

export function setSession(store: CookieStore, tokens: TokenPair) {
  store.set(ACCESS_COOKIE, tokens.accessToken, {
    ...cookieBase,
    expires: new Date(tokens.accessTokenExpiresAtEpochSeconds * 1000),
  });
  store.set(REFRESH_COOKIE, tokens.refreshToken, {
    ...cookieBase,
    maxAge: 60 * 60 * 24 * 7,
  });
}

export function clearSession(store: CookieStore) {
  store.delete(ACCESS_COOKIE);
  store.delete(REFRESH_COOKIE);
}

export async function refreshSession(store: CookieStore) {
  const refreshToken = store.get(REFRESH_COOKIE)?.value;
  if (!refreshToken) return null;

  const response = await callGateway("/api/v1/auth/refresh", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ refreshToken }),
  });
  const tokens = await parseTokenPair(response);
  if (!tokens) {
    clearSession(store);
    return null;
  }
  setSession(store, tokens);
  return tokens;
}

export function relay(response: Response) {
  const headers = new Headers();
  const contentType = response.headers.get("content-type");
  const requestId = response.headers.get("x-request-id");
  if (contentType) headers.set("content-type", contentType);
  if (requestId) headers.set("x-request-id", requestId);
  headers.set("cache-control", "no-store");
  return new NextResponse(response.body, { status: response.status, headers });
}

export function sameOrigin(request: Request) {
  if (["GET", "HEAD", "OPTIONS"].includes(request.method)) return true;
  const origin = request.headers.get("origin");
  if (!origin) return true;
  return origin === new URL(request.url).origin;
}
