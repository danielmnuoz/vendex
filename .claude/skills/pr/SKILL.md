---
name: pr
description: Create a GitHub PR for the current branch. Use when the user wants to open a PR, submit changes, or push work up for review.
disable-model-invocation: true
---

1. Run `git log main..HEAD --oneline` and `git diff main..HEAD --stat` to understand what changed.
2. Pick a type: Feature, Bug, or Chore.
3. Title format: `Feature: <description>`, `Bug: <description>`, or `Chore: <description>`.
4. Write the PR body with two sections:

**What's being added**
- bullet points describing the product/user-facing changes

**Technical changes**
- bullet points describing implementation details

5. Run `gh pr create --title "<title>" --body "<body>"` and return the PR URL.

No prose, no extra sections, no filler.
