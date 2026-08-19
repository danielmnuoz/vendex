import type { NextRequest } from "next/server";
import { NextResponse } from "next/server";
import { ACCESS_COOKIE, REFRESH_COOKIE } from "@/lib/gateway";

const protectedPrefixes = [
  "/dashboard",
  "/inventory",
  "/buy-list",
  "/events",
  "/overlaps",
  "/notifications",
  "/settings",
  "/profile",
];

export function proxy(request: NextRequest) {
  const path = request.nextUrl.pathname;
  const hasSession = request.cookies.has(ACCESS_COOKIE) || request.cookies.has(REFRESH_COOKIE);
  const protectedRoute = protectedPrefixes.some((prefix) => path === prefix || path.startsWith(`${prefix}/`));

  if (protectedRoute && !hasSession) {
    const login = new URL("/login", request.url);
    login.searchParams.set("next", path);
    return NextResponse.redirect(login);
  }
  if ((path === "/login" || path === "/signup") && hasSession) {
    return NextResponse.redirect(new URL("/dashboard", request.url));
  }
  return NextResponse.next();
}

export const config = {
  matcher: [
    "/dashboard/:path*",
    "/inventory/:path*",
    "/buy-list/:path*",
    "/events/:path*",
    "/overlaps/:path*",
    "/notifications/:path*",
    "/settings/:path*",
    "/profile/:path*",
    "/login",
    "/signup",
  ],
};
