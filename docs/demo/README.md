# Demo walkthrough and findings

Recorded on September 12, 2026 against the local Docker Compose backend and a production build of the frontend. All vendor names, event descriptions, stock, and prices in these captures are demo data.

## Format and demo plan

The README starts with a static match screen so the value is visible immediately. Two short, captioned GIFs then explain the inputs and the outcome. They are paced sequences of actual browser screenshots, **not continuous screen recordings**. Source screenshots are retained under `frames/`, and static alternatives are linked beside each animation. Total playback is 62 seconds; both animations loop.

| Clip | Time | Story |
| --- | --- | --- |
| Prepare stock | 0–10s | Show existing inventory, upload a CSV, and select the event. |
| Prepare stock | 10–22s | Resolve an abbreviated set name and review the exact rows. |
| Prepare stock | 22–32s | Commit the import, then explain condition and budget on the buy list. |
| Find matches | 0–12s | Establish the Dallas convention; show a buying and a selling match. |
| Find matches | 12–18s | Switch to Portland; different attending vendors produce different matches. |
| Find matches | 18–30s | Return to Dallas, save Pikachu, and show the dashboard's saved count. |

For a later narrated recording, use the same sequence at roughly 90 seconds. Spend the extra time explaining the Rayquaza example: the buyer wants two NM copies at up to $150 each, and a vendor at the same event has two at $140. Skip login typing and infrastructure screens. The README's architecture section explains the backend separately.

## Reproduce the walkthrough

