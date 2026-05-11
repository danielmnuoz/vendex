---
name: issue
description: Create a GitHub issue for a feature, bug, or chore. Use when the user wants to track future work, log a bug, or capture a task to be picked up later. Also use when a conversation surfaces follow-up work that should not be forgotten.
---

## Title format

`Feature: <description>`, `Bug: <description>`, or `Chore: <description>`

## For Feature and Chore issues

Gather enough context from the conversation, then create the issue with:
- A one-sentence description of what it is
- What it enables or why it's needed
- Acceptance criteria (bullet list of what "done" looks like)

## For Bug issues

Bugs need specifics to be actionable. Before creating, ask the user any of the following that aren't already known:

- What were you doing when it happened?
- What did you expect to happen?
- What actually happened?
- Can you reproduce it consistently, or does it happen randomly?
- Any error messages or logs?
- What environment (local, staging, prod)?

Write the issue body with these sections:
- **What happened** — specific description of the wrong behavior
- **Steps to reproduce** — numbered list
- **Expected behavior**
- **Environment / context**

A vague bug report wastes time. Push for specifics before creating.

## Creating the issue

```
gh issue create --title "<title>" --body "<body>"
```

Return the issue URL when done.
