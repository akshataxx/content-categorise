---
name: content-categorise-tests
description: Use whenever working in the content-categorise repository and the user asks to run tests, debug Maven/Spring Boot test failures, diagnose Testcontainers or Docker issues, investigate database test problems, fix Mockito/Java agent failures, verify backend changes, or reason about test profiles. This skill is repo-specific; prefer it for any content-categorise test command even when the user only says "run tests" or "debug the DB problem."
---

# Content Categorise Tests

## Scope

Use this skill only for the `content-categorise` repository:

`/Users/apushpavannan/personal/content-categorise`

This repo has Spring Boot, Maven, PostgreSQL Testcontainers, Mockito inline/static mocking, OpenAI/Whisper profile wiring, and some test-only shutdown configuration. The correct test command and environment matter.

## Default Test Command

Run the full backend test suite with the Mockito Java agent:

```bash
./mvnw -q test -DargLine="-javaagent:/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar"
```

For focused tests:

```bash
./mvnw -q test -DargLine="-javaagent:/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar" -Dtest=ClassNameTest
```

For multiple focused tests:

```bash
./mvnw -q test -DargLine="-javaagent:/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar" -Dtest=ContentApplicationTests,OpenAIProfileWiringTest
```

If the Mockito version changes in `pom.xml`, update the agent path to the matching jar under:

`/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/`

## Docker And Testcontainers

Testcontainers is expected for DB-backed tests. Docker Desktop may be running correctly while the sandboxed Codex shell cannot access the Docker socket.

Known sandbox symptom:

```text
permission denied while trying to connect to the Docker daemon socket at unix:///Users/apushpavannan/.docker/run/docker.sock
operation not permitted
```

When tests use Testcontainers, run the Maven command with escalated permissions so the process can access Docker. Do not conclude Docker or Testcontainers is broken until an escalated `docker version` or escalated Maven test command has failed.

Good Docker sanity check:

```bash
docker version
```

If the local hook blocks commands and asks for `rtk`, rerun the rewritten command through:

```bash
/opt/homebrew/bin/rtk <command>
```

## Mockito Java Agent

This repo needs the Mockito Java agent on the current JDK setup. Without it, Mockito inline self-attach can fail.

Do not work around this by adding:

```text
src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker=mock-maker-subclass
```

That may make some tests pass, but it breaks static/final mocking paths such as Firebase-oriented tests. Prefer the `-DargLine="-javaagent:..."` command.

## Spring Test Profile

Full Spring context tests should explicitly use:

```java
@ActiveProfiles("test")
```

Reason: local environment files can set production-like defaults, including `SPRING_PROFILES_ACTIVE=prod`. Context-loading tests should not accidentally boot prod wiring.

The test profile is expected to provide:

- PostgreSQL Testcontainers datasource configuration.
- Mock OpenAI and Whisper clients by default.
- No real OpenAI calls unless `openai-real` is explicitly active.
- Apple billing settings suitable for local/Xcode-oriented testing.
- Scheduled job poller disabled.
- Short/non-blocking async executor shutdown.

## OpenAI And Whisper Profiles

Expected profile behavior:

- Mock clients: `(dev | test) & !openai-real`
- Real clients: `prod | openai-real`

Use `OpenAIProfileWiringTest` when changing profile annotations or endpoint behavior.

To intentionally hit real OpenAI endpoints in a local test run, activate `openai-real` deliberately and ensure credentials are present. Do not let normal test runs contact real OpenAI endpoints.

## Database Test Gotchas

The test profile should use:

```properties
spring.jpa.hibernate.ddl-auto=create
```

Avoid `create-drop` with Testcontainers in this repo. The DB container is ephemeral, and delayed Hibernate schema-drop work at JVM shutdown can try to reconnect after containers have stopped, causing Hikari timeouts and Surefire dumpstreams.

If a full suite has `*.dumpstream` files under `target/surefire-reports`, inspect whether they are new or stale before treating them as current failures.

Useful checks:

```bash
ls -lt target/surefire-reports | head
```

```bash
grep -R "Tests run:" target/surefire-reports/*.txt
```

All report summaries should show failures/errors/skipped as zero for a clean suite.

## Job Poller And Async Shutdown

The scheduled job poller should be disabled in tests via:

```properties
app.jobs.poller.enabled=false
```

`JobPollerService` is expected to be guarded by:

```java
@ConditionalOnProperty(name = "app.jobs.poller.enabled", havingValue = "true", matchIfMissing = true)
```

The media executor shutdown behavior is configurable:

```properties
app.media-executor.wait-for-tasks-on-shutdown=false
app.media-executor.await-termination-seconds=5
```

These test settings prevent scheduled DB polling and long executor waits from masking the real test failure.

## Debugging Sequence

When tests fail:

1. Reproduce with a focused `-Dtest=...` command and the Mockito Java agent.
2. If Testcontainers or Docker is involved, rerun with Docker access/escalation before diagnosing application code.
3. Read the first failing Surefire report under `target/surefire-reports`.
4. Check whether any dumpstream file is new for the current run.
5. For Spring context failures, confirm `@ActiveProfiles("test")` and profile-specific client wiring.
6. For DB shutdown failures, check `ddl-auto`, container lifecycle, scheduled pollers, and async executor shutdown settings.
7. Run the full suite with the default test command before claiming the repo is green.

## Reporting Back

When reporting test results, include:

- The exact command run.
- Whether it required Docker/escalated access.
- The failing test class and first root-cause error, if any.
- Whether failures are from current reports or stale Surefire dumpstreams.
- The final verification state.
