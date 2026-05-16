---
name: learn
description: Teach a concept by grounding it in this project. Use when the user wants to understand a topic ("what is gRPC", "teach me Kafka consumer groups", "how do JWTs work", "explain the saga pattern") — not when explaining a specific code change. Output is an analogy, real-world product examples, a .NET/monolith bridge, the concrete demonstration in VenDex, tradeoffs, and what to read next. Save the writeup to docs/learning/ so the user accumulates a personal textbook as the project progresses.
---

## When to use this skill vs /explain

- `/explain` — "what did we just build / change here" — scoped to a specific diff or file. The artifact is the subject.
- `/learn` — "what is X / teach me X" — scoped to a concept. The artifact is just one demonstration of the idea.

If the user asks both at once ("what is this and what does it do here"), run `/learn` and use the project's code as the "How VenDex uses it" section.

## Who you're teaching

The reader is a 25-year-old SWE with ~2 years of experience, primary background **.NET on a monolithic repo**. They are new to **Go, Python, and microservices**. They know textbook definitions of distributed-systems building blocks (Kafka, Redis, gRPC, JWT, protobuf, etc.) — they can tell you what each one *is* — but they have not actually *used* them. Don't lecture from first principles; bridge from .NET/monolith land to what's different here.

**Important: the reader is not a .NET expert either.** They know the language and the everyday surface area, but not deep framework internals. When you make a .NET bridge, you must also briefly explain the .NET side — don't assume names like WCF, `IHostedService`, `MemoryCache`, `IConfiguration`, MediatR, Polly, or Azure Service Bus are familiar without a one-line gloss. A bridge that uses an unfamiliar reference to explain an unfamiliar reference teaches nothing.

Format for a bridge: name the .NET thing, give it a one-line definition, *then* connect it to the new concept. For example:

- "gRPC is like **WCF** — a .NET framework for calling methods on a remote server as if they were local, with strongly-typed request/response contracts. gRPC works the same way, but the contract lives in a `.proto` file rather than a C# interface, and the wire format is binary protobuf rather than SOAP/JSON."
- "A Kafka consumer group is like **competing-consumer workers on Azure Service Bus** — multiple processes pulling from a shared queue, each message handled once. The Kafka twist: it also remembers each *group's* read position independently, so a second unrelated group can replay every message from the start without affecting the first group."
- "Redis as a cache is like **`MemoryCache`** (the in-process key/value cache built into .NET) — except it lives in a separate process, survives app restarts, and is shared across every service that points at it."

If you don't know a clean .NET analog, say so plainly and skip the bridge — don't force one. A missing bridge is better than a misleading one.

## Context to gather first

Before writing:
1. Skim `product-spec.md` and `technical-spec.md` for where the concept shows up in VenDex (which phase, which service, which deliverable). Use grep, not just intuition.
2. If code for the concept already exists, find the file(s) and read the relevant section.
3. If the concept is upcoming (e.g., Kafka in Phase 2 before Phase 2 is built), point at the spec section instead.
4. If the user pointed at a specific file or PR, read that first — it's the demonstration anchor.

Never invent a file path, function name, or commit. If you're not sure something exists, grep before claiming it.

## Structure of the output

Produce a markdown document with these sections, in this order. Keep it under ~600 words unless the concept genuinely needs more.

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

## Tone and format

- Conversational. Use "you" and "we." Contractions are fine.
- Direct, not hedged. "Use X when Y" beats "it could be argued that X might be appropriate when…"
- ASCII diagrams for anything involving data flow, component relationships, or before/after structural changes.
- Don't define every term — define ones the user is unlikely to know, and use the .NET bridge for ones they might already have a different name for.
- No filler ("Great question!", "Let's dive in!"). Start with the content.

## Persisting the output

Default behavior: save the writeup to `docs/learning/<concept-slug>.md` (kebab-case slug of the topic, e.g. `kafka-consumer-groups.md`, `jwt-key-rotation.md`). Create the directory if it doesn't exist.

Before writing the file, tell the user the path you're about to create and confirm — unless they explicitly asked for a file or said "save it." If they say they just want the answer in-conversation, skip the file.

If a file at that path already exists, ask whether to overwrite, append, or pick a new slug. Don't silently clobber prior learning notes.
