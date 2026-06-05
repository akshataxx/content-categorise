---
name: content-categorise-runtime-profiles
description: Use whenever working in the content-categorise repository and the user asks about Spring profiles, `.env` files, IntelliJ run configurations, Docker Compose env files, `APP_OPENAI_MODE`, Apple billing modes (`xcode-testing`, `sandbox`, `production`), local versus production setup, or startup failures caused by profile and env mismatches. Trigger even for simple questions like "which env do I need", "how do I run with mocks", "why is Apple private key loading failing", or "which profile should I use locally."
---

# Content Categorise Runtime Profiles

## Scope

Use this skill only for:

`/Users/apushpavannan/personal/content-categorise`

This repo has a specific split between:

- Spring environment profiles: `dev`, `test`, `prod`
- Integration mode: `APP_OPENAI_MODE=real|mock`
- Apple billing environments: `xcode-testing`, `sandbox`, `production`

Keep those concerns separate when answering.

## Authoritative Files

Read these first when answers depend on current repo behavior:

- `src/main/resources/application.properties`
- `src/main/resources/application-dev.properties`
- `src/main/resources/application-prod.properties`
- `src/test/resources/application-test.properties`
- `.env.example`
- `docker-compose.prod.yml`
- `README.md`

## Mental Model

Treat the repo like this:

- `dev` means local development defaults.
- `test` means test-safe defaults.
- `prod` means deployment defaults.
- `APP_OPENAI_MODE` decides whether OpenAI and Whisper are real or mocked.
- `APPLE_ENVIRONMENT` decides whether Apple billing uses local StoreKit testing, Apple's sandbox, or production APIs.

Do not recommend `prod` for routine local work unless the user explicitly wants a prod-like run and understands the consequences.

## Current Defaults

The current repo defaults are:

- `application.properties`
  - imports `./.env` and `./.env.properties`
  - sets `app.openai.mode=${APP_OPENAI_MODE:mock}`
- `application-dev.properties`
  - sets `apple.app-store.environment=${APPLE_ENVIRONMENT:xcode-testing}`
  - sets `app.openai.mode=${APP_OPENAI_MODE:real}`
  - disables noisy Hibernate SQL printing for dev
- `application-prod.properties`
  - sets `app.openai.mode=${APP_OPENAI_MODE:real}`
  - sets `apple.app-store.environment=${APPLE_ENVIRONMENT:production}`
- `application-test.properties`
  - sets `app.openai.mode=mock`
  - sets `apple.app-store.environment=xcode-testing`

That means:

- `dev` defaults to real OpenAI and Whisper plus local Apple StoreKit testing.
- `test` defaults to mock OpenAI and Whisper plus local Apple StoreKit testing.
- `prod` defaults to real OpenAI and Whisper plus production Apple billing.

## Quick Answer Matrix

### Normal Local Development

Recommend:

```dotenv
SPRING_PROFILES_ACTIVE=dev
OPENAI_API_KEY=...
```

This gives:

- real OpenAI and Whisper
- Apple `xcode-testing`
- no Apple `.p8` key required

### Local Development With Mock OpenAI

Recommend:

```dotenv
SPRING_PROFILES_ACTIVE=dev
APP_OPENAI_MODE=mock
```

`OPENAI_API_KEY` is not needed when using mock mode.

### Local Apple Sandbox Testing

Recommend:

```dotenv
SPRING_PROFILES_ACTIVE=dev
OPENAI_API_KEY=...
APPLE_ENVIRONMENT=sandbox
APPLE_BUNDLE_ID=...
APPLE_ISSUER_ID=...
APPLE_KEY_ID=...
APPLE_PRIVATE_KEY_PATH=/absolute/path/on/your/mac/AuthKey_XXXXXX.p8
APPLE_MONTHLY_PRODUCT_ID=premium_monthly
```

For local runs, `APPLE_PRIVATE_KEY_PATH` must be a real path on the host machine. Do not use container paths like `/app/secrets/...` in IntelliJ or `spring-boot:run`.

### Local Xcode StoreKit Testing

Recommend:

```dotenv
SPRING_PROFILES_ACTIVE=dev
OPENAI_API_KEY=...
APPLE_ENVIRONMENT=xcode-testing
APPLE_BUNDLE_ID=...
APPLE_MONTHLY_PRODUCT_ID=premium_monthly
```

Do not require `APPLE_ISSUER_ID`, `APPLE_KEY_ID`, or `APPLE_PRIVATE_KEY_PATH` for this mode.

