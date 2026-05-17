---
name: test
description: Generate a Go test file for a source file. Use when the user wants table-driven tests scaffolded for a function, type, or whole file. Produces `_test.go` ready to run with `go test`.
---

## Inputs

The user gives you a Go source file path, or a function/type name. If only a name, find the file with grep first.

## How to write the tests

1. **Read the source file** with the Read tool. Understand the public surface: exported functions, methods, types.
2. **Identify what's worth testing.** Pure functions: input/output tables. Methods with side effects: mock the dependency. Unexported helpers used by exported code: tested transitively, not directly.
3. **Use table-driven tests** as the default Go pattern. Each case is a struct in a slice; loop with `t.Run(tc.name, ...)` for subtest names. Cover: happy path, every documented edge case, every error condition, boundary values (empty, nil, zero, negative, max).
4. **Mock at interface boundaries.** If the code under test depends on an interface, write a fake implementation (or use the existing one in the package's test files — check first). Do NOT mock things like `time.Time` or `*sql.DB` directly — those are concrete types; the package should expose an interface or a `now func() time.Time` seam, and if it doesn't, flag that as a testability issue.
5. **No external dependencies in unit tests.** No real DB, no network, no filesystem. If the function reaches for them, mock or skip. Integration tests live in `*_integration_test.go` files with a `//go:build integration` tag.
6. **Match the package's existing test style.** If there's already `_test.go` in the package, mirror its conventions (helper naming, assertion style, fixture setup).
7. **Standard library testing.** Use `testing` + `errors.Is` for error comparison. Pull in `github.com/stretchr/testify/assert` only if it's already used in the package.

## Output

- Write the test file as `<source>_test.go` in the same package.
- Run `go test ./<package>/...` to confirm it compiles and passes.
- Run `go test -cover ./<package>/...` and report the coverage delta in a single sentence.

## What NOT to do

- Don't generate tests for `main.go` — it's an entrypoint, exercised by integration tests instead.
- Don't generate tests for trivial getters/setters or constructors with no logic.
- Don't write tests that just exercise the language ("does append work?"). Test our logic, not Go's.
- Don't write tests against the implementation — test the behavior. If the implementation changes but behavior is the same, the test should still pass.
- Don't introduce a new mocking library. Hand-written fakes are cheaper to maintain at this project's scale.
