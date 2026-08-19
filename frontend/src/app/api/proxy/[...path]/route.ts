import { cookies } from "next/headers";
import { callGateway, ACCESS_COOKIE, clearSession, refreshSession, relay, sameOrigin } from "@/lib/gateway";
import { validProxyPath } from "@/lib/proxy-policy";

async function forward(request: Request, segments: string[], accessToken: string) {
  const incoming = new URL(request.url);
  const path = `/api/v1/${segments.join("/")}${incoming.search}`;
  const headers = new Headers({
    accept: request.headers.get("accept") ?? "application/json",
    authorization: `Bearer ${accessToken}`,
  });
  const contentType = request.headers.get("content-type");
  if (contentType) headers.set("content-type", contentType);
  const body = ["GET", "HEAD"].includes(request.method) ? undefined : await request.arrayBuffer();
  return callGateway(path, {
    method: request.method,
    headers,
    body: body && body.byteLength > 0 ? body : undefined,
  });
}

async function handle(request: Request, context: RouteContext<"/api/proxy/[...path]">) {
  if (!sameOrigin(request)) {
    return Response.json({ code: "CROSS_ORIGIN_REQUEST", message: "Request origin was rejected." }, { status: 403 });
  }
  const { path } = await context.params;
  if (!validProxyPath(path)) {
    return Response.json({ code: "PROXY_ROUTE_REJECTED", message: "This gateway route is not exposed." }, { status: 404 });
  }

  const store = await cookies();
  let accessToken = store.get(ACCESS_COOKIE)?.value;
  let refreshed = false;
  if (!accessToken) {
    const tokens = await refreshSession(store);
    accessToken = tokens?.accessToken;
    refreshed = Boolean(tokens);
  }
  if (!accessToken) {
    return Response.json({ code: "AUTHENTICATION_REQUIRED", message: "Sign in to continue." }, { status: 401 });
  }

  let response = await forward(request.clone(), path, accessToken);
  if (response.status === 401 && !refreshed) {
    const tokens = await refreshSession(store);
    if (tokens) response = await forward(request.clone(), path, tokens.accessToken);
  }
  if (response.status === 401) clearSession(store);
  return relay(response);
}

export const GET = handle;
export const POST = handle;
export const PUT = handle;
export const PATCH = handle;
export const DELETE = handle;
