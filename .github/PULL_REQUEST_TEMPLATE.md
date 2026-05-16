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

## Testing

<!--
- [ ] Unit tests added/updated
- [ ] Integration tests added/updated
- [ ] Manual verification (describe)
- [ ] N/A — explain why
-->

## Checklist

- [ ] Specs (`product-spec.md` / `technical-spec.md`) updated if behavior or architecture changed
- [ ] UI changes consume tokens from `ui/tokens.css` rather than hex literals
- [ ] Mockups in `ui/vendex.pen` updated via `mcp__pencil__*` if visual design changed
- [ ] No secrets, credentials, or large binaries staged
