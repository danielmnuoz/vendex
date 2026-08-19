import { cookies } from "next/headers";
import { z } from "zod";
import { callGateway, parseTokenPair, relay, sameOrigin, setSession } from "@/lib/gateway";

const loginSchema = z.object({
  email: z.email().max(320),
  password: z.string().min(1).max(128),
});

export async function POST(request: Request) {
  if (!sameOrigin(request)) {
    return Response.json({ code: "CROSS_ORIGIN_REQUEST", message: "Request origin was rejected." }, { status: 403 });
  }
  const parsed = loginSchema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) {
    return Response.json({ code: "INVALID_FORM", message: "Enter a valid email and password." }, { status: 400 });
  }

  const response = await callGateway("/api/v1/auth/login", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(parsed.data),
  });
  const tokens = await parseTokenPair(response);
  if (!tokens) return relay(response);

  setSession(await cookies(), tokens);
  return Response.json({ authenticated: true });
}
