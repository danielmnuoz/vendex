<!--
PR title: follow conventional commits, e.g.
  feat(inventory): add CSV ingestion endpoint
  fix(auth): handle expired refresh token on rotation
  chore(ci): pin golangci-lint version
-->

## Summary

<!-- One or two sentences. What does this PR do, at a glance? -->

## What changed and why

<!--
The "why" matters more than the "what" — the diff already shows the what.
If this PR is driven by the spec, link the section. If it diverges from the
spec, say so explicitly (per CLAUDE.md, the code is current; flag the drift).
-->

## Major entities added or modified

| Entity | Kind | Change | Responsibility |
|---|---|---|---|
| <!-- e.g. InventoryService --> | <!-- service/module/table/page/contract --> | <!-- added/modified --> | <!-- concise responsibility --> |

## System design delta

<!--
Required when this PR changes services, data flow, persistence, messaging,
external dependencies, or trust boundaries.

Link a committed explainer under docs/learning/. Its cumulative Mermaid diagram
uses blue (#4C78A8) for components already on main, orange (#F28E2B) for parts
and flows added here, and gray (#B0B7C3) for explicitly future components.

If architecture did not change, write: "No system-design change — <reason>."
-->

## Testing

<!--
- [ ] Unit tests added/updated
- [ ] Integration tests added/updated
- [ ] Manual verification (describe)
- [ ] N/A — explain why
-->

## Follow-ups

<!-- What is deliberately deferred to a later PR? -->

## Checklist

- [ ] Specs (`product-spec.md` / `technical-spec.md`) updated if behavior or architecture changed
- [ ] UI changes consume tokens from `ui/tokens.css` rather than hex literals
- [ ] Mockups in `ui/vendex.pen` updated via `mcp__pencil__*` if visual design changed
- [ ] Major entities are summarized in the PR description
- [ ] Architecture-changing PR includes and links a cumulative `docs/learning/` explainer
- [ ] Existing PR branch will be preserved after merge
- [ ] No secrets, credentials, or large binaries staged
