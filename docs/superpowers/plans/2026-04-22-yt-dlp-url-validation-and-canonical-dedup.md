# yt-dlp-Based URL Validation & Canonical Video Dedup

**Date:** 2026-04-22
**Status:** Planning
**Author:** Discussion with Rovo Dev

## Background / Motivation

Today, video URL validation is handled by a hand-maintained allowlist in
`VideoUrlValidator`:

- ✅ HTTPS only, length & format checks
- ✅ Hardcoded list of allowed hostnames per platform (YouTube, TikTok, Instagram, Vimeo, etc.)
- ✅ DNS resolution + SSRF IP block (rejects loopback, private, link-local, cloud metadata)

**Problems:**
1. The allowlist must be maintained by hand. Real platforms have many hostnames
   (e.g. `tiktokv.com`, `youtube-nocookie.com`, regional YouTube ccTLDs) that we
   miss or block legitimately.
2. We just had a bug where DNS resolution failures rejected legitimate
   allowlisted URLs — leading to a workaround (the recent change to allow on DNS
   failure for allowlisted domains).
3. Different URLs for the **same video** (e.g. `youtu.be/X` vs `youtube.com/watch?v=X`
   vs `youtube.com/shorts/X`) are treated as distinct content. Each user-share
   triggers a fresh download + Whisper + OpenAI run, even when the same video
   was processed yesterday via a different URL form.

**Insight:** yt-dlp itself already knows what URLs it supports and produces a
canonical video ID per platform. We can use yt-dlp as both:
- The **validator** — if yt-dlp can fetch metadata, the URL is valid; if not, reject.
- The **canonical-id source** — dedup base transcripts by `(platform, video_id)`
  rather than raw URL.

## Goals

- Drop the hand-maintained URL allowlist and SSRF code from `VideoUrlValidator`.
- Use `yt-dlp --dump-json --no-download` as the URL validator.
- Dedup base transcripts by canonical `(platform, video_id)`, saving the cost of
  re-downloading + re-transcribing + re-categorising the same video shared via
  different URL forms.

## Non-Goals

- Replacing yt-dlp itself.
- Changing the rate-limiting model.
- Changing the user-transcript association model (per-user dedup is unchanged).
- Reverse-resolving the canonical URL: we keep the first-seen URL as
  `base_transcripts.video_url` and dedup happens via the new key.

## Decisions

- **Generic user-facing error, detailed server-side log.** When `fetchMetadata`
  fails (yt-dlp error, timeout, network failure, etc.), we throw
  `VideoProcessingException("Could not process video URL — please check the link and try again.")`.
  The full yt-dlp output / stack trace is logged at ERROR level server-side.
  Reason: don't leak internal details / yt-dlp version info to clients.

- **Async endpoint also fails on validation.** The `/transcribe-async` endpoint
  surfaces validation failure via the job's `FAILED` status + error message.
  We accept the small extra latency in the worker thread (not the request thread)
  rather than letting invalid URLs into the queue. Job submission still returns
  `202` immediately.

- **No URL-form sanity pre-filter.** We will not keep a stripped-down version
  of `VideoUrlValidator` as a "cheap pre-check". yt-dlp rejects obvious garbage
  fast enough (sub-second), and keeping any allowlist defeats the purpose.
  We do retain the existing controller-level null/blank check for `videoUrl`
  in the request body.

- **Two-tier dedup.**
  - **Tier 1 (existing)**: exact URL match in `base_transcripts.video_url`.
  - **Tier 2 (new)**: canonical match via `(platform, platform_video_id)`.

- **Two-column dedup key**: `platform` (already exists as enum) + new
  `platform_video_id` column. Cleaner for indexing/querying than a packed
  `canonical_key` string. Unique constraint on `(platform, platform_video_id)`
  where both are non-null.

- **Backfill strategy**: leave existing rows with `platform_video_id = NULL`.
  Tier 1 (URL match) continues to dedup legacy rows. Newly created or freshly
  re-fetched rows get the canonical id populated.

- **Two PRs**, executed sequentially:
  - **PR 1**: Replace `VideoUrlValidator` with `fetchMetadata`-based validation.
  - **PR 2**: Add canonical-id-based dedup, building on PR 1.

## Open Questions

- Should we also persist the canonical `webpage_url` from yt-dlp into a new
  column for debugging / future migrations? (Not required for dedup, low cost.)
  → **Defer to PR 2 review.**
- For YouTube specifically, does yt-dlp's `id` match across `youtube.com/shorts`,
  `youtube.com/watch`, `youtu.be`, `youtube-nocookie.com`? We're banking on yes;
  worth verifying empirically before PR 2.
- TikTok: short links (`vm.tiktok.com/XYZ`) vs full URLs — does yt-dlp resolve
  to the same `id`? Likely yes (the redirect target shares the id).

---

# PR 1 — Replace `VideoUrlValidator` with yt-dlp Metadata Fetch

## Scope

Remove allowlist/SSRF validation. Use yt-dlp `--dump-json --no-download` as the
URL validator and metadata source. Refactor the video processing pipeline to
fetch metadata first (cheap, no audio) and only download audio if needed.

