import { cookies } from "next/headers";
import { clearSession, sameOrigin } from "@/lib/gateway";

export async function POST(request: Request) {
  if (!sameOrigin(request)) {
    return Response.json({ code: "CROSS_ORIGIN_REQUEST", message: "Request origin was rejected." }, { status: 403 });
  }
  clearSession(await cookies());
  return new Response(null, { status: 204 });
}
