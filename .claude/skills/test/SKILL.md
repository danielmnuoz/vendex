---
name: test
description: Generate a JUnit 5 test class for a Java source file. Use when the user wants tests scaffolded for a class, method, or whole file. Produces a `*Test.java` (unit) or `*IT.java` (integration) file ready to run with `mvn test` or `mvn verify`.
---

## Inputs

The user gives you a Java source file path, or a class/method name. If only a name, find the file with grep first.

## How to write the tests

1. **Read the source file** with the Read tool. Understand the public surface: public classes, methods, constructors, annotations (Spring stereotypes like `@Service`, `@Repository`, `@RestController`, `@Component`).
2. **Identify what's worth testing.** Pure methods: input/output cases via `@ParameterizedTest`. Methods with collaborators: mock the dependency with **Mockito**. Package-private helpers used by public code: tested transitively, not directly.
3. **Use parameterized tests for input coverage.** JUnit 5's `@ParameterizedTest` + `@MethodSource` / `@CsvSource` / `@EnumSource` is the Java equivalent of Go's table-driven pattern. Cover: happy path, every documented edge case, every error condition, boundary values (empty, null, zero, negative, max). Each parameter set becomes its own subtest.
4. **Mock at interface boundaries with Mockito.**
   - Constructor-injected collaborators: pass `Mockito.mock(SomeService.class)` directly in the test, or use `@Mock` + `@InjectMocks` if the class has many deps.
   - For Spring components that need the context, prefer **`@SpringBootTest` with `@MockBean`** only when you actually need the container. Most unit tests should *not* boot Spring — instantiate the class directly with mocked deps; it's 10× faster.
   - Do NOT mock concrete classes from the stdlib (`Instant`, `Clock`, `Connection`). The package should expose a `Clock` bean or constructor-injected seam — if it doesn't, flag that as a testability issue.
5. **No external dependencies in unit tests.** No real DB, no network, no filesystem. If the class reaches for them, mock or use Testcontainers in an integration test.
6. **Integration tests live in `*IT.java`** (Maven Failsafe convention). They run on `mvn verify`, not `mvn test`. Use **Testcontainers** for real Postgres/Redis/Redpanda — annotate with `@Testcontainers` and declare `@Container static PostgreSQLContainer<?> postgres = ...`. Spin up the container once per test class (`static`), not per method.
7. **Match the existing test style.** If there are already `*Test.java` files in the same package, mirror their conventions (assertion library, helper naming, fixture setup, `@BeforeEach` patterns).
8. **Assertion library.** Default to **AssertJ** (`assertThat(actual).isEqualTo(...).hasMessage(...)`) — it's fluent, chainable, and produces better failure messages than JUnit's `assertEquals`. Use plain JUnit `assertThrows` for exception assertions when AssertJ's `assertThatThrownBy` isn't a fit. Only fall back to Hamcrest if the surrounding code already uses it.

## Output

- Write the test file as `<SourceClass>Test.java` (unit) or `<SourceClass>IT.java` (integration) in `src/test/java/...` mirroring the source's package path.
- Run `mvn -pl services/<module> test` (unit) or `mvn -pl services/<module> verify` (with integration) to confirm it compiles and passes.
- Report the test count and any Jacoco coverage delta in a single sentence.

## What NOT to do

- Don't generate tests for `*Application.java` Spring Boot entrypoints — they're exercised by the smoke test `contextLoads()` (one per service is enough).
- Don't generate tests for trivial getters/setters/records or constructors with no logic.
- Don't write tests that exercise the framework ("does Spring inject the bean?"). Test our logic, not Spring's.
- Don't write tests against the implementation — test the behavior. If the implementation changes but behavior is the same, the test should still pass.
- Don't add a new mocking library. Stick to Mockito (already in `spring-boot-starter-test`).
- Don't use `@SpringBootTest` if a plain unit test will do. Booting the application context for a class that takes 3 mocked deps is overkill and slow.
