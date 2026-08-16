# TikTok Ingestion Resilience Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `subagent-driven-development` (recommended) or `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep yt-dlp with `curl_cffi` as the primary TikTok ingestion route and add a secure official TikTok embed-page fallback that runs only after an identifiable TikTok anti-bot challenge failure.

**Architecture:** Move TikTok-specific orchestration out of `VideoService` into a focused `TikTokIngestionService`. The service runs the primary yt-dlp path, classifies failures through `TikTokChallengeDetector`, and invokes `TikTokEmbedFallbackClient` at most once for a confirmed challenge. The fallback validates the canonical numeric TikTok ID, parses current official embed data, downloads an ephemeral signed CDN URL through strict allow-lists, extracts audio locally, and returns the existing `VideoMetadata`/`ProcessedVideoFiles` shape without persisting or exposing the signed URL.

**Status:** Planning only. This document does not authorize implementation, deployment, or external writes.

---

## Evidence gathered on 2026-08-13

### Existing pipeline

`src/main/java/com/app/categorise/domain/service/VideoService.java` currently:

- Runs TikTok media download and metadata extraction together using `--write-info-json`.
- Uses the invalid case-sensitive target `Chrome-136:Macos-15`.
- Hard-codes `api22-normal-c-useast1a.tiktokv.com`.
- Still contains cookie-file support, including production Compose mounts, despite the product decision to support no browser cookies.
- Stores the submitted URL in `base_transcripts.video_url`.
- Stores stable identity in `(platform, platform_video_id)`, protected by the partial unique index introduced by `V23__add_platform_video_id.sql`.
- Converts every `VideoProcessingException` into HTTP 422, even though the asynchronous job layer considers most unknown/upstream failures transient.

`DefaultProcessExecutor` combines stdout and stderr but exposes failures only as generic `RuntimeException`s. Precise challenge classification therefore requires a structured process exception.

The scheduled rebuild workflow verifies the new yt-dlp version, but does not verify that `curl_cffi` is importable or that the configured impersonation target is supported by the built image.

### Live official embed response