## Files Changed

### Added / Modified

- `util/processExecutor/ProcessExecutor.java` — add `runAndCapture(int timeoutMinutes, String... command)` returning captured stdout.
- `util/processExecutor/DefaultProcessExecutor.java` — implement `runAndCapture`.
- `util/processExecutor/ProcessRunner.java` — add a parallel `runCommandCaptureOutput` helper.
- `domain/service/VideoService.java`
  - Add `fetchMetadata(String videoUrl): VideoMetadata`
  - Add `downloadAudio(String videoUrl): ProcessedVideoFiles` (no `--write-info-json`, audio only)
  - Refactor `_processVideoAndCreateTranscript`:
    1. Tier 1 dedup (`findByVideoUrl`) — unchanged.
    2. `fetchMetadata(url)` — replaces URL allowlist validation.
    3. `validateTranscriptData(... metadata only fields)` — split into `validateMetadata` (called now) and `validateTranscriptText` (called after Whisper).
    4. `downloadAudio(url)` → `transcribeAudio(audio)`.
    5. Save base transcript + user transcript as before.
  - Remove `extractAudioAndMetadata` and `extractMetadata` (no longer used).
- `application/internal/ProcessedVideoFiles.java` — drop the `metadataFile` field; keep `audioFile` + `tempDir`.
- `api/controller/VideoController.java` — remove `VideoUrlValidator.validate(videoUrl)` call. Keep null/blank check.
- `application.properties` — add `yt-dlp.metadata-timeout-seconds=30` (or similar).
- `config/AppConfig.java` (or wherever the timeout is bound) — bind the new property.

### Deleted

- `util/VideoUrlValidator.java`
- `test/util/VideoUrlValidatorTest.java`

### Tests

- `test/util/processExecutor/TestProcessExecutor.java` — extend to support stub stdout for `runAndCapture` (settable per test, default empty string).
- `test/domain/service/VideoServiceTest.java`
  - Remove `extractAudioAndMetadata` tests.
  - Add `fetchMetadata` tests:
    - Success: stub stdout returns valid JSON → returns `VideoMetadata`.
    - yt-dlp non-zero exit → throws `VideoProcessingException` with generic message.
    - Invalid JSON in stdout → throws `VideoProcessingException` with generic message.
    - Timeout / IOException → throws `VideoProcessingException` with generic message.
  - Add `downloadAudio` tests (mirrors what was tested for `extractAudioAndMetadata`, minus `--write-info-json`).
  - Update `_processVideoAndCreateTranscript` integration tests to mock `fetchMetadata` + `downloadAudio` separately.
- `test/api/controller/VideoControllerTest.java` — remove URL allowlist / SSRF tests. Keep null / blank / missing principal tests.

## Implementation Details

### `fetchMetadata(String videoUrl)`

```java
public VideoMetadata fetchMetadata(String videoUrl) {
    List<String> command = new ArrayList<>();
    command.add("yt-dlp");
    command.add("--dump-json");
    command.add("--no-download");
    command.add("--no-warnings");
    command.add("--user-agent");
    command.add(YT_DLP_USER_AGENT);
    if (VideoPlatform.fromUrl(videoUrl) == VideoPlatform.TIKTOK) {
        command.add("--extractor-args");
        command.add("tiktok:api_hostname=api22-normal-c-useast1a.tiktokv.com");
    }
    command.add("--");
    command.add(videoUrl);

    String stdout;
    try {
        stdout = processExecutor.runAndCapture(metadataTimeoutMinutes, command.toArray(new String[0]));
    } catch (Exception e) {
        log.error("yt-dlp metadata fetch failed for url={}", LogSanitizer.sanitize(videoUrl), e);
        throw new VideoProcessingException(USER_FACING_FETCH_ERROR);
    }

    try {
        return objectMapper.readValue(stdout, VideoMetadata.class);
    } catch (Exception e) {
        log.error("Failed to parse yt-dlp metadata JSON for url={}: stdout={}",
            LogSanitizer.sanitize(videoUrl), stdout, e);
        throw new VideoProcessingException(USER_FACING_FETCH_ERROR);
    }
}

private static final String USER_FACING_FETCH_ERROR =
    "Could not process video URL — please check the link and try again.";
```

### `downloadAudio(String videoUrl)`

Same as today's `extractAudioAndMetadata` but **without** `--write-info-json`,
and returns `ProcessedVideoFiles(audioFile, tempDir)`.

### Argument-injection safety

Keep the `--` separator before `videoUrl` in both `fetchMetadata` and
`downloadAudio` commands. Existing test `extractAudioAndMetadata_includesArgSeparatorBeforeUrl`
should be ported to the new methods.

### Timeout

- `fetchMetadata`: 1 minute max (network-only, no transcoding).
- `downloadAudio`: existing `ytDlpTimeoutMinutes` (default 5+ minutes).

## Verification

1. `./mvnw test` — all tests green.
2. Manual: submit a valid YouTube URL → success.
3. Manual: submit `https://example.com/video` → response is generic error message,
   server log contains the yt-dlp output.
