# VenDex — Product Specification

## What Is VenDex?

VenDex is an event-centric inventory coordination platform for Pokemon TCG vendors. It structures the messy, fragmented process of vendor-to-vendor trading that currently happens through Instagram DMs, Discord chats, convention hallway conversations, and spreadsheets.

The platform is not a consumer marketplace. It does not compete with TCGPlayer, eBay, or Whatnot. Those platforms own the vendor-to-consumer transaction. VenDex sits in a different lane entirely — it is operational tooling for vendors to coordinate supply and demand with each other, and with convention attendees who want to sell cards through vendors rather than peer-to-peer.

Conventions are the activation point. The platform is most valuable in the days leading up to and during a Pokemon TCG convention, where vendors and attendees are co-located with limited time and capital.

---

## The Problem

Pokemon TCG vendors face a persistent coordination problem. Vendor A has excess inventory of cards they cannot move. Vendor B is actively searching for those exact cards. Right now, the process of discovering this overlap depends on:

- Scrolling Instagram stories hoping to spot relevant inventory posts
- Browsing Discord servers with inconsistent formatting and no search structure
- Walking booth-to-booth at conventions asking "do you have X?"
- Maintaining private spreadsheets of wanted cards with no automated way to cross-reference other vendors
- Relying on personal relationships built at previous events

This is slow, unreliable, and heavily dependent on luck and social capital. Vendors with smaller networks or fewer convention appearances are at a disadvantage.

Convention attendees face a parallel problem. Someone walks into a card show with a binder full of cards they want to sell. They have no way to know which vendors at the event are looking for what they have. They wander booth-to-booth, get inconsistent offers, and often leave without making deals they would have been happy with — simply because they never found the right vendor.

Event organizers have limited visibility into the trading activity happening at their events. They cannot measure vendor satisfaction, transaction volume, or demand trends in any structured way.

---

## Who Is It For?

### Primary Users: Pokemon TCG Vendors

Small to mid-sized vendors who sell Pokemon singles and sealed product. They attend conventions, run online shops, or both. They buy and sell inventory from other vendors regularly but lack structured tools to coordinate this.

**Their goals:**
- Find vendors who have cards on their buy list before or during events
- Move slow-moving or excess inventory faster
- Reduce time spent manually searching for trading partners
- Prepare smarter inventory for upcoming conventions

### Secondary Users: Convention Attendees (Sellers)

People who attend Pokemon TCG conventions with personal collections they want to sell. They are not vendors — they do not have shops or booths. They want to sell cards to vendors at fair prices without the inefficiency of walking booth-to-booth.

**Their goals:**
- Get visibility to multiple vendors without revealing identity upfront
- Receive competitive offers from interested vendors
- Reduce time spent wandering the convention floor
- Feel safe and in control of who they engage with

### Tertiary Users: Event Organizers

Convention organizers who run Pokemon TCG events. They benefit from increased transaction activity and vendor/attendee satisfaction at their events.

**Their goals:**
- Increase vendor engagement and satisfaction
- Attract more attendees by offering a better event experience
- Access aggregate data about demand trends at their events
- Differentiate their events from competitors

---

## Core Concepts

### Inventory

A vendor's catalog of cards they currently have available. Each entry includes the card identity, condition, quantity, asking price, and an optional priority flag. Inventory marked as "liquidate" signals that the vendor is motivated to move those cards quickly and may accept lower offers.

Inventory can be scoped to a specific event ("I'm bringing these to Collect-A-Con Dallas") or marked as always-available.

### Buy List

A vendor's structured list of cards they are actively trying to acquire. Each entry includes the card they want, acceptable condition range, maximum price they are willing to pay, and quantity needed.

### Inventory Overlap Detection

The core mechanism of the platform. The system continuously compares vendor inventories against other vendors' buy lists within the context of a shared event. When Vendor A's inventory contains cards that Vendor B's buy list is requesting, the system surfaces this as an actionable opportunity to both parties.

