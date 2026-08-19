import { describe, expect, it } from "vitest";

import { sameOrigin } from "@/lib/gateway";
import { validProxyPath } from "@/lib/proxy-policy";

describe("BFF proxy policy", () => {
  it("allows only explicit product roots with safe path segments", () => {
    expect(validProxyPath(["inventory", "item-123"])).toBe(true);
    expect(validProxyPath(["events", "event_42", "vendors"])).toBe(true);
    expect(validProxyPath(["auth", "login"])).toBe(false);
    expect(validProxyPath(["inventory", ".."])).toBe(false);
    expect(validProxyPath(["cards", "encoded/value"])).toBe(false);
    expect(validProxyPath([])).toBe(false);
  });

  it("rejects cross-origin state changes while allowing safe reads", () => {
    const target = "https://app.vendex.example/api/proxy/inventory";

    expect(sameOrigin(new Request(target, {
      method: "POST",
      headers: { origin: "https://malicious.example" },
    }))).toBe(false);
    expect(sameOrigin(new Request(target, {
      method: "POST",
      headers: { origin: "https://app.vendex.example" },
    }))).toBe(true);
    expect(sameOrigin(new Request(target, {
      method: "GET",
      headers: { origin: "https://malicious.example" },
    }))).toBe(true);
  });
});
