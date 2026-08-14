---
name: pr
description: Create a GitHub PR for the current branch. Use when the user wants to open a PR, submit changes, or push work up for review.
disable-model-invocation: true
---

1. Inspect the complete branch before writing anything:
   - `git log main..HEAD --oneline`
   - `git diff main...HEAD --stat`
   - `git diff main...HEAD --name-status`
   - the relevant product/technical-spec sections
2. Use a conventional-commit PR title, for example `feat(inventory): add CSV ingestion`.
3. Write the PR body with these sections:

**Summary**
- One or two sentences describing the user/product outcome and where this increment sits in the roadmap.

**What changed and why**
- Describe behavior and architectural decisions. Explain why the change exists instead of repeating filenames from the diff.

**Major entities added or modified**
- Use a compact table with `Entity`, `Kind`, `Change`, and `Responsibility` columns.
- Include services, modules, contracts, database tables, endpoints, background workers, pages, and shared components that are central to the increment.

**System design delta**
- Required when services, data flow, persistence, messaging, external dependencies, or trust boundaries change.
- Add a committed explainer under `docs/learning/<phase-or-pr-slug>.md` and link it from the PR. `docs/` is ignored by default, so explicitly force-add the intended explainer; never sweep in unrelated personal learning files.
- The explainer must contain a cumulative Mermaid diagram. Color components already present on `main` blue (`#4C78A8`), components/data flows added by this PR orange (`#F28E2B`), and explicitly future/not-yet-built components gray (`#B0B7C3`). Include a legend and at least one concrete end-to-end flow.
- If Markdown/Mermaid is not sufficient, commit an SVG or PNG under `docs/learning/assets/` and embed it in the explainer.
- For a PR with no system-design change, write `No system-design change` and explain why in one sentence.

**Testing**
- List the exact commands run and their outcomes. Distinguish unit, integration, manual, and CI verification. Never imply tests ran when they did not.

**Follow-ups**
- Name intentionally deferred work so the PR boundary stays clear.

4. Keep `product-spec.md` and `technical-spec.md` synchronized when behavior or architecture changes.
5. Push the branch, create or update the PR, wait for required CI, and address actionable review feedback before merging.
6. Merge only when the PR is mergeable and required checks pass. Never manually close an implementation PR instead of merging it, and never delete its local or remote branch after merge.
7. Return the PR URL, merge result, verification summary, and explainer path.