This is not social matching. It is a supply-demand intersection — the same fundamental mechanic as a stock exchange order book, applied to trading card inventory. No swiping, no profiles, no algorithmic curation. Just: "these two vendors have complementary inventory, and they are both attending the same event."

Vendors can act on overlaps in two ways. **Pre-event:** save the overlap to an event plan that re-surfaces on event day with a notification, so the vendor can act on it once the floor opens. **In-event:** reveal booth and on-floor contact info to a specific vendor for an in-person meetup. VenDex never hosts the transaction — the actual exchange happens at the booth.

### Anonymous Attendee Listings

Convention attendees can post temporary, anonymous listings of cards they want to sell. These listings are only visible to verified vendors attending the same event. Vendors can express interest or submit preliminary price ranges. The attendee's identity is never revealed until they explicitly choose to engage with a specific vendor.

Listings auto-expire when the event ends. **Attendees cannot interact with other attendees** — VenDex never surfaces other attendees' listings to an attendee, and attendees cannot make offers on each other's cards.

For browsing the event, VenDex follows a **"browse demand, query supply"** rule (see the design principle below). Attendees can freely browse vendor *buy lists* at events they're registered for, so a seller with cards in hand can see which booths want them and plan their walk. But attendees cannot browse vendor *inventory* as a catalog — they can only search for a specific card to see which vendors at the event have it. There is intentionally no "show me everything for sale here" surface, because that would let attendees skip the floor entirely. All attendee purchases still happen at the booth, not in-app.

### Events

Conventions and card shows are the organizing structure of the platform. Events have dates, locations, and rosters of participating vendors. All inventory overlap detection and attendee listings are scoped to events. The platform is most active in the lead-up to and during events.

### Notifications

VenDex's value spikes in the days leading up to and during an event, so notifications are a first-class surface, not an afterthought. Vendors receive in-app and email notifications for: new overlaps on their buy list, new overlaps on inventory marked to liquidate, new anonymous attendee listings matching their buy list, optional price drops on watched cards, and event reminders (T-7d, T-1d, day-of). Each trigger is independently togglable and can be muted per-event. Notification cadence is configurable as real-time, daily digest, or event-only. Push notifications are deferred to a later phase.

---

## Design Principles

These are constraints the product holds itself to. Features that violate them get cut.

### The app is the meeting layer, not the interchange layer

VenDex coordinates *who should meet whom*, but the actual exchange — pricing negotiation, condition inspection, payment, handshake — always happens at the booth. The product deliberately has no in-app checkout, no "mark deal as done" button, no escrow, no chat-based haggling. If someone could complete a card transaction without ever speaking to the other party, the convention has been replaced by a fulfillment center, and VenDex has done damage to the hobby instead of serving it.

### Pre-event matching produces intent, never commitments

Pre-event discovery is the strongest value VenDex creates — vendors walk into a convention with a plan instead of wandering. But matching must produce *awareness* (here's who has what), not *closed deals* (we've agreed on price and quantity). No reserved cards. No locked prices. No "pick this up at my booth" tickets. Every match still requires walking up, looking at the card, talking to the human.

### Browse demand, query supply

VenDex exposes the *demand* side of the market (vendor buy lists at an event) as a fully browseable list. Anyone planning to sell at a convention should be able to see who wants what. But the *supply* side (vendor inventory at an event) is intentionally query-only — you can search for a specific card to find which vendors at this event have it, but there is no "show me all cards for sale at this event" catalog. This asymmetry is deliberate:

- **Browsing demand preserves the convention.** Sellers (vendors or attendees) need structured information to find the right booth efficiently. There is no "happy accident" version of selling a binder.
- **Query-only supply preserves the convention.** Buying is where the magic of a card show lives — turning a corner, spotting something under glass, getting drawn into a booth. A browseable inventory surface would replace the floor walk with a feed scroll, which is exactly the failure mode the product is built to avoid.
- **Vendor competitive intelligence stays intact.** A vendor's full inventory isn't a public catalog other vendors can scrape and undercut.

