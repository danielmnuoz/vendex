---
name: learn
description: Teach concepts grounded in this project. Single concept ("what is gRPC", "teach me Kafka consumer groups", "how do JWTs work") → markdown deep-dive. Multi-concept walkthrough ("explain what we just merged", "walk me through PR #7", "teach me everything Phase 1 introduced") → HTML survey doc with TOC, callouts, and Q&A. Both modes save to docs/learning/ so the user accumulates a personal textbook.
---

## When to use this skill vs explain-code-changes

- `explain-code-changes` — terse "what does this diff do" with ASCII diagrams. Use when the user wants a brief, single-PR explanation focused on the mechanics.
- `learn` — pedagogical. Use when the user wants to *understand* something, whether that's a concept or a body of work. The output is a teaching artifact they can re-read later.

If the user says any of: "teach me", "explain in detail", "I don't understand", "help me learn", "walk me through" — that's `learn`.

## Format dispatch: markdown vs HTML

The skill produces one of two artifacts depending on what's being taught.

| Input shape | Examples | Format | Path |
|---|---|---|---|
| **Single concept** | "what is RS256", "teach me Kafka consumer groups", "explain JWKS" | Markdown | `docs/learning/<slug>.md` |
| **Multi-concept walkthrough** | "explain what we just merged", "walk me through PR #7", "teach me everything Phase 1 introduced", "tour the auth service" | HTML | `docs/learning/<slug>.html` |

Heuristic: if the answer is one focused topic, write markdown. If you'd end up writing 4+ H2 sections covering distinct concepts (e.g., a PR that introduces JWTs *and* JWKS *and* bcrypt *and* AES-GCM), write HTML — the visual hierarchy and TOC make it digestible. If unsure, ask: "single deep-dive or full walkthrough?"

The user can override: "in markdown" or "in HTML" forces the format.

## Who you're teaching

The reader is a 25-year-old SWE with ~2 years of experience, primary background **.NET on a monolithic repo**. They are new to **Java, Python, Go, and microservices**. VenDex's backend is written in **Java 21 + Spring Boot** as of 2026-05-25 (switched from Go mid-Phase-1). The reader knows textbook definitions of distributed-systems building blocks (Kafka, Redis, gRPC, JWT, protobuf, etc.) — they can tell you what each one *is* — but they have not actually *used* them. Don't lecture from first principles; bridge from .NET/monolith land to what's different here.

When explaining Java/Spring Boot specifically, don't assume Spring annotations (`@Service`, `@RestController`, `@Autowired`, `@Configuration`, `@Bean`, `@Component`, etc.) are familiar — give each one a one-line gloss the first time it appears. The reader's instinct will be to compare them to .NET attributes / DI patterns; lean into that comparison.

**Important: the reader is not a .NET expert either.** They know the language and the everyday surface area, but not deep framework internals. When you make a .NET bridge, you must also briefly explain the .NET side — don't assume names like WCF, `IHostedService`, `MemoryCache`, `IConfiguration`, MediatR, Polly, or Azure Service Bus are familiar without a one-line gloss. A bridge that uses an unfamiliar reference to explain an unfamiliar reference teaches nothing.

Format for a bridge: name the .NET thing, give it a one-line definition, *then* connect it to the new concept. For example:

- "gRPC is like **WCF** — a .NET framework for calling methods on a remote server as if they were local, with strongly-typed request/response contracts. gRPC works the same way, but the contract lives in a `.proto` file rather than a C# interface, and the wire format is binary protobuf rather than SOAP/JSON."
- "A Kafka consumer group is like **competing-consumer workers on Azure Service Bus** — multiple processes pulling from a shared queue, each message handled once. The Kafka twist: it also remembers each *group's* read position independently, so a second unrelated group can replay every message from the start without affecting the first group."
- "Redis as a cache is like **`MemoryCache`** (the in-process key/value cache built into .NET) — except it lives in a separate process, survives app restarts, and is shared across every service that points at it."
- "Spring Boot's `@RestController` is like ASP.NET Core's `[ApiController]` / `Controller` base class — a stereotype annotation that tells the framework 'this class handles HTTP requests, and method return values should be serialized to the response body.' Spring picks up the class via component scanning instead of MVC route registration in `Program.cs`, but the end result is identical."
- "Maven is like **NuGet + MSBuild combined** — `pom.xml` declares dependencies (NuGet's job) *and* drives the build lifecycle (MSBuild's job: compile, test, package, deploy). Modules in a multi-module Maven project map roughly to projects in a .NET solution."
- "Spring's dependency injection container is like ASP.NET Core's built-in DI (`IServiceCollection` / `IServiceProvider`) — both wire constructor-injected dependencies based on registered type bindings. The biggest day-to-day difference: Spring scans the classpath for `@Component`/`@Service`/etc. annotations and auto-registers them, where ASP.NET Core expects explicit `services.AddScoped<...>()` calls. Spring sometimes feels like 'magic'; ASP.NET DI is more explicit."

If you don't know a clean .NET analog, say so plainly and skip the bridge — don't force one. A missing bridge is better than a misleading one.

## Context to gather first

Before writing:

1. Skim `product-spec.md` and `technical-spec.md` for where the concept(s) show up in VenDex (which phase, which service, which deliverable). Use grep, not just intuition.
2. If code for the concept already exists, find the file(s) and read the relevant sections. Use `file_path:line_number` references when citing.
3. If the concept is upcoming (e.g., Kafka in Phase 2 before Phase 2 is built), point at the spec section instead.
4. If the user pointed at a specific file, PR, or branch, read it first — it's the demonstration anchor. For PR/branch walkthroughs, run `git diff main..HEAD --stat` and `git log main..HEAD --oneline` to understand the scope.

