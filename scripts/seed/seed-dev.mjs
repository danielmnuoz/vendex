#!/usr/bin/env node
/**
 * VenDex local demo seed.
 *
 * Seeds a realistic vendor pilot dataset against the running docker-compose
 * stack by going THROUGH the product surfaces rather than writing SQL:
 *
 *   - events ............ EventService gRPC CreateEvent (there is no organizer
 *                         REST route or UI yet, so grpcurl in a container)
 *   - vendors ........... POST /api/v1/auth/register + /auth/login
 *   - registrations ..... POST /api/v1/events/{id}/register
 *   - inventory ......... POST /api/v1/inventory
 *   - buy lists ......... POST /api/v1/buylist
 *
 * Going through the gateway matters: inventory/buy-list/registration writes
 * land in each service's transactional outbox -> Redpanda -> Overlap Engine,
 * so overlaps materialize exactly as they would for a real vendor. Raw SQL
 * inserts would bypass the outbox and produce no overlaps.
 *
 * Prereqs: `docker compose up -d` healthy, card catalog synced (>0 cards).
 * Usage:   node scripts/seed/seed-dev.mjs
 *
 * Re-running is safe-ish: existing vendors are logged into instead of
 * re-registered, existing events are reused by name, and a vendor that
 * already has inventory is skipped entirely (no duplicate stock/demand).
 * For a clean slate: `docker compose down -v && docker compose up -d`.
 */
import { execFileSync } from "node:child_process";

const GATEWAY = process.env.GATEWAY_BASE_URL ?? "http://localhost:8081";
const EVENT_GRPC = process.env.EVENT_GRPC_TARGET ?? "host.docker.internal:9092";
const PASSWORD = "VendexDemo1!";
const ORGANIZER_ID = "00000000-0000-4000-8000-000000000001";

// ---------------------------------------------------------------- data ----

const EVENTS = [
  {
    key: "lonestar",
    name: "Lone Star Card Expo",
    city: "Dallas", state: "TX", venue: "Dallas Market Hall",
    start_date: "2026-10-17", end_date: "2026-10-18",
    description: "Two-day Pokémon TCG vendor hall with 80+ booths. Vendor load-in Friday evening.",
  },
  {
    key: "pnw",
    name: "Pacific Northwest Collectors Fair",
    city: "Portland", state: "OR", venue: "Oregon Convention Center",
    start_date: "2026-11-07", end_date: "2026-11-08",
    description: "Regional collectibles fair; Pokémon TCG row in Hall C.",
  },
];

const VENDORS = [
  { key: "ash",    email: "ash.ketchum@vendex.local",  shopName: "Pallet Town Cards",      city: "Dallas",        state: "TX", booths: { lonestar: "A-12", pnw: "C-07" } },
  { key: "misty",  email: "misty@vendex.local",        shopName: "Cerulean Gym Singles",   city: "Austin",        state: "TX", booths: { lonestar: "B-04", pnw: "C-11" } },
  { key: "brock",  email: "brock@vendex.local",        shopName: "Pewter City Vault",      city: "Houston",       state: "TX", booths: { lonestar: "C-21" } },
  { key: "gary",   email: "gary.oak@vendex.local",     shopName: "Oak Labs Collectibles",  city: "Oklahoma City", state: "OK", booths: { lonestar: "A-15" } },
  { key: "jessie", email: "jessie.james@vendex.local", shopName: "Team Rocket Trading Co", city: "Tulsa",         state: "OK", booths: { lonestar: "D-02" } },
];

// label -> { query, setId?, exact? } resolved against the live catalog.
const CARDS = {
  CHARIZARD_BASE: { query: "Charizard",     setId: "base1" },
  BLASTOISE_BASE: { query: "Blastoise",     setId: "base1" },
  VENUSAUR_BASE:  { query: "Venusaur",      setId: "base1" },
  PIKACHU_BASE:   { query: "Pikachu",       setId: "base1" },
  MEWTWO_BASE:    { query: "Mewtwo",        setId: "base1" },
  LUGIA_NEO:      { query: "Lugia",         setId: "neo1"  },
  UMBREON_ES:     { query: "Umbreon VMAX",  setId: "swsh7" },
  RAYQUAZA_ES:    { query: "Rayquaza VMAX", setId: "swsh7" },
  CHARIZARD_EVO:  { query: "Charizard",     setId: "xy12"  },
  MEW_EVO:        { query: "Mew",           setId: "xy12"  },
  GENGAR:         { query: "Gengar" },
  SNORLAX:        { query: "Snorlax" },
  EEVEE:          { query: "Eevee" },
  DRAGONITE:      { query: "Dragonite" },
  GYARADOS:       { query: "Gyarados" },
};