### Dwell time in the app at events is a negative metric

Activation should happen pre-event and during morning prep. During the event itself, the app's job is to *push the user off it* — surfacing booth-direction nudges ("Vendor C3 wants your Charizards — booth B-7, two aisles over") rather than browse surfaces. If users are spending more time in VenDex during conventions, the product is failing on the principles above.

---

## User Stories

### Vendor Stories

**Pre-Event Preparation**
> As a vendor attending a convention next weekend, I want to see which other attending vendors have cards on my buy list, so I can plan which deals to pursue at the event.

**Inventory Liquidation**
> As a vendor with slow-moving inventory, I want to flag cards as high-priority to liquidate and be surfaced to vendors whose buy lists include those cards, so I can free up capital faster.

**Convention Morning**
> As a vendor setting up my booth at a convention, I want to open the app and immediately see actionable opportunities — "3 vendors here today have cards you want" and "2 vendors are looking for cards you have" — so I can prioritize my time.

**Bulk Upload**
> As a vendor with a large inventory tracked in spreadsheets, I want to import my inventory via CSV so I do not have to manually enter hundreds of cards.

**Buy List Management**
> As a vendor, I want to maintain a persistent buy list that automatically gets checked against other vendors' inventories whenever I register for a new event.

### Attendee Stories

**Anonymous Selling**
> As a convention attendee with a binder of valuable cards, I want to post my cards anonymously so multiple vendors can see what I have and compete for my business, without me needing to walk booth-to-booth.

**Receiving Offers**
> As an attendee who posted anonymous listings, I want to see aggregated vendor interest — "4 vendors interested, price range $38–$45" — so I can decide who to engage with.

**Controlled Identity Reveal**
> As an attendee, I want to reveal my identity only to a specific vendor I choose to meet, so I stay in control of who knows what I have.

**Temporary Participation**
> As a casual attendee, I want my listings to disappear when the event ends so I do not have a persistent profile or ongoing obligations on the platform.

### Organizer Stories

**Event Creation**
> As an event organizer, I want to create an event on the platform so vendors and attendees can coordinate around my convention.

**Demand Visibility**
> As an organizer, I want to see aggregate demand trends — which cards are most wanted, which are oversupplied — to understand what is driving activity at my event.

**Vendor Engagement**
> As an organizer, I want to see how many vendors are actively using the platform at my event, so I can measure the value of promoting VenDex to my attendees.

---

## Organizer UX — Deferred

Organizer-facing UI (event creation form, vendor roster, aggregate analytics dashboard) is deferred to Phase 6 or later. Until then, events are created on behalf of organizers by the VenDex operator via direct database insert or an admin script. The Event Service still ships its full gRPC surface in Phase 2 — `CreateEvent`, `UpdateEvent`, `GetEvent`, `GetEventVendors`, `GetEventAttendees` — so this is strictly a UX deferral, not a service deferral. Vendors and attendees experience events as fully-populated entities from the moment they sign up.

---

## Feature Set by Phase

The product is built in two halves: **vendor-vendor first** (Phases 1-4), then **attendees added on top** (Phase 5). The split exists so the vendor-side product can be validated with real users before any attendee complexity is introduced. If vendor-vendor doesn't earn its keep, attendee flow doesn't ship.

### Phases 1-3 Features (Vendor-Vendor Backend)

- Vendor registration and authentication
- Pokemon card catalog with search (powered by Pokemon TCG API data)
- Inventory management (manual entry + CSV bulk upload)
- Buy list management
- Event creation (by organizers, via admin script for v1) and vendor registration for events
- Inventory overlap detection within event context
- Notification feed: actionable opportunities surfaced to vendors
- Per-vendor saved overlaps (pre-event event-plan) and conversation state (all-can-bid)