Never invent a file path, function name, or commit. If you're not sure something exists, grep before claiming it.

## Markdown output (single-concept mode)

A focused deep-dive, ~600 words unless the concept genuinely needs more. Sections in this order:

**1. What it is**
Two or three sentences, plain English. Define every term inline the first time it appears. End with a `.NET / monolith bridge:` line that names the closest analog from the user's existing mental model — or explicitly says "no clean .NET analog; here's why" if there isn't one.

**2. An analogy**
A concrete, non-tech analogy. The goal is to make the idea sticky 24 hours from now. Strong analogies use settings the reader can picture: a post office, a restaurant kitchen during dinner rush, a library, a hotel front desk, a busy coffee shop. Skip the analogy if it doesn't fit cleanly — a forced analogy teaches less than no analogy.

**3. Where it shows up in the wild**
Two or three real products where this concept is load-bearing. **Name actual companies and features** — Stripe webhooks, Discord's message fan-out, GitHub Actions runners, Venmo's transaction ledger, Spotify's "Made for You" pipeline, Uber's surge pricing engine. One sentence each on *how* the concept is used there. The user explicitly likes thinking in terms of real-world products — lean into this section.

**4. How VenDex uses it**
The concrete demonstration. Point at the specific service, file, or planned feature with `file_path:line_number` when code exists. If it doesn't exist yet, quote the relevant lines from the spec and name the phase it lands in. Walk through one end-to-end example — *the actual thing*, not a generic illustration. Use an ASCII diagram if data is flowing between components.

```
Example ASCII pattern for a data-flow concept:
  Vendor uploads CSV → [inventory svc] → Kafka topic "inventory.updated"
                                              ↓
                                  [overlap-detection svc] reads & recomputes matches
```

**5. Why this and not something else**
- What problem does this solve that a simpler tool wouldn't?
- What did we give up by choosing it? (Every choice has a cost — name it.)
- When would you reach for this pattern in *another* project?
- When is it the wrong tool? Give a concrete example of when *not* to use it.

This section is where the user starts being able to make their own architecture decisions. Don't skip it.

**6. Going deeper**
- One canonical primer if you genuinely know a good one (official docs page, a well-known blog post, a specific book chapter). If you don't know a reliable resource, write `Search terms: <2-3 phrases>` instead — never invent a URL.
- One or two adjacent concepts worth learning *after* this one clicks, with a one-line "why next."

**7. Q&A (optional)**
If you can predict 2–3 obvious follow-up questions the reader will have ("wait, but how does X handle Y?"), answer them inline. Skip the section entirely if no obvious follow-ups exist — don't manufacture them.

## HTML output (multi-concept walkthrough mode)

For PRs, branches, phases, or any time the answer spans several concepts. Start from the template at `.claude/skills/learn/template.html` — copy it, then fill in the marked sections. The template provides the Anthropic-style CSS (serif headings, warm off-white background, callout boxes for Analogy / Why this matters / Warning), the TOC scaffold, and the footer layout.

Structure:

1. **Title + lede** — the doc's name and a one-sentence summary of what's being explained.
2. **Meta line** — "Written for the VenDex repo owner. Save or delete this file when you're done with it — it lives in `docs/learning/` but isn't committed."
3. **TOC** — every H2 section linked. Sub-bullets for H3 sections inside long top-level sections.
4. **Big picture** (first H2) — the one-paragraph "what changed and why it matters" for someone who hasn't been following along.
5. **One H2 per major concept**, in the order they show up in the codebase or in the user's mental flow. Each section:
   - Starts with the .NET bridge or analogy if either fits.
   - Uses callout boxes (`<div class="callout why">` for "Why this matters", `class="callout analogy"` for analogies, `class="callout warn"` for caveats) sparingly — at most one per section.
   - References real code with file paths.
6. **Q&A at the end** — anticipate 5–10 obvious follow-up questions and answer them inline. The Q&A section is one of the most valuable parts of the doc; don't skip it.
7. **Footer** — files referenced, related issues, related docs in the repo.

When the user asks follow-up questions about an HTML doc you wrote, extend the Q&A section in place rather than producing a new doc — keeps the doc canonical.

Reference example: `docs/learning/phase1-explainer.html` is a finished example of this pattern explaining VenDex's first two PRs.

## Tone and format (both modes)

- Conversational. Use "you" and "we." Contractions are fine.
- Direct, not hedged. "Use X when Y" beats "it could be argued that X might be appropriate when…"
- ASCII diagrams (markdown) or real code blocks (HTML) for anything involving data flow, component relationships, or before/after structural changes.
- Don't define every term — define ones the user is unlikely to know, and use the .NET bridge for ones they might already have a different name for.
- No filler ("Great question!", "Let's dive in!"). Start with the content.
- Real code from the repo, not abstract pseudo-code, whenever a real example exists.

## Persisting the output

Default behavior: save to `docs/learning/<concept-slug>.md` or `docs/learning/<concept-slug>.html`. Kebab-case slug of the topic — `kafka-consumer-groups.md`, `jwt-key-rotation.md`, `phase1-explainer.html`, `pr-7-card-catalog.html`. Create the directory if it doesn't exist.

Before writing the file, tell the user the path you're about to create. They can redirect — but don't pause execution waiting for explicit confirmation if the path is obvious from the request.

If a file at that path already exists, ask whether to overwrite, append (for HTML Q&A extensions), or pick a new slug. Don't silently clobber prior learning notes.

The `docs/` directory is gitignored — these are personal learning artifacts, not project documentation. That's intentional; the writeups can be opinionated and reflect one person's mental model without being treated as authoritative for the whole team.