// [card, condition, quantity, askingPrice, priority, eventKey?]
const INVENTORY = {
  ash: [
    ["CHARIZARD_BASE", "lp", 1,  "450.00", "normal"],
    ["PIKACHU_BASE",   "nm", 12, "8.00",   "liquidate"],
    ["BLASTOISE_BASE", "mp", 1,  "180.00", "normal"],
    ["EEVEE",          "nm", 20, "2.00",   "liquidate"],
    ["MEW_EVO",        "nm", 3,  "45.00",  "normal", "lonestar"],
  ],
  misty: [
    ["GYARADOS",       "nm", 4,  "25.00",  "normal"],
    ["LUGIA_NEO",      "lp", 1,  "220.00", "normal"],
    ["UMBREON_ES",     "nm", 1,  "650.00", "normal"],
    ["SNORLAX",        "nm", 6,  "12.00",  "liquidate"],
    ["VENUSAUR_BASE",  "hp", 1,  "90.00",  "normal"],
  ],
  brock: [
    ["RAYQUAZA_ES",    "nm", 2,  "140.00", "normal"],
    ["MEWTWO_BASE",    "lp", 2,  "60.00",  "normal"],
    ["DRAGONITE",      "nm", 3,  "30.00",  "liquidate"],
    ["CHARIZARD_EVO",  "nm", 2,  "95.00",  "normal"],
    ["GENGAR",         "mp", 5,  "15.00",  "normal"],
  ],
  gary: [
    ["CHARIZARD_BASE", "nm", 1,  "900.00", "normal"],
    ["UMBREON_ES",     "lp", 1,  "500.00", "normal"],
    ["PIKACHU_BASE",   "nm", 30, "6.00",   "liquidate"],
    ["VENUSAUR_BASE",  "nm", 1,  "200.00", "normal"],
  ],
  jessie: [
    ["MEW_EVO",        "lp", 5,  "35.00",  "normal"],
    ["EEVEE",          "nm", 50, "1.50",   "liquidate"],
  ],
};

// [card, minimumCondition, maxBuyPrice, quantityWanted]
// Designed so each vendor has real overlaps plus a few near-misses
// (price too high or condition too low) to show the engine filtering.
const BUYLIST = {
  ash:    [["UMBREON_ES", "lp", "600.00", 1], ["RAYQUAZA_ES", "nm", "150.00", 2], ["LUGIA_NEO", "mp", "250.00", 1]],
  misty:  [["CHARIZARD_BASE", "lp", "500.00", 1], ["PIKACHU_BASE", "nm", "10.00", 20], ["MEW_EVO", "nm", "50.00", 4]],
  brock:  [["BLASTOISE_BASE", "mp", "200.00", 1], ["EEVEE", "nm", "3.00", 30], ["SNORLAX", "nm", "15.00", 5]],
  gary:   [["CHARIZARD_EVO", "nm", "100.00", 1], ["MEWTWO_BASE", "nm", "80.00", 1], ["GENGAR", "lp", "20.00", 3], ["DRAGONITE", "nm", "35.00", 2]],
  jessie: [["GYARADOS", "lp", "30.00", 3], ["VENUSAUR_BASE", "lp", "150.00", 1], ["PIKACHU_BASE", "nm", "7.00", 50]],
};

// ------------------------------------------------------------- helpers ----