4. Manual: submit `https://www.tiktok.com/@user/video/123` → success (TikTok args still applied).
5. Verify the response body for failed `/transcribe` does **not** include yt-dlp version, internal paths, or stack info.

## Risks

- **yt-dlp not installed in dev/CI**: existing tests already work via
  `TestProcessExecutor` no-op. Capture variant returns stub. No real binary needed.
- **Slightly slower failure for invalid URLs** in async path (job queued then
  failed, vs. rejected at submission). Trade-off accepted per discussion.
- **No SSRF allowlist**: yt-dlp itself acts as the gatekeeper of which sites
  it speaks to — much narrower than arbitrary HTTP. Accepted risk.

---

# PR 2 — Canonical-ID-Based Dedup

## Scope

After `fetchMetadata` returns, dedup base transcripts by `(platform, platform_video_id)`
in addition to the existing URL-based dedup. Saves the cost of audio download +
Whisper + OpenAI categorisation when the same video is shared via different URL
forms.

## Files Changed

### Schema / Migration

- `src/main/resources/db/migration/V23__add_platform_video_id.sql`
  ```sql
  ALTER TABLE base_transcripts
      ADD COLUMN platform_video_id VARCHAR(255);

  -- Partial unique index: only enforce uniqueness on rows that have both fields.
  CREATE UNIQUE INDEX uq_base_transcripts_platform_video_id
      ON base_transcripts (platform, platform_video_id)
      WHERE platform IS NOT NULL AND platform_video_id IS NOT NULL;
  ```
  No backfill — existing rows stay `NULL` and continue to dedup via URL.

### Entity / Domain / Mapper

- `data/entity/BaseTranscriptEntity.java` — add `platformVideoId` column.
- `domain/model/BaseTranscript.java` — add `platformVideoId` field.
- `application/mapper/VideoMapper.java` — set `platformVideoId` from `metadata.getId()`
  when creating a new entity.

### Repository

- `data/repository/BaseTranscriptRepository.java` — add
  `Optional<BaseTranscriptEntity> findByPlatformAndPlatformVideoId(VideoPlatform platform, String platformVideoId)`.

### Service

- `domain/service/VideoService._processVideoAndCreateTranscript`:
  ```
  1. Tier 1: findByVideoUrl(url) → if found, reuse.
  2. fetchMetadata(url) → VideoMetadata
  3. Determine platform = fromExtractor(metadata.extractor) || fromUrl(url)
  4. Tier 2: if metadata.id != null:
       findByPlatformAndPlatformVideoId(platform, metadata.id) → if found, reuse.
  5. validateMetadata(metadata)
  6. downloadAudio(url) → transcribeAudio(audio)
  7. validateTranscriptText(text)
  8. createBaseTranscriptEntity(... platformVideoId = metadata.id)
  9. Save + user association as before.
  ```

### Tests

- `test/data/repository/BaseTranscriptRepositoryTest.java` — add
  `findByPlatformAndPlatformVideoId` test (positive, negative, NULL handling).
- `test/domain/service/VideoServiceTest.java` — add `_processVideoAndCreateTranscript`
  tests for:
  - URL miss + canonical hit → reuse, no audio download, no Whisper call.
  - URL miss + canonical miss → full pipeline.
  - URL hit (Tier 1) → no metadata fetch (existing test, just verify not regressed).

## Verification

1. `./mvnw test` — all green.
2. Manual: process `https://youtu.be/abc` → submit `https://youtube.com/watch?v=abc`
   → second submission reuses the same base transcript (logged "Reusing existing
   transcript" via Tier 2 path).
3. Manual: process `https://vm.tiktok.com/XYZ/` → resolve and verify the
   `platform_video_id` matches what the full TikTok URL produces.
4. DB inspection: confirm the unique index allows multiple `NULL` rows but
   blocks duplicates with the same `(platform, platform_video_id)`.

## Risks / Open Questions

- **YouTube short links / shorts**: confirm `yt-dlp --dump-json` returns the
  same `id` for `youtu.be/X`, `youtube.com/watch?v=X`, `youtube.com/shorts/X`,
  and `youtube-nocookie.com/embed/X`. If not, dedup leaks for some forms.
- **Instagram**: `/reel/X` vs `/p/X` for the same media — does yt-dlp normalise?
- **TikTok short links**: yt-dlp follows the redirect, so the resulting `id`
  should match the canonical URL's id. Verify.
- **Race condition**: two simultaneous first-time submissions of the same video
  via different URLs could both fetch metadata + start downloads before either
  saves. The unique index will reject the second insert; we should catch
  `DataIntegrityViolationException` and re-query by `(platform, id)`. Add this
  to the implementation.
- **Migrating existing data**: not done in this PR. A future cleanup migration
  could backfill `platform_video_id` for existing rows by parsing URLs (cheap,
  no yt-dlp call needed for most platforms — the id is in the URL).

---

# Rollout Order

1. Land PR 1 first. Verify in production that yt-dlp-based validation works
   smoothly before adding the schema change.
2. Land PR 2 after PR 1 has soaked for at least a few days. The migration is
   additive (nullable column + partial index), zero-downtime safe.