### Phase 4 Features (Vendor Frontend + API Gateway)

This is where the vendor-vendor product becomes usable end-to-end. End of Phase 4 = a vendor can sign up, import inventory, register for an event, see overlaps, and reveal booth info to a counterparty — all through a real web app.

- API Gateway: REST → gRPC translation for auth, card catalog, inventory, buy list, events, overlaps, notifications, vendor profile.
- Vendor web app (mobile-first responsive):
  - Vendor dashboard with event-scoped views (stats, opportunities, upcoming events, notifications)
  - Inventory manager: CRUD + CSV import with fuzzy-match resolution
  - Buy list manager
  - Event browse + registration
  - Overlap detail with conversation state and pre-event vs in-event CTAs
  - Notification feed + preferences
  - Vendor signup + profile setup
- Soft launch with a handful of real vendors. No attendee flow yet.

### Phase 5 Features (Attendee Backend + Frontend)

- Attendee role added to Auth Service. Attendee registration (lightweight, no shop profile).
- Anonymous event listing upload (owned by Offer Service end-to-end so attendee identity stays inside one service boundary).
- Vendor interest signals and preliminary offers on anonymous listings.
- Attendee offer review and acceptance with identity reveal.
- Auto-expiration of listings when events end.
- Attendee event-scoped browse and search, following the "browse demand, query supply" rule (browse vendor buy lists, query vendor inventory by card).
- Attendee mobile web flow: signup, event home, browse, find-a-card, list-a-card, my listings & offers, offer detail / accept.
- API Gateway routes extended for listings + offers + attendee endpoints.

### Future Features (Phase 6+: Infrastructure, Organizer UX, Intelligence)

- Kubernetes deployment + observability stack
- Organizer UX: event creation form, vendor roster, aggregate analytics
- Demand trend tracking across events
- Price convergence analytics (ask price vs buy price over time)
- Event-specific aggregate analytics for organizers
- Inventory velocity scoring (how fast certain cards move)
- Regional demand heatmaps
- Proximity-based opportunity surfacing at events ("vendor 2 aisles away has your card")
- Expansion to additional TCGs (Magic, Yu-Gi-Oh, One Piece) if Pokemon adoption succeeds

---

## What VenDex Is Not

- **Not a consumer marketplace.** Collectors can browse what's at an event to plan their visit, but no retail transactions happen in-app. TCGPlayer, eBay, and Whatnot own the consumer transaction.
- **Not a social platform.** No feeds, no followers, no likes, no public profiles. Interactions are transactional and structured.
- **Not a grading service or price guide.** The platform references market data but does not set prices or grade cards.
- **Not peer-to-peer trading.** Attendees cannot trade with other attendees. Vendors are always at the center of every transaction.
- **Not a general trading card platform — at launch.** VenDex is Pokemon TCG only for the initial release. Other TCGs (Magic, Yu-Gi-Oh, One Piece, etc.) are explicitly out of scope for v1, but the platform could expand to additional TCGs in a future phase if Pokemon adoption succeeds.

---

## Monetization (Deferred)

The platform is free for all users during development and early adoption. Potential future revenue streams:

- **Event organizer fees:** Organizers pay for premium analytics, promoted event listings, or platform integration at their conventions.
- **Vendor subscription tiers:** Free tier for basic inventory/buy list management. Paid tier for advanced analytics, priority matching, or higher inventory limits.
- **Event coordination packages:** White-label or co-branded event experiences for larger conventions.

Monetization decisions are deferred until the platform demonstrates value and has active users. The priority is building something useful first.

---

## Success Metrics (If Pursued Beyond Learning)

- Number of vendors with active inventory and buy lists
- Number of overlaps detected per event
- Percentage of overlaps that result in vendor engagement (viewing details, initiating contact)
- Number of anonymous attendee listings posted per event
- Vendor return rate across multiple events
- Organizer adoption (events created on the platform)
