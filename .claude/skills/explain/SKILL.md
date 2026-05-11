---
name: explain
description: Explain code changes to the user in plain language with analogies and ASCII diagrams. Use when the user asks what changed, what a PR does, what uncommitted changes do, or when you're about to explain a technical concept, architectural decision, or piece of code you wrote or modified.
---

## Context to gather first

Check what to explain:
- If uncommitted changes exist: `git diff HEAD`
- If on a feature branch with commits: `git log main..HEAD --oneline` + `git diff main..HEAD`
- If explaining a concept or specific file: read the relevant code

## How to explain

You're talking to an engineer with under 3 years of experience who is not deeply familiar with the language the changes are written in. Your job is to make the "why" and "what" click, not just list what lines changed.

**Use analogies.** Ground unfamiliar concepts in something concrete. If a service is acting as a middleman between two systems, say it's like a translator at a meeting. If data is being cached, say it's like keeping a sticky note on your desk instead of walking to the filing cabinet every time.

**Draw ASCII diagrams** when showing how pieces connect, how data flows, or how something changed structurally. Prefer diagrams over paragraphs for anything involving relationships or sequences.

```
Example flow diagram:
  Request → [Gateway] → [Auth Service] → [Inventory Service] → Response

Example before/after:
  Before: Client → DB (every request)
  After:  Client → Cache → DB (only on miss)
```

**Structure your explanation:**
1. **What changed** — one sentence, plain English, no jargon
2. **Why it matters** — what problem does this solve or what does it enable
3. **How it works** — the mechanism, using a diagram or analogy
4. **Anything to watch out for** — edge cases, things that could go wrong, or follow-up work (only if relevant)

Keep it conversational. Avoid defining every term; if a concept needs a definition, use an analogy instead of a dictionary entry.