async function api(path, { method = "GET", token, body } = {}) {
  const res = await fetch(GATEWAY + path, {
    method,
    headers: {
      ...(body ? { "content-type": "application/json" } : {}),
      ...(token ? { authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { /* non-JSON body */ }
  return { status: res.status, json, text };
}

function must(r, what) {
  if (r.status >= 200 && r.status < 300) return r.json;
  throw new Error(`${what} failed: HTTP ${r.status} ${r.text}`);
}

function grpc(method, payload) {
  const out = execFileSync("docker", [
    "run", "--rm", "-i", "fullstorydev/grpcurl:latest",
    "-plaintext", "-d", "@", EVENT_GRPC, method,
  ], { input: JSON.stringify(payload), encoding: "utf8" });
  return JSON.parse(out);
}

const log = (...a) => console.log(...a);

// ---------------------------------------------------------------- main ----

async function main() {
  // 0. Sanity: gateway up, catalog populated (checked after first login).
  const health = await api("/actuator/health");
  must(health, "gateway health");

  // 1. Vendors: register (or reuse) + login.
  const vendors = {};
  for (const v of VENDORS) {
    const reg = await api("/api/v1/auth/register", {
      method: "POST",
      body: { email: v.email, password: PASSWORD, shopName: v.shopName, city: v.city, state: v.state },
    });
    let created = false;
    if (reg.status === 201) created = true;
    else if (reg.status !== 409) must(reg, `register ${v.email}`);
    const tokens = must(await api("/api/v1/auth/login", {
      method: "POST", body: { email: v.email, password: PASSWORD },
    }), `login ${v.email}`);
    const profile = must(await api("/api/v1/profile", { token: tokens.accessToken }), `profile ${v.email}`);
    vendors[v.key] = { ...v, userId: profile.userId, token: tokens.accessToken };
    log(`vendor ${created ? "created" : "reused "} ${v.shopName.padEnd(24)} ${profile.userId}`);
  }
  const anyToken = vendors.ash.token;

  // 2. Cards: resolve labels against the live catalog through the gateway.
  const cards = {};
  for (const [label, spec] of Object.entries(CARDS)) {
    const qs = new URLSearchParams({ query: spec.query, pageSize: "25" });
    if (spec.setId) qs.set("setId", spec.setId);
    const page = must(await api(`/api/v1/cards/search?${qs}`, { token: anyToken }), `search ${label}`);
    const exact = page.cards.find((c) => c.name.toLowerCase() === spec.query.toLowerCase());
    const pick = exact ?? page.cards[0];
    if (!pick) throw new Error(`no catalog match for ${label} (${spec.query}); is the catalog synced?`);
    cards[label] = pick;
    log(`card   ${label.padEnd(15)} ${pick.externalId.padEnd(11)} ${pick.name} (${pick.setName})`);
  }

  // 3. Events: create via gRPC unless an event with the same name exists.
  const existing = must(await api("/api/v1/events?pageSize=100", { token: anyToken }), "list events");
  const existingList = Array.isArray(existing) ? existing : (existing.items ?? existing.events ?? []);
  const events = {};
  for (const e of EVENTS) {
    const found = existingList.find((x) => x.name === e.name);
    if (found) {
      events[e.key] = found;
      log(`event  reused  ${e.name.padEnd(36)} ${found.id}`);
      continue;
    }
    const res = grpc("vendex.events.v1.EventService/CreateEvent", { organizer_id: ORGANIZER_ID, ...e, key: undefined });
    events[e.key] = res.event;
    log(`event  created ${e.name.padEnd(36)} ${res.event.id}`);
  }

  // 4. Registrations (idempotent: 409 means already registered).
  for (const v of Object.values(vendors)) {
    for (const [eventKey, booth] of Object.entries(v.booths)) {
      const r = await api(`/api/v1/events/${events[eventKey].id}/register`, {
        method: "POST", token: v.token, body: { booth },
      });
      if (r.status !== 409) must(r, `register ${v.key} @ ${eventKey}`);
      log(`reg    ${v.key.padEnd(7)} -> ${eventKey.padEnd(9)} booth ${booth} ${r.status === 409 ? "(already)" : ""}`);
    }
  }

  // 5. Inventory + buy lists. Skip vendors that already have stock.
  for (const v of Object.values(vendors)) {
    const current = must(await api("/api/v1/inventory?pageSize=1", { token: v.token }), `inventory ${v.key}`);
    const has = Array.isArray(current) ? current.length : (current.items?.length ?? 0);
    if (has > 0) { log(`stock  ${v.key.padEnd(7)} already has inventory; skipping stock + demand`); continue; }

    for (const [label, condition, quantity, askingPrice, priority, eventKey] of INVENTORY[v.key] ?? []) {
      must(await api("/api/v1/inventory", {
        method: "POST", token: v.token,
        body: {
          cardId: cards[label].id, condition, quantity, askingPrice, priority,
          eventId: eventKey ? events[eventKey].id : null,
        },
      }), `inventory ${v.key} ${label}`);
    }
    for (const [label, minimumCondition, maxBuyPrice, quantityWanted] of BUYLIST[v.key] ?? []) {
      must(await api("/api/v1/buylist", {
        method: "POST", token: v.token,
        body: { cardId: cards[label].id, minimumCondition, maxBuyPrice, quantityWanted },
      }), `buylist ${v.key} ${label}`);
    }
    log(`stock  ${v.key.padEnd(7)} ${INVENTORY[v.key].length} inventory items, ${BUYLIST[v.key].length} wanted cards`);
  }

  // 6. Give the outbox -> Redpanda -> Overlap path a moment, then report.
  await new Promise((r) => setTimeout(r, 4000));
  log("\noverlaps at Lone Star Card Expo:");
  for (const v of Object.values(vendors)) {
    const page = must(await api(`/api/v1/overlaps/event/${events.lonestar.id}`, { token: v.token }), `overlaps ${v.key}`);
    const list = page.items ?? page.content ?? page.overlaps ?? [];
    const buying = list.filter((o) => o.buyerVendorId === v.userId).length;
    const selling = list.filter((o) => o.sellerVendorId === v.userId).length;
    log(`  ${v.shopName.padEnd(24)} ${String(list.length).padStart(2)} total  (${buying} as buyer, ${selling} as seller)`);
  }

  log(`\nLogin at http://localhost:3000/login with any vendor email above, password: ${PASSWORD}`);
}

main().catch((e) => { console.error(e.message ?? e); process.exit(1); });