Follow [local setup](../../README.md#run-locally), then run `node scripts/seed/seed-dev.mjs` from the repository root. The seeder defaults to Docker Desktop's `host.docker.internal:9092` for Event gRPC. On other Docker setups, `EVENT_GRPC_TARGET` must be an address reachable from the temporary grpcurl container; `GATEWAY_BASE_URL` must be reachable from the host running Node.

All five seeded accounts use password `VendexDemo1!`:

| Shop | Email | Seeded events |
| --- | --- | --- |
| Pallet Town Cards | `ash.ketchum@vendex.local` | Dallas and Portland |
| Cerulean Gym Singles | `misty@vendex.local` | Dallas and Portland |
| Pewter City Vault | `brock@vendex.local` | Dallas |
| Oak Labs Collectibles | `gary.oak@vendex.local` | Dallas |
| Team Rocket Trading Co | `jessie.james@vendex.local` | Dallas |

1. Sign in as Pallet Town Cards. The base seed produces eight Dallas opportunities and three Portland opportunities for this account.
2. Open **Inventory → Import CSV** and upload [inventory.csv](inventory.csv). Scope it to **Lone Star Card Expo**. The first row intentionally says `Charizard,Base`; with the captured catalog this requires choosing **Charizard / Base Set** from the candidates. Catalog refreshes may change candidate ordering.
3. Build the preview. Confirm three rows, their condition, quantities, and prices. Import once and verify the completion screen. Return to inventory and confirm that three rows were added.
4. Open **Buy list**. Explain Rayquaza's NM minimum, quantity of two, and $150 ceiling. Optionally edit that ceiling to $130, save, and reopen Matches after the asynchronous pipeline catches up: the $140 Rayquaza listing disappears. Restore $150 afterward; the match returns. A saved match keeps its saved status when it becomes active again.
5. Open **Events → Lone Star Card Expo → View opportunities**. Compare the Pikachu selling opportunity with the Rayquaza buying opportunity.
6. Change the event selector to **Pacific Northwest Collectors Fair**. Rayquaza is absent because its seller is not registered there. Return to Dallas.
7. Save Pikachu and/or Rayquaza. Revisit the page to verify the Saved badge persists, then open Overview to see the count. Captures show two saved opportunities; an untouched seed starts with none.

Imports are additive. This session imported the three-row fixture twice: once to reproduce the completion bug and once to verify the fix, taking Pallet Town Cards from five to eleven inventory rows. Its saved Pikachu and Rayquaza matches remain in the local demo database, and the temporary Rayquaza price edit was restored to $150. One demo notification was marked read. Existing events and other repository changes were retained.

For clean future captures, use a fresh local demo environment or a separate demo account instead of repeatedly importing into the same account. The seeder does not reset existing data: it skips both stock and demand for any vendor with inventory. Its fixed October/November 2026 event dates also need maintenance as time passes.

## Capture and rebuild the GIFs

The screenshots were taken through browser computer use at the browser's default 1280 × 720 viewport. The frontend was built with `npm run build` and served with `npm run start -- --port 3001`, keeping development indicators out of the captures. Port 3001 is only a capture convenience; ordinary development uses port 3000.

`build-gifs.py` adds a caption strip below each screenshot and encodes the sequence with explicit reading time. It does not change the displayed application state or synthesize mouse movement. To rebuild the checked-in GIFs from their source frames:

```bash
python3 -m venv /tmp/vendex-demo-tools
/tmp/vendex-demo-tools/bin/pip install Pillow
/tmp/vendex-demo-tools/bin/python docs/demo/build-gifs.py
```

The two GIFs are approximately 1 MiB each. When recapturing, wait for the requested content and card art to render; a visible page heading alone may precede the API response. Preserve the before/after state of the saved match. Inspect every frame, and keep the static alternatives current.

## Walkthrough findings

### Fixed during README preparation

| Issue | Reproduction | Fix and evidence |
| --- | --- | --- |
| Broken event date range | Dashboard, event list, and event detail displayed `Oct 17–2026 (day: 18)`. | Explicit same-month, cross-month, cross-year, and single-day formatting in `client-data.ts`. Five regression cases pass; production browser shows `Oct 17–18, 2026`. |
| CSV success reset to upload | Import three reviewed rows. The rows are saved, but the modal resets to “Choose a file,” obscuring success and inviting a duplicate import. | Defer inventory refresh until the completed dialog closes. Tests cover both Return to inventory and the close button; the production browser now shows “3 inventory items imported,” then refreshes the table. |
| Incorrect frontend gateway setup | Frontend README referred to localhost:8080, which hosts the broker console. | Corrected the instructions to port 8081 and the locked dependency install. |

### Remaining product and demo gaps

| Priority | Finding | Evidence / next step |
| --- | --- | --- |
| Before a vendor pilot | **Booth visibility contradicts the match-card promise.** Match cards say booths stay private, but the authorized event roster shows every registered vendor's booth. | Observed on the Dallas roster. `EventController.vendors()` returns booth-bearing registration DTOs. Reconcile the intended policy across gateway, roster, match copy, and the planned audited reveal workflow. A disabled reveal button does not enforce privacy across other endpoints. |
| Core workflow | **There is no dedicated saved-plan view.** Saved matches have badges and a count, but no list/filter or removal action in the web app. An inactive saved match disappears from the active match grid. | Saved Rayquaza, lowered the ceiling to $130, and observed it disappear from Matches. The backend saved endpoint retains history, but the frontend only renders active overlaps. Add a plan view with inactive status and explicit navigation. |
| Data correctness | **The seed can select Pokémon TCG Pocket cards.** Unconstrained name searches chose Gengar, Snorlax, and Gyarados from Genetic Apex during this run. | The seed output included `A1-122`, `A1-211`, and `A1-078`. Pin physical card printings in the fixture and decide whether the catalog needs a physical/digital boundary for convention inventory. Do not assume name-only search picks the right printing. |
| UI consistency | **The navigation notification badge can be stale.** | After a buy-list update the dashboard showed four unread while the navigation still showed three. The shell loads its own count independently. Refresh/invalidate it after notification mutations and relevant navigation or updates. |
| Larger datasets | **Some screens load a first page without a way to fetch more.** The dashboard requests only eight overlaps but labels that number as current opportunities. Inventory, buy lists, and matches stop at 100 entries. | Source inspection in the vendor pages; the eight-match demo does not exercise overflow. Add pagination or honest “showing N” counts before treating these as totals. |
| Demo resilience | **Seed retries do not repair partial data, and some card artwork is missing.** | The seed skips stock and demand together once any inventory exists. The chosen Eevee printing displays the `?` fallback. Pin suitable fixture printings and make seeding reconcile its own rows if repeatable clean runs are needed. |

Contact reveal, email transport, attendee offers, and organizer UI remain unimplemented. The walkthrough did not enable or simulate those features.

### Validation performed

- Real browser: demo login; dashboard; inventory; ambiguous CSV selection → preview → commit → refreshed stock; buy-list price edit and restoration; event browse/detail/roster; Dallas/Portland switching; match disappearance/reactivation; save persistence; notification mark-as-read; settings inspection.
- Existing demo seed executed successfully through the gateway and Event API; all five vendors reported real overlaps.
- Frontend: `npm test` — 14 tests passed; `npm run lint` — passed; `npm run build` — passed.
- Captured and inspected screenshots and every GIF scene from the production build.

This was a desktop vendor walkthrough, not a complete release audit. Fresh signup, new event registration through the browser, mobile layouts, large-page behavior, and a fresh empty-database bootstrap were not re-tested here. The full backend Maven suite was not rerun because the implementation changes are confined to frontend date formatting and the CSV dialog lifecycle.
