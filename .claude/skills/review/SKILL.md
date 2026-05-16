---
name: review
description: Review code changes on the current branch. Use when the user wants a code review before opening a PR, or wants feedback on uncommitted changes.
---

## What to review

Run `git diff main..HEAD` (or `git diff HEAD` for uncommitted changes) to get the changes.

## How to review

Go through the diff and flag issues at three severity levels:

- **High** — correctness bugs, security issues, data loss risk, broken error handling. Must fix before merging.
- **Medium** — logic that will likely cause problems soon, missing edge case handling, unclear ownership of a resource. Worth fixing before merging.
- **Low** — naming, style, minor duplication, nits. Optional.

Only surface High and Medium findings in your output. Include Low findings as a brief summary line at the end ("N low-severity nits — ask if you want them.") — don't list them individually unless asked.

## Output format

For each finding:

```
[High/Medium] Short title
File: path/to/file.go:line
What: one sentence describing the issue
Why: one sentence on why it matters
Fix: one sentence on what to do instead
```

End with a one-line summary: "X high, Y medium findings." If there are none, say so — a clean review is worth reporting.

## What to look for

- Correctness: does the logic do what it claims?
- Error handling: are errors checked and propagated correctly?
- Go idioms: exported names, receiver conventions, defer usage
- gRPC patterns: status codes, context propagation, deadline handling
- Kafka: are consumers handling errors and offsets correctly?
- Security: hardcoded secrets, unvalidated input at system boundaries
- Tests: are new code paths covered?
