import { cookies } from "next/headers";
import { z } from "zod";
import { callGateway, parseTokenPair, relay, sameOrigin, setSession } from "@/lib/gateway";

const registerSchema = z.object({
  email: z.email().max(320),
  password: z.string().min(8).max(128),
  shopName: z.string().trim().min(1).max(120),
  city: z.string().trim().max(120),
  state: z.string().trim().max(64),
});

export async function POST(request: Request) {
  if (!sameOrigin(request)) {
    return Response.json({ code: "CROSS_ORIGIN_REQUEST", message: "Request origin was rejected." }, { status: 403 });
  }
  const parsed = registerSchema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) {
    return Response.json(
      { code: "INVALID_FORM", message: "Complete the shop details and use a password of at least 8 characters." },
      { status: 400 },
    );
  }

  const registered = await callGateway("/api/v1/auth/register", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(parsed.data),
  });
  if (!registered.ok) return relay(registered);

  const login = await callGateway("/api/v1/auth/login", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ email: parsed.data.email, password: parsed.data.password }),
  });
  const tokens = await parseTokenPair(login);
  if (!tokens) return relay(login);

  setSession(await cookies(), tokens);
  return Response.json({ authenticated: true }, { status: 201 });
}