The public video from [yt-dlp issue 17403](https://github.com/yt-dlp/yt-dlp/issues/17403), ID `7668090902816017671`, was queried through the official endpoint:

```text
https://www.tiktok.com/embed/v2/7668090902816017671
```

The endpoint returned HTTP 200 HTML with JSON in:

```text
script#__FRONTITY_CONNECT_STATE__
```

The current JSON location is:

```text
source.data["/embed/v2/{id}"].videoData
```

Relevant fields observed:

```text
itemInfos.id
itemInfos.text
itemInfos.createTime
itemInfos.video.urls[]
itemInfos.video.videoMeta.duration
itemInfos.video.videoMeta.width
itemInfos.video.videoMeta.height

authorInfos.userId
authorInfos.uniqueId
authorInfos.nickName
authorInfos.secUid
```

Map them to the existing `VideoMetadata` contract as follows:

| `VideoMetadata` field | Embed field |
|---|---|
| `id` | `itemInfos.id` |
| `title` | First 72 Unicode code points of `itemInfos.text`, matching current yt-dlp behavior |
| `description` | `itemInfos.text` |
| `duration` | `itemInfos.video.videoMeta.duration` |
| `uploadedEpoch` | Parsed `itemInfos.createTime` |
| `accountId` | `authorInfos.userId` |
| `account` | `authorInfos.uniqueId` |
| `identifierId` | `authorInfos.secUid` |
| `identifier` | `authorInfos.nickName` |
| `extractor` | Literal `TikTok` |

The tested signed media candidate:

- Used HTTPS on a subdomain of `tiktokcdn.com`.
- Returned `206 video/mp4` for a 1 KiB range request.
- Required no redirect.
- Worked both with and without `Referer: https://www.tiktok.com/`.
- Encoded an eight-hex expiry timestamp in the path, approximately 48 hours after issuance.
- Returned HTTP 403 after that path component was deliberately changed to an expired value.

Changing the path also invalidates the signature, so the exact status for natural expiry remains unknown. Treat HTTP 401, 403, 404, and 410 as an unusable signed URL, refresh the embed once, then fail retryably.

A deliberately nonexistent numeric ID returned HTTP 400 with `isError=true`, `errorCode=10204`, and `pageName=video_v2_error`. Do not interpret embed status alone as proof that the submitted URL is invalid; upstream availability and regional blocking can produce indistinguishable failures.

## Design decisions

### Component boundary

Create a focused TikTok vertical slice:

```text
VideoService
  -> TikTokIngestionService
       -> yt-dlp primary route
       -> TikTokChallengeDetector
       -> TikTokEmbedFallbackClient
            -> TikTokEmbedPageParser
            -> validated TikTok CDN download
       -> ffmpeg audio extraction
```

Responsibilities:

- `TikTokIngestionService`: Own primary execution and the single permitted fallback decision.
- `TikTokChallengeDetector`: Classify bounded yt-dlp output into a low-cardinality challenge reason or no match.
- `TikTokEmbedFallbackClient`: Perform official embed and CDN requests with explicit redirect, host, size, timeout, and cleanup controls.
- `TikTokEmbedPageParser`: Parse only the current Frontity JSON structure and map it to safe metadata.
- `TikTokVideoIdentityResolver`: Validate TikTok input URLs, resolve approved short links, and derive the stable numeric video ID.
- `TikTokIngestionProperties`: Bind validated configuration with safe defaults.
- `ProcessExecutionException`: Preserve exit code, timeout state, and bounded output without exposing command arguments.

`VideoService` must not parse HTML, inspect CDN URLs, or know fallback-specific fields.

### End-to-end flow

1. Validate the submitted TikTok URL and derive its stable numeric video ID.
2. Check canonical deduplication before fetching media whenever the ID is available directly from the URL.
3. Run the existing combined yt-dlp audio and metadata command.
4. On success, return the existing `ProcessedVideoFiles` result and record primary success.
5. On failure, classify the structured process output.
6. If it is not a recognized challenge, propagate a correctly typed failure without invoking fallback.
7. If it is a recognized challenge, fetch `https://www.tiktok.com/embed/v2/{id}` exactly once.
8. Parse metadata and require `itemInfos.id` to equal the derived input ID.
9. Apply existing metadata and duration validation before downloading CDN media.
10. Download an approved media candidate into the request's temporary directory.
11. If the signed URL returns 401, 403, 404, or 410, refresh the embed once and try one newly issued candidate once.
12. Extract MP3 locally using ffmpeg.
13. Return safe `VideoMetadata` and audio artifacts to the existing transcription/deduplication pipeline.
14. Delete temporary media on success, failure, or cancellation.

The signed URL is an ephemeral local variable inside `TikTokEmbedFallbackClient`. It is never serialized, logged, persisted, returned through an API, used as `videoUrl`, or used as `platformVideoId`.

### Exact fallback trigger

Fallback requires all of these conditions:

- The validated input platform is TikTok.
- yt-dlp exits unsuccessfully.
- Captured output contains the TikTok extractor marker `[TikTok]`.
- Captured output contains one of these current challenge-path messages:

```text
Unexpected response from webpage request
Unable to extract challenge data
Unable to solve JS challenge
Unable to extract universal data for rehydration
```

The detector returns a stable enum such as:

```java
enum TikTokChallengeReason {
    UNEXPECTED_WEBPAGE,
    MISSING_CHALLENGE_DATA,
    CHALLENGE_SOLVE_FAILED,
    MISSING_REHYDRATION_DATA
}
```

The following never trigger fallback:

- Unsupported, malformed, or non-HTTPS URL.
- Private, deleted, age-restricted, or login-required content.
- An unavailable/misspelled impersonation target.
- A bare HTTP 403 or 429 without both the TikTok extractor marker and a challenge message.
- DNS, connect, socket, or process timeout.
- ffmpeg/audio extraction failure.
- Missing audio stream or unsupported media format.
- Empty or malformed yt-dlp metadata.
- Any generic yt-dlp internal failure.

A non-triggering failure may still be retryable; skipping fallback must not turn an upstream availability problem into an invalid-URL error.

### Trust boundary and SSRF controls

#### TikTok input URL

Require:

- HTTPS.
- No userinfo.
- Port absent or 443.
- No IP literal.
- Exact approved input host:
  - `www.tiktok.com`
  - `m.tiktok.com`
  - `vm.tiktok.com`
  - `vt.tiktok.com`
- A recognized path rather than an arbitrary TikTok-hosted URL.

Recognize direct ID paths:

```text
/@{creator}/video/{id}
/embed/v2/{id}
/player/v1/{id}
```

Validate the derived ID with:

```regex
^[1-9][0-9]{14,21}$
```

Resolve `vm.tiktok.com` and `vt.tiktok.com` links with automatic redirects disabled. Permit at most three hops, validate every hop against the exact TikTok input-host allow-list, and require the final URI to contain a recognized direct video path.

#### Embed request

- Construct the URI internally from the validated numeric ID.
- Request only exact host `www.tiktok.com` over HTTPS port 443.
- Disable automatic redirects; reject any embed redirect unless it remains on exact host `www.tiktok.com` and the same `/embed/v2/{id}` route.
- Limit HTML to 2 MiB.
- Require HTML content type before parsing.
- Require the returned `itemInfos.id` to equal the requested ID.

#### CDN request

- Require HTTPS and port absent or 443.
- Require host equal to `tiktokcdn.com` or ending in `.tiktokcdn.com`.
- Reject userinfo, fragments, IP literals, private/reserved resolved addresses, and lookalike suffixes.
- Disable automatic redirects and validate every hop; permit at most three approved CDN hops.
- Reject redirects to `tiktok.com`, `tiktokv.com`, generic cloud storage, or any other root until reviewed and added in code.
- Limit media to 150 MiB using both `Content-Length` and counted streamed bytes.
- Require successful 2xx and an expected media content type.
- Send `Referer: https://www.tiktok.com/` conservatively; record that the 2026-08-13 test did not require it.
- Do not add a hard-coded plain browser User-Agent.
- Redact the complete query and path from logs and exceptions; log only a low-cardinality outcome and optionally the validated registrable suffix `tiktokcdn.com`.

Embed and CDN allow-lists stay hard-coded security boundaries. They are not environment-configurable.

### Configuration

Add these properties to `src/main/resources/application.properties`:

```properties
app.tiktok.ytdlp.impersonate-target=${APP_TIKTOK_YTDLP_IMPERSONATE_TARGET:chrome}
app.tiktok.ytdlp.api-hostname=${APP_TIKTOK_YTDLP_API_HOSTNAME:}
app.tiktok.embed-fallback.enabled=${APP_TIKTOK_EMBED_FALLBACK_ENABLED:true}
app.tiktok.embed-fallback.connect-timeout-seconds=${APP_TIKTOK_EMBED_CONNECT_TIMEOUT_SECONDS:10}
app.tiktok.embed-fallback.read-timeout-seconds=${APP_TIKTOK_EMBED_READ_TIMEOUT_SECONDS:30}
app.tiktok.embed-fallback.max-html-bytes=${APP_TIKTOK_EMBED_MAX_HTML_BYTES:2097152}
app.tiktok.embed-fallback.max-media-bytes=${APP_TIKTOK_EMBED_MAX_MEDIA_BYTES:157286400}
```

Defaults and validation:

- `chrome` is lowercase and versionless, allowing yt-dlp to resolve an available Chrome target instead of pinning unreliable Chrome 136.
- Blank API hostname omits `tiktok:api_hostname`, letting the installed yt-dlp version use its maintained default.
- A nonblank API hostname must be a hostname under `tiktokv.com`, with no scheme, path, port, whitespace, comma, semicolon, or extractor-argument separator.
- The fallback enable flag is the operational kill switch.

Remove cookie support from `VideoService`, application properties, `.env.example`, and `docker-compose.prod.yml` because cookies are explicitly outside product scope.

Operational target selection from the deployed image:

```bash
docker compose -f docker-compose.prod.yml exec -T app \
  yt-dlp --list-impersonate-targets
```

Validate a candidate without contacting TikTok:

```bash
docker compose -f docker-compose.prod.yml exec -T app sh -ceu '
  yt-dlp --ignore-config \
    --impersonate "$APP_TIKTOK_YTDLP_IMPERSONATE_TARGET" \
    --list-extractors >/dev/null
'
```

Support does not imply reliability. Before changing production, test the candidate from the deployed VM against the controlled TikTok canary and compare primary/fallback metrics with the previous image.

### Error semantics

Add `RetryableVideoProcessingException extends VideoProcessingException` and use it when:

- A confirmed challenge is followed by fallback failure.
- TikTok blocks the deployment IP.
- A generic upstream/network failure prevents retrieval.
- An issued CDN URL expires or repeatedly fails after the single refresh.

Map it to HTTP 503 with:

```text
TikTok is temporarily unavailable. Please try again later.
```

Return a conservative `Retry-After` header. Preserve HTTP 422 for malformed, unsupported, private, deleted, over-duration, or otherwise permanent user-input failures. Update `TranscriptionJobService` to classify the typed retryable exception before its legacy message matching.

### Observability

Emit structured events:

```text
event=tiktok_primary_success
event=tiktok_challenge_detected reason=<TikTokChallengeReason>
event=tiktok_fallback_attempted
event=tiktok_fallback_success
event=tiktok_fallback_failure reason=<low-cardinality-enum>
```

Emit counters:

```text
tiktok.ingestion.primary.success
tiktok.ingestion.challenge.detected
tiktok.ingestion.fallback.attempted
tiktok.ingestion.fallback.success
tiktok.ingestion.fallback.failure{reason=...}
```

Never tag metrics with URL, video ID, signed host, exception text, or user ID.

At startup, log sanitized diagnostics:

- yt-dlp version.
- `curl_cffi` version.
- Configured impersonation target.
- Whether target validation succeeded.
- API hostname as `upstream-default` or sanitized hostname.

Fail application readiness/startup when `curl_cffi` is missing or the configured impersonation target is unsupported. The image workflow must catch both cases before deployment as well.

## File map

### Create

- `src/main/java/com/app/categorise/config/TikTokIngestionProperties.java` — validated properties and safe defaults.
- `src/main/java/com/app/categorise/config/YtDlpCapabilityVerifier.java` — startup version and impersonation capability check.
- `src/main/java/com/app/categorise/data/client/tiktok/TikTokEmbedFallbackClient.java` — embed/CDN HTTP boundary.
- `src/main/java/com/app/categorise/data/client/tiktok/TikTokEmbedPageParser.java` — Frontity JSON parser.
- `src/main/java/com/app/categorise/data/client/tiktok/TikTokEmbedMedia.java` — safe metadata plus ephemeral media URI internal value.
- `src/main/java/com/app/categorise/data/client/tiktok/TikTokEmbedFallbackException.java` — sanitized, typed fallback failure.
- `src/main/java/com/app/categorise/domain/model/TikTokChallengeReason.java` — stable challenge enum.
- `src/main/java/com/app/categorise/domain/service/TikTokChallengeDetector.java` — strict trigger classifier.
- `src/main/java/com/app/categorise/domain/service/TikTokIngestionService.java` — primary/fallback orchestration.
- `src/main/java/com/app/categorise/domain/service/TikTokVideoIdentityResolver.java` — input and redirect validation.
- `src/main/java/com/app/categorise/exception/RetryableVideoProcessingException.java` — retryable user-facing availability failure.
- `src/main/java/com/app/categorise/util/processExecutor/ProcessExecutionException.java` — structured process failure.
- Unit tests matching each class above.
- Sanitized HTML fixtures under `src/test/resources/fixtures/tiktok/`.

### Modify

- `src/main/java/com/app/categorise/domain/service/VideoService.java`
- `src/main/java/com/app/categorise/domain/service/TranscriptionJobService.java`
- `src/main/java/com/app/categorise/application/internal/ProcessedVideoFiles.java`
- `src/main/java/com/app/categorise/exception/GlobalExceptionHandler.java`
- `src/main/java/com/app/categorise/util/processExecutor/ProcessExecutor.java`
- `src/main/java/com/app/categorise/util/processExecutor/DefaultProcessExecutor.java`
- `src/main/resources/application.properties`
- `src/test/resources/application-test.properties`
- `src/test/java/com/app/categorise/domain/service/VideoServiceTest.java`
- `src/test/java/com/app/categorise/domain/service/TranscriptionJobServiceTest.java`
- `src/test/java/com/app/categorise/util/processExecutor/DefaultProcessExecutorTest.java`
- `src/test/java/com/app/categorise/util/processExecutor/TestProcessExecutor.java`
- `Dockerfile`
- `.github/workflows/scheduled-rebuild.yml`
- `docker-compose.prod.yml`
- `.env.example`

No database migration is required. Existing `video_url`, `platform`, and `platform_video_id` fields already provide the correct identity model.

## TDD task sequence

### Task 1: Structured process failures

**Files:**

- Create: `src/main/java/com/app/categorise/util/processExecutor/ProcessExecutionException.java`
- Modify: `src/main/java/com/app/categorise/util/processExecutor/ProcessExecutor.java`
- Modify: `src/main/java/com/app/categorise/util/processExecutor/DefaultProcessExecutor.java`
- Modify: `src/test/java/com/app/categorise/util/processExecutor/DefaultProcessExecutorTest.java`
- Modify: `src/test/java/com/app/categorise/util/processExecutor/TestProcessExecutor.java`

- [ ] **Step 1: Write failing executor tests**

Cover nonzero exit code, bounded output, timeout state, absent command arguments, and preserved thread interruption. Assert the typed exception's accessors rather than parsing its message.

- [ ] **Step 2: Run focused tests and confirm red**

```bash
./mvnw -q test \
  -DargLine="-javaagent:/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar" \
  -Dtest=DefaultProcessExecutorTest
```

Expected: compilation or assertion failures because `ProcessExecutionException` and its typed fields do not exist.

- [ ] **Step 3: Implement the structured exception**

Expose only:

```java
public final class ProcessExecutionException extends RuntimeException {
    private final Integer exitCode;
    private final boolean timedOut;
    private final String capturedOutput;
}
```

Keep output bounded at 4096 characters. The exception message contains a generic failure category and exit code only; it contains no command arguments or URL.

- [ ] **Step 4: Update the test executor**

Allow queued outputs/exceptions so orchestration tests can model a failed yt-dlp attempt followed by ffmpeg success without global mutable behavior.

- [ ] **Step 5: Run focused tests and confirm green**

Run the command from Step 2. Expected: `DefaultProcessExecutorTest` passes with zero failures and errors.

- [ ] **Step 6: Commit the executor slice**

```bash
git add src/main/java/com/app/categorise/util/processExecutor \
  src/test/java/com/app/categorise/util/processExecutor
git commit -m "refactor: expose structured process failures"
```

### Task 2: TikTok identity validation

**Files:**

- Create: `src/main/java/com/app/categorise/domain/service/TikTokVideoIdentityResolver.java`
- Create: `src/test/java/com/app/categorise/domain/service/TikTokVideoIdentityResolverTest.java`

- [ ] **Step 1: Write failing identity tests**

Cover direct video, embed, and player URLs; approved short-link redirects; invalid scheme; userinfo; non-443 port; IP literal; lookalike host; arbitrary TikTok path; overlong ID; zero-prefixed ID; redirect loop; off-list redirect; and final ID extraction.

- [ ] **Step 2: Run the identity test and confirm red**

```bash
./mvnw -q test \
  -DargLine="-javaagent:/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar" \
  -Dtest=TikTokVideoIdentityResolverTest
```

Expected: compilation failure because the resolver does not exist.

- [ ] **Step 3: Implement exact allow-lists and ID validation**

Use `URI`, exact/suffix comparisons with dot-boundary checks, automatic redirects disabled, a three-hop maximum, and `^[1-9][0-9]{14,21}$`.

- [ ] **Step 4: Run the identity test and confirm green**

Run the command from Step 2. Expected: all identity and SSRF cases pass.

- [ ] **Step 5: Commit the identity slice**

```bash
git add src/main/java/com/app/categorise/domain/service/TikTokVideoIdentityResolver.java \
  src/test/java/com/app/categorise/domain/service/TikTokVideoIdentityResolverTest.java
git commit -m "feat: validate canonical TikTok video identity"
```

### Task 3: Embed fixtures and parser

**Files:**

- Create: `src/main/java/com/app/categorise/data/client/tiktok/TikTokEmbedMedia.java`
- Create: `src/main/java/com/app/categorise/data/client/tiktok/TikTokEmbedPageParser.java`
- Create: `src/test/java/com/app/categorise/data/client/tiktok/TikTokEmbedPageParserTest.java`
- Create: `src/test/resources/fixtures/tiktok/embed-v2-success.html`
- Create: `src/test/resources/fixtures/tiktok/embed-v2-missing-state.html`
- Create: `src/test/resources/fixtures/tiktok/embed-v2-malformed-json.html`
- Create: `src/test/resources/fixtures/tiktok/embed-v2-missing-video-data.html`
- Create: `src/test/resources/fixtures/tiktok/embed-v2-unapproved-media-host.html`
- Create: `src/test/resources/fixtures/tiktok/embed-v2-id-mismatch.html`

- [ ] **Step 1: Add sanitized fixtures**

Preserve the observed `__FRONTITY_CONNECT_STATE__` script ID and `source.data["/embed/v2/{id}"].videoData` shape. Replace real signatures, creator text, and signed query values with inert test values.

- [ ] **Step 2: Write failing parser tests**

Assert every `VideoMetadata` field mapping, Unicode-safe 72-code-point title truncation, ID equality, required duration/create time, ordered media candidates, malformed data failures, and rejection of an unapproved media host.

- [ ] **Step 3: Run the parser test and confirm red**

```bash
./mvnw -q test \
  -DargLine="-javaagent:/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar" \
  -Dtest=TikTokEmbedPageParserTest
```

Expected: compilation failure because parser types do not exist.

- [ ] **Step 4: Implement parser and safe internal result**

Parse the named script with a bounded HTML parser or a narrowly scoped script-element extractor followed by Jackson. Return existing `VideoMetadata` plus an internal ordered list of candidate `URI`s. Override `toString()` so it never renders media URIs.

- [ ] **Step 5: Run the parser test and confirm green**

Run the command from Step 3. Expected: every success and malformed fixture case passes.

- [ ] **Step 6: Commit the parser slice**

```bash
git add src/main/java/com/app/categorise/data/client/tiktok \
  src/test/java/com/app/categorise/data/client/tiktok \
  src/test/resources/fixtures/tiktok
git commit -m "feat: parse official TikTok embed metadata"
```

### Task 4: Secure embed and CDN client

**Files:**

- Create: `src/main/java/com/app/categorise/data/client/tiktok/TikTokEmbedFallbackClient.java`
- Create: `src/main/java/com/app/categorise/data/client/tiktok/TikTokEmbedFallbackException.java`
- Create: `src/test/java/com/app/categorise/data/client/tiktok/TikTokEmbedFallbackClientTest.java`

- [ ] **Step 1: Write failing HTTP-boundary tests**

Inject a JDK `HttpClient` test double with deterministic `HttpResponse<InputStream>` objects. Cover exact embed host, approved CDN suffix, unapproved host, off-list redirect, redirect limit, oversized HTML, oversized media, content type, CDN 403 refresh, repeated failure, cleanup, and signed-URL redaction.

- [ ] **Step 2: Run the client test and confirm red**

```bash
./mvnw -q test \
  -DargLine="-javaagent:/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar" \
  -Dtest=TikTokEmbedFallbackClientTest
```

Expected: compilation failure because the client does not exist.

- [ ] **Step 3: Implement the embed request**

Build `https://www.tiktok.com/embed/v2/{validatedId}` internally. Use `HttpClient.Redirect.NEVER`, configured timeouts, an explicit byte-counting body reader, and the parser from Task 3.

- [ ] **Step 4: Implement the CDN request**

Validate every candidate and redirect before sending. Stream to a temporary file with a 150 MiB hard limit. On 401/403/404/410, refresh the embed once and retry once with the new candidate set. Delete partial files on every failed path.

- [ ] **Step 5: Run the client test and confirm green**

Run the command from Step 2. Expected: all HTTP, SSRF, expiry, cleanup, and redaction cases pass.

- [ ] **Step 6: Commit the HTTP slice**

```bash
git add src/main/java/com/app/categorise/data/client/tiktok \
  src/test/java/com/app/categorise/data/client/tiktok
git commit -m "feat: securely download TikTok embed media"
```

### Task 5: Challenge classifier

**Files:**

- Create: `src/main/java/com/app/categorise/domain/model/TikTokChallengeReason.java`
- Create: `src/main/java/com/app/categorise/domain/service/TikTokChallengeDetector.java`
- Create: `src/test/java/com/app/categorise/domain/service/TikTokChallengeDetectorTest.java`

- [ ] **Step 1: Write failing classifier table tests**

For each approved message, require `[TikTok]` and assert the exact enum. Add negative rows for bare 403, timeout, login/private, unsupported URL, impersonation target unavailable, ffmpeg, missing format, malformed metadata, and the same challenge text without `[TikTok]`.

- [ ] **Step 2: Run classifier tests and confirm red**

```bash
./mvnw -q test \
  -DargLine="-javaagent:/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar" \
  -Dtest=TikTokChallengeDetectorTest
```

Expected: compilation failure because classifier types do not exist.

- [ ] **Step 3: Implement deterministic classification**

Accept `ProcessExecutionException`, inspect only its bounded captured output, require both the extractor marker and one exact challenge phrase, and return `Optional<TikTokChallengeReason>`.

- [ ] **Step 4: Run classifier tests and confirm green**

Run the command from Step 2. Expected: every positive and negative table row passes.

- [ ] **Step 5: Commit the classifier slice**

```bash
git add src/main/java/com/app/categorise/domain/model/TikTokChallengeReason.java \
  src/main/java/com/app/categorise/domain/service/TikTokChallengeDetector.java \
  src/test/java/com/app/categorise/domain/service/TikTokChallengeDetectorTest.java
git commit -m "feat: classify TikTok anti-bot challenges"
```

### Task 6: Configurable primary TikTok route

**Files:**

- Create: `src/main/java/com/app/categorise/config/TikTokIngestionProperties.java`
- Create: `src/test/java/com/app/categorise/config/TikTokIngestionPropertiesTest.java`
- Modify: `src/main/resources/application.properties`
- Modify: `src/test/resources/application-test.properties`
- Modify: `.env.example`

- [ ] **Step 1: Write failing property validation tests**

Cover the `chrome` default, blank API hostname, lowercase target requirement, invalid hostname suffix, scheme/path/port/separator rejection, byte limits, timeouts, and fallback flag.

- [ ] **Step 2: Run property tests and confirm red**

```bash
./mvnw -q test \
  -DargLine="-javaagent:/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar" \
  -Dtest=TikTokIngestionPropertiesTest
```

Expected: compilation failure because property binding does not exist.

- [ ] **Step 3: Implement validated configuration**

Use `@ConfigurationProperties(prefix = "app.tiktok")` with constructor/record validation. Expose a method that returns no extractor argument for blank API hostname and exactly `tiktok:api_hostname=<validated-host>` otherwise.

- [ ] **Step 4: Document environment variables**

Add the properties and `.env.example` entries from the Configuration section. Do not add cookie or proxy properties.

- [ ] **Step 5: Run property tests and confirm green**

Run the command from Step 2. Expected: defaults and all invalid inputs behave as specified.

- [ ] **Step 6: Commit the configuration slice**

```bash
git add src/main/java/com/app/categorise/config/TikTokIngestionProperties.java \
  src/test/java/com/app/categorise/config/TikTokIngestionPropertiesTest.java \
  src/main/resources/application.properties \
  src/test/resources/application-test.properties \
  .env.example
git commit -m "feat: configure TikTok ingestion behavior"
```

### Task 7: TikTok ingestion orchestration

**Files:**

- Create: `src/main/java/com/app/categorise/domain/service/TikTokIngestionService.java`
- Create: `src/test/java/com/app/categorise/domain/service/TikTokIngestionServiceTest.java`
- Modify: `src/main/java/com/app/categorise/application/internal/ProcessedVideoFiles.java`
- Modify: `src/main/java/com/app/categorise/domain/service/VideoService.java`
- Modify: `src/test/java/com/app/categorise/domain/service/VideoServiceTest.java`

- [ ] **Step 1: Write failing orchestration tests**

Assert:

- Primary success never invokes fallback.
- Challenge failure invokes fallback exactly once.
- Generic yt-dlp failure never invokes fallback.
- Timeout never invokes fallback.
- ffmpeg failure after fallback never invokes fallback again.
- Fallback metadata is validated before CDN download.
- Fallback media is converted to MP3.
- Temporary artifacts are always cleaned.
- The fallback kill switch produces a retryable failure after a confirmed challenge.

- [ ] **Step 2: Run orchestration tests and confirm red**

```bash
./mvnw -q test \
  -DargLine="-javaagent:/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar" \
  -Dtest=TikTokIngestionServiceTest,VideoServiceTest
```

Expected: compilation/assertion failures because orchestration still lives in `VideoService`.

- [ ] **Step 3: Implement the primary command builder**

Preserve the existing TikTok format selection, audio conversion settings, `--write-info-json`, `--` argument separator, and configured timeouts. Use the configured lowercase impersonation target and append `--extractor-args` only when API hostname is nonblank.

- [ ] **Step 4: Implement fallback orchestration**

Catch only `ProcessExecutionException` from the yt-dlp primary call. Invoke the classifier; call the fallback client exactly once only on a match. Run ffmpeg locally on the downloaded media and return safe metadata plus MP3 through `ProcessedVideoFiles`.

- [ ] **Step 5: Delegate the TikTok branch from `VideoService`**

Keep non-TikTok behavior unchanged. Reuse the existing `validateMetadata`, `validateDurationLimit`, `findCanonicalMatch`, mapper, and conflict handling.

- [ ] **Step 6: Run orchestration tests and confirm green**

Run the command from Step 2. Expected: all primary/fallback gating and existing `VideoService` tests pass.

- [ ] **Step 7: Commit the orchestration slice**

```bash
git add src/main/java/com/app/categorise/domain/service/TikTokIngestionService.java \
  src/main/java/com/app/categorise/domain/service/VideoService.java \
  src/main/java/com/app/categorise/application/internal/ProcessedVideoFiles.java \
  src/test/java/com/app/categorise/domain/service/TikTokIngestionServiceTest.java \
  src/test/java/com/app/categorise/domain/service/VideoServiceTest.java
git commit -m "feat: add challenge-gated TikTok fallback"
```

### Task 8: Canonical identity and deduplication regression coverage

**Files:**

- Modify: `src/test/java/com/app/categorise/domain/service/VideoServiceTest.java`
- Modify: `src/test/java/com/app/categorise/data/repository/BaseTranscriptRepositoryTest.java`

- [ ] **Step 1: Write failing identity regression tests**

Assert the submitted TikTok URL remains `BaseTranscriptEntity.videoUrl`, the stable embed ID becomes `platformVideoId`, platform remains `TIKTOK`, the signed CDN URI is absent from entities and response DTOs, and two submitted TikTok URL forms sharing an ID reuse one canonical row.

- [ ] **Step 2: Run identity regression tests**

```bash
./mvnw -q test \
  -DargLine="-javaagent:/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar" \
  -Dtest=VideoServiceTest,BaseTranscriptRepositoryTest
```

Expected before required orchestration adjustments: at least the fallback identity path fails. Repository tests require Docker/Testcontainers access.

- [ ] **Step 3: Make the minimal identity fixes**

Pass only the submitted URL and validated numeric ID into the existing mapper. Do not add a signed-URL field or database migration.

- [ ] **Step 4: Run identity regression tests and confirm green**

Run the command from Step 2 with Docker access. Expected: zero failures and one canonical base transcript per `(TIKTOK, id)`.

- [ ] **Step 5: Commit the identity regression slice**

```bash
git add src/test/java/com/app/categorise/domain/service/VideoServiceTest.java \
  src/test/java/com/app/categorise/data/repository/BaseTranscriptRepositoryTest.java \
  src/main/java/com/app/categorise/domain/service/VideoService.java \
  src/main/java/com/app/categorise/domain/service/TikTokIngestionService.java
git commit -m "test: preserve canonical TikTok identity"
```

### Task 9: Retryable errors and observability

**Files:**

- Create: `src/main/java/com/app/categorise/exception/RetryableVideoProcessingException.java`
- Modify: `src/main/java/com/app/categorise/exception/GlobalExceptionHandler.java`
- Modify: `src/main/java/com/app/categorise/domain/service/TranscriptionJobService.java`
- Modify: `src/main/java/com/app/categorise/domain/service/TikTokIngestionService.java`
- Create: `src/test/java/com/app/categorise/exception/GlobalExceptionHandlerTest.java` if no focused handler test exists.
- Modify: `src/test/java/com/app/categorise/domain/service/TranscriptionJobServiceTest.java`
- Modify: `src/test/java/com/app/categorise/domain/service/TikTokIngestionServiceTest.java`

- [ ] **Step 1: Write failing error-semantic tests**

Assert retryable TikTok availability errors map to HTTP 503 plus `Retry-After`, invalid/private errors remain 422, async jobs retry typed availability failures, and user-facing messages contain no URL or signed query.

- [ ] **Step 2: Write failing metric tests**

Use Micrometer's simple registry and assert exactly one counter increment for primary success, challenge detection, fallback attempted, fallback success, and fallback failure. Assert only low-cardinality reason tags.

- [ ] **Step 3: Run focused tests and confirm red**

```bash
./mvnw -q test \
  -DargLine="-javaagent:/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar" \
  -Dtest=GlobalExceptionHandlerTest,TranscriptionJobServiceTest,TikTokIngestionServiceTest
```

Expected: failures because typed retryable mapping and TikTok metrics do not exist.

- [ ] **Step 4: Implement typed retryability and counters**

Map the new exception before the generic `VideoProcessingException` handler. Check its type before legacy message matching in `TranscriptionJobService`. Add counters at state transitions, not in catch-all controller code.

- [ ] **Step 5: Add sanitized structured logs**

Log event name, challenge/failure enum, elapsed time, and safe IDs already used by the current pipeline. Never log raw process output, full embed HTML, candidate URI, signed query, or HTTP exception text containing a URI.

- [ ] **Step 6: Run focused tests and confirm green**

Run the command from Step 3. Expected: HTTP, retry, metrics, and sanitization tests pass.

- [ ] **Step 7: Commit the error/observability slice**

```bash
git add src/main/java/com/app/categorise/exception \
  src/main/java/com/app/categorise/domain/service \
  src/test/java/com/app/categorise/exception \
  src/test/java/com/app/categorise/domain/service
git commit -m "feat: expose retryable TikTok availability failures"
```

### Task 10: Image capability verification and cookie removal

**Files:**

- Create: `src/main/java/com/app/categorise/config/YtDlpCapabilityVerifier.java`
- Create: `src/test/java/com/app/categorise/config/YtDlpCapabilityVerifierTest.java`
- Modify: `src/main/java/com/app/categorise/domain/service/VideoService.java`
- Modify: `Dockerfile`
- Modify: `docker-compose.prod.yml`
- Modify: `.env.example`
- Modify: `.github/workflows/scheduled-rebuild.yml`

- [ ] **Step 1: Write failing capability-verifier tests**

Use the process executor test double to cover yt-dlp version, `curl_cffi` version, supported target, unavailable target, uppercase/misspelled target, process timeout, and sanitized diagnostic output.

- [ ] **Step 2: Run verifier tests and confirm red**

```bash
./mvnw -q test \
  -DargLine="-javaagent:/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar" \
  -Dtest=YtDlpCapabilityVerifierTest
```

Expected: compilation failure because the verifier does not exist.

- [ ] **Step 3: Implement startup capability verification**

Run bounded local commands for `yt-dlp --version`, `python3 -c 'import curl_cffi; ...'`, and target validation. Emit only sanitized versions/target status. Fail startup/readiness for missing `curl_cffi` or unsupported configured target.

- [ ] **Step 4: Remove all cookie plumbing**

Remove `app.ytdlp.cookies-file`, constructor fields, `--cookies` arguments, `APP_YTDLP_COOKIES_FILE`, `APP_YTDLP_COOKIES_HOST_PATH`, and the cookie volume mount. Confirm `rg -n "cookies-file|YTDLP_COOKIES|--cookies"` returns no application/deployment matches.

- [ ] **Step 5: Strengthen the image workflow**

Before push and after deploy, run:

```bash
yt-dlp --version
python3 -c 'import curl_cffi; print(curl_cffi.__version__)'
yt-dlp --list-impersonate-targets
yt-dlp --ignore-config --impersonate "$APP_TIKTOK_YTDLP_IMPERSONATE_TARGET" --list-extractors
```

Abort deployment if any command fails. Record the previously running immutable image digest before deployment so rollback can restore it.

- [ ] **Step 6: Run verifier tests and inspect workflow syntax**

Run the command from Step 2 and validate the workflow with the repository's existing GitHub Actions lint mechanism if present. Expected: verifier tests pass and YAML parses successfully.

- [ ] **Step 7: Commit deployment hardening**

```bash
git add src/main/java/com/app/categorise/config/YtDlpCapabilityVerifier.java \
  src/test/java/com/app/categorise/config/YtDlpCapabilityVerifierTest.java \
  src/main/java/com/app/categorise/domain/service/VideoService.java \
  Dockerfile docker-compose.prod.yml .env.example .github/workflows/scheduled-rebuild.yml
git commit -m "build: verify TikTok impersonation capability"
```

### Task 11: Automated verification and live canary

**Files:**

- Modify: `.github/workflows/scheduled-rebuild.yml`
- Create: `scripts/check-tiktok-canary.sh` only if the repository accepts deployment scripts; otherwise keep the logic inline in the workflow.

- [ ] **Step 1: Add deterministic verification stage**

Run focused parser/client/orchestration tests and then the full backend suite before building the deployable image.

- [ ] **Step 2: Add the image-level blocking check**

```bash
docker run --rm --entrypoint sh \
  -e APP_TIKTOK_YTDLP_IMPERSONATE_TARGET=chrome \
  "$IMAGE" -ceu '
    yt-dlp --version
    python3 -c "import curl_cffi; print(curl_cffi.__version__)"
    yt-dlp --list-impersonate-targets
    yt-dlp --ignore-config \
      --impersonate "$APP_TIKTOK_YTDLP_IMPERSONATE_TARGET" \
      --list-extractors >/dev/null
  '
```

Expected: zero exit status and a printed yt-dlp/curl_cffi version.

- [ ] **Step 3: Add a controlled live canary**

Use a long-lived, embedding-enabled video owned by the team. Run from the production egress network with two or three bounded attempts. Compare candidate and currently deployed images back-to-back against the same video.

Record one of:

```text
primary_success
challenge_then_fallback_available
external_failure_for_both_images
candidate_regression
```

Fetch only metadata and a small CDN range for fallback availability; do not invoke Whisper.

- [ ] **Step 4: Encode honest canary gating**

- Candidate fails while baseline succeeds consistently: block candidate deploy.
- Both fail: alert as external availability and retain the current image; do not label the candidate defective.
- Candidate succeeds: continue rollout.
- Canary video missing/private/embed-disabled: alert that the canary is invalid and require operator review.

- [ ] **Step 5: Verify deployed image**

After Compose recreates the app, repeat version, `curl_cffi`, target, readiness, and canary checks against the running container.

- [ ] **Step 6: Commit canary verification**

```bash
git add .github/workflows/scheduled-rebuild.yml scripts/check-tiktok-canary.sh
git commit -m "ci: verify TikTok ingestion before deployment"
```

Omit `scripts/check-tiktok-canary.sh` from the command when the implementation remains inline.

### Task 12: Full regression and operational documentation

**Files:**

- Modify: `README.md` or the repository's existing deployment runbook.
- Modify tests only when verification reveals an actual gap.

- [ ] **Step 1: Run the focused TikTok suite**

```bash
./mvnw -q test \
  -DargLine="-javaagent:/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar" \
  -Dtest=DefaultProcessExecutorTest,TikTokVideoIdentityResolverTest,TikTokEmbedPageParserTest,TikTokEmbedFallbackClientTest,TikTokChallengeDetectorTest,TikTokIngestionPropertiesTest,TikTokIngestionServiceTest,VideoServiceTest,TranscriptionJobServiceTest
```

Expected: zero failures and errors.

- [ ] **Step 2: Run the full backend suite**

```bash
./mvnw -q test \
  -DargLine="-javaagent:/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar"
```

Expected: all Surefire summaries show zero failures and errors. Use Docker access for Testcontainers-backed tests.

- [ ] **Step 3: Build and verify the production image**

```bash
docker build --platform linux/amd64 -t content-app:tiktok-resilience .
docker run --rm --entrypoint sh \
  -e APP_TIKTOK_YTDLP_IMPERSONATE_TARGET=chrome \
  content-app:tiktok-resilience -ceu '
    yt-dlp --version
    python3 -c "import curl_cffi; print(curl_cffi.__version__)"
    yt-dlp --ignore-config --impersonate chrome --list-extractors >/dev/null
  '
```

Expected: image build and capability checks succeed.

- [ ] **Step 4: Document operations**

Document:

- How to list targets from the deployed image.
- How to trial a lowercase target.
- How to change or clear the API hostname.
- How to disable the embed fallback.
- How to interpret the five metrics.
- How to identify a broken canary.
- How to roll back by immutable image digest/tag.

- [ ] **Step 5: Confirm signed-URL absence**

Run repository searches and tests proving no entity, DTO, log template, fixture, or error response contains a real signed CDN URI or signature.

- [ ] **Step 6: Commit the final documentation**

```bash
git add README.md
git commit -m "docs: add TikTok ingestion operations runbook"
```

## Canary limitations

No live TikTok test is non-flaky. The controlled canary may be deleted, region-restricted, marked private, have embedding disabled, or fail because of TikTok IP reputation. Required CI correctness therefore comes from saved sanitized fixtures, mocked HTTP/process behavior, and image capability checks. The live canary supplies deployment evidence and regression comparison; it does not replace deterministic tests.

## Risks and unknowns

- `__FRONTITY_CONNECT_STATE__` and its JSON path are undocumented and may change.
- CDN host diversity was observed from one response region; strict allow-listing may initially create safe false negatives.
- Natural signed-URL expiry status was not directly observed.
- Photo posts and other non-video TikTok content may have different embed structures and remain unsupported.
- Exact challenge matching deliberately favors safe false negatives; a new upstream message will initially skip fallback.
- The fallback downloads full video before ffmpeg, increasing bandwidth compared with a direct audio rendition.
- Daily unpinned yt-dlp/curl_cffi updates can introduce regressions.
- Official embed and media retrieval should be reviewed against TikTok's current terms before production launch.
- Metrics export beyond the in-process actuator registry may require separate operational infrastructure; this plan adds instrumentation, not a monitoring backend.

## Rollback

1. Set `APP_TIKTOK_EMBED_FALLBACK_ENABLED=false` and redeploy to disable fallback immediately.
2. Set the impersonation target back to `chrome` or the last verified lowercase target.
3. Clear `APP_TIKTOK_YTDLP_API_HOSTNAME` to restore yt-dlp's upstream default.
4. Redeploy the previously recorded immutable image digest or `ytdlp-<version>` tag.
5. Automatically restore the previous digest if post-deployment capability/readiness checks fail.
6. No database rollback is needed because the change introduces no schema and never persists signed CDN URLs.

## Completion criteria

Implementation is complete only when all of the following are true:

- Primary yt-dlp remains the first TikTok route.
- The invalid uppercase target is gone and the configured target is supported by the built/deployed image.
- Fallback runs exactly once only for the four recognized challenge categories.
- Generic yt-dlp, network, validation, ffmpeg, private/login, and audio failures never invoke fallback.
- Valid embed metadata parses from a sanitized fixture matching the observed current shape.
- SSRF tests cover input, embed, CDN, and redirects.
- Signed CDN URLs never appear in persistence, canonical identity, responses, logs, metrics, or exception messages.
- Canonical deduplication uses submitted TikTok URL plus `(TIKTOK, numeric video ID)`.
- Retryable availability failures surface as HTTP 503 and retry in the job pipeline.
- Required structured events and metrics distinguish every primary/fallback state.
- Unit, integration, full Maven, image capability, and deployed-image checks pass.
- The canary strategy and its limitations are documented.
- The fallback kill switch and immutable-image rollback procedure are tested operationally.