### Docker Production

Recommend `.env.prod` shaped like:

```dotenv
SPRING_PROFILES_ACTIVE=prod
OPENAI_API_KEY=...
APP_OPENAI_MODE=real

APPLE_ENVIRONMENT=production
APPLE_BUNDLE_ID=...
APPLE_ISSUER_ID=...
APPLE_KEY_ID=...
APPLE_PRIVATE_KEY_HOST_PATH=/opt/content-categorise/secrets/AuthKey_XXXXXX.p8
APPLE_PRIVATE_KEY_PATH=/app/secrets/apple-app-store-key.p8
APPLE_MONTHLY_PRODUCT_ID=premium_monthly
```

The important distinction:

- `APPLE_PRIVATE_KEY_HOST_PATH`: path on the VM
- `APPLE_PRIVATE_KEY_PATH`: path inside the container

`docker-compose.prod.yml` mounts the host file into the container using those two values.

## IntelliJ Guidance

When the user asks how to control which env file IntelliJ uses, recommend one run configuration per mode with:

- `SPRING_PROFILES_ACTIVE=dev` or `prod`
- `SPRING_CONFIG_IMPORT=optional:file:./.env.local`
- `SPRING_CONFIG_IMPORT=optional:file:./.env.sandbox`
- `SPRING_CONFIG_IMPORT=optional:file:./.env.prod`

Suggested run configuration names:

- `Local Dev`
- `Local Sandbox`
- `Prod-Like`

## Apple Failure Triage

When Apple billing startup fails, check in this order:

1. Which Spring profile is active?
2. What is `APPLE_ENVIRONMENT`?
3. Is the app running on the host or inside Docker?
4. Does `APPLE_PRIVATE_KEY_PATH` refer to a host path or a container path?
5. Does the file actually exist at that path?

Common interpretations:

- `prod` plus missing `APPLE_PRIVATE_KEY_PATH`
  - Production Apple billing is enabled and refusing to start without a key.
- `prod` plus `APPLE_ENVIRONMENT=xcode-testing`
  - Invalid combination by design.
- Local run plus `APPLE_PRIVATE_KEY_PATH=/app/secrets/...`
  - Container path used outside Docker.
- Docker run plus host path used as `APPLE_PRIVATE_KEY_PATH`
  - Host path leaked into the container setting.

Prefer the smallest fix:

- For normal local work, switch to `SPRING_PROFILES_ACTIVE=dev`.
- For local Xcode StoreKit, use `APPLE_ENVIRONMENT=xcode-testing`.
- For Docker prod, mount the key and keep `APPLE_PRIVATE_KEY_PATH` as the container path.

## OpenAI Mode Guidance

Do not recommend extra Spring profiles for OpenAI mode. This repo intentionally moved away from `openai-real`.

Answer using:

- `APP_OPENAI_MODE=real`
- `APP_OPENAI_MODE=mock`

When the user asks whether the old extra profile was weird, say yes in a kind, precise way:

- profiles are better for environments
- properties are better for feature and integration toggles

## Response Style

When using this skill:

- lead with the simplest valid env snippet
- explain whether the path is local-host or container-local
- mention the active profile explicitly
- call out whether Apple `.p8` material is needed or not
- keep answers concise unless the user asks for deployment detail

## Example Answers

**Example 1:**
Input: "what envs do I need for local testing"
Output:

```text
Use `SPRING_PROFILES_ACTIVE=dev`.
Add `OPENAI_API_KEY=...`.
That gives you real OpenAI and Apple `xcode-testing` by default, so no Apple `.p8` is needed.
Add `APP_OPENAI_MODE=mock` only if you want mock OpenAI.
```

**Example 2:**
Input: "why is `/app/secrets/...p8` failing in IntelliJ"
Output:

```text
That path only makes sense inside Docker.
For IntelliJ or local Spring Boot runs, either remove `APPLE_PRIVATE_KEY_PATH` and stay on `xcode-testing`, or point it at a real absolute path on your Mac and use Apple `sandbox`.
```

**Example 3:**
Input: "what should go in `.env.prod` for Apple billing"
Output:

```text
Set `APPLE_PRIVATE_KEY_HOST_PATH` to the real VM path and `APPLE_PRIVATE_KEY_PATH` to `/app/secrets/apple-app-store-key.p8`.
The first is for Docker to mount; the second is what Spring reads inside the container.
```
