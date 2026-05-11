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

### Anonymous Attendee Listings

Convention attendees can post temporary, anonymous listings of cards they want to sell. These listings are only visible to verified vendors attending the same event. Vendors can express interest or submit preliminary price ranges. The attendee's identity is never revealed until they explicitly choose to engage with a specific vendor.

Listings auto-expire when the event ends. Attendees cannot browse vendor inventory or interact with other attendees. The flow is one-directional: attendees post supply, vendors discover it.

### Events

Conventions and card shows are the organizing structure of the platform. Events have dates, locations, and rosters of participating vendors. All inventory overlap detection and attendee listings are scoped to events. The platform is most active in the lead-up to and during events.

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

## Feature Set by Phase

### Phase 1–3 Features (Core Platform)

- Vendor registration and authentication
- Pokemon card catalog with search (powered by Pokemon TCG API data)
- Inventory management (manual entry + CSV bulk upload)
- Buy list management
- Event creation (by organizers) and vendor registration for events
- Inventory overlap detection within event context
- Notification feed: actionable opportunities surfaced to vendors
- Vendor dashboard: upcoming events, active opportunities, inventory overview

### Phase 4 Features (Attendee Flow)

- Attendee registration (lightweight, no shop profile)
- Anonymous event listing upload
- Vendor interest signals and preliminary offers on anonymous listings
- Attendee offer review and acceptance
- Identity reveal on accepted offers
- Auto-expiration of listings when events end

### Phase 5 Features (Frontend)

- Mobile-first responsive web application
- Vendor dashboard with event-scoped views
- Attendee listing and offer management UI
- Organizer event creation and analytics view

### Future Features (Phase 7+)

- Demand trend tracking across events
- Price convergence analytics (ask price vs buy price over time)
- Event-specific aggregate analytics for organizers
- Inventory velocity scoring (how fast certain cards move)
- Regional demand heatmaps
- Proximity-based opportunity surfacing at events ("vendor 2 aisles away has your card")

---

## What VenDex Is Not

- **Not a consumer marketplace.** Collectors do not browse and buy cards here. That is TCGPlayer, eBay, and Whatnot's domain.
- **Not a social platform.** No feeds, no followers, no likes, no public profiles. Interactions are transactional and structured.
- **Not a grading service or price guide.** The platform references market data but does not set prices or grade cards.
- **Not peer-to-peer trading.** Attendees cannot trade with other attendees. Vendors are always at the center of every transaction.
- **Not a general trading card platform.** VenDex is Pokemon TCG only.

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
