import { cookies } from "next/headers";
import { ACCESS_COOKIE, REFRESH_COOKIE } from "@/lib/gateway";

export async function GET() {
  const store = await cookies();
  return Response.json({
    authenticated: store.has(ACCESS_COOKIE) || store.has(REFRESH_COOKIE),
  });
}
