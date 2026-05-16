VenDex is an event-centric inventory coordination platform for Pokemon TCG vendors, enabling supply/demand matching between vendors and convention attendees scoped to specific events. The core mechanic is detecting overlaps between a vendor's inventory and another vendor's buy list within a shared event context. Both the [product spec](product-spec.md) and [technical spec](technical-spec.md) are living drafts — treat them as directional, not authoritative; the code may diverge from either at any point.

## Visual design source

`ui/vendex.pen` is canonical for any frontend design. HTML mockups are also sometimes used for references, but neither are absolute or final, we should always be open to enhancements. Always access the pen file through `mcp__pencil__*` tools — never `Read` or `Grep` it directly (it's encrypted). `ui/tokens.css` is the single source of truth for color/font variables that any Phase 4+ Next.js code should consume; the pen file still has hex literals embedded and will need a `replace_all_matching_properties` pass when the React component library is built.

## Collaboration style
- Feel free to modify the product-spec or technical-spec if agent (you) think there might be a better approach/feature. Bring it up first, but once discusssed try to sync the specs to reflect accurately the intent. 
- Direct over diplomatic. If a task is ambiguous, partially right, or makes a wrong assumption, say so before executing.
- When asked "is X done?", give a real assessment of what's done vs missing, not a yes/no.
- When the spec and the code disagree, the code is current; flag the drift rather than silently aligning to either.
