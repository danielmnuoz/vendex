import { afterEach, describe, expect, it, vi } from "vitest";

import { dateRange, hydrateCards } from "@/lib/client-data";
import type { CardSummary } from "@/lib/contracts";

afterEach(() => {
  vi.restoreAllMocks();
});

describe("dateRange", () => {
  it.each([
    ["2026-10-17", "2026-10-18", "Oct 17–18, 2026"],
    ["2026-10-31", "2026-11-01", "Oct 31–Nov 1, 2026"],
    ["2026-12-31", "2027-01-01", "Dec 31, 2026–Jan 1, 2027"],
    ["2026-10-17", "2026-10-17", "Oct 17, 2026"],
    ["invalid", "2026-10-18", "invalid – 2026-10-18"],
  ])("formats %s through %s for event displays", (start, end, expected) => {
    expect(dateRange(start, end)).toBe(expected);
  });
});

describe("hydrateCards", () => {
  it("deduplicates ids, batches at the gateway limit, and indexes responses", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(async (_input, init) => {
      const ids = JSON.parse(String(init?.body)).cardIds as string[];
      return new Response(
        JSON.stringify({
          cards: ids.map(
            (id): CardSummary => ({
              id,
              externalId: id,
              name: `Card ${id}`,
              setId: "set-1",
              setName: "Test Set",
              setSeries: "Test Series",
              rarity: "rare",
              imageUrl: "",
              imageUrlLarge: "",
              releaseDate: "2026-01-01",
            }),
          ),
        }),
        { status: 200, headers: { "content-type": "application/json" } },
      );
    });
    const ids = Array.from({ length: 101 }, (_, index) => `card-${index}`);

    const cards = await hydrateCards([...ids, "card-0", ""]);

    expect(fetchMock).toHaveBeenCalledTimes(2);
    const firstBody = JSON.parse(String(fetchMock.mock.calls[0][1]?.body));
    const secondBody = JSON.parse(String(fetchMock.mock.calls[1][1]?.body));
    expect(firstBody.cardIds).toHaveLength(100);
    expect(secondBody.cardIds).toEqual(["card-100"]);
    expect(cards).toHaveLength(101);
    expect(cards.get("card-100")?.name).toBe("Card card-100");
  });

  it("avoids a gateway request when no ids need hydration", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch");

    await expect(hydrateCards(["", ""])).resolves.toEqual(new Map());
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
