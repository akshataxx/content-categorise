# Multi-Platform Video Support Design

**Date:** 2025-04-03
**Status:** Draft

## Overview

Extend the app to support YouTube Shorts and Instagram Reels/videos alongside TikTok, with a clean platform-agnostic architecture. Add a `platform` field so the UI can display platform-specific branding (icons, labels).

## Current State

- **yt-dlp already works generically** — YouTube, Instagram, and TikTok all return the same core metadata fields (`title`, `description`, `duration`, `timestamp`, `uploader`, `uploader_id`, `channel`, `channel_id`). No platform-specific yt-dlp configuration is needed for basic metadata extraction.
- **`VideoUrlValidator`** already supports 13+ platforms including YouTube, Instagram, TikTok, Vimeo, X/Twitter, Reddit, etc.
- **`AddLinkRequest`** and `BaseTranscriptEntity` are already platform-agnostic.
- **iOS app** is almost entirely platform-agnostic. `ActivityItem.displayTitle` already maps hostnames → friendly labels for multiple platforms.

### What's TikTok-Coupled Today

| Location | Coupling | Fix |
|---|---|---|
| `TikTokMetadata.java` class name | Misleading name for a generic yt-dlp DTO | Rename to `VideoMetadata` |
| `VideoService.extractAudioAndMetadata()` | TikTok `--extractor-args` applied to ALL URLs | Make conditional on TikTok URLs only |
| `VideoService` javadocs | Say "TikTok" everywhere | Update to be generic |
| `OpenAIClientImpl.buildDeveloperPrompt()` | Says "e.g., TikToks" | Update to include other platforms |
| `Dockerfile` comment | References TikTok | Update |

## Design

### 1. `VideoPlatform` Enum

New enum in `domain.model`:

```java
public enum VideoPlatform {
    YOUTUBE,
    TIKTOK,
    INSTAGRAM,
    VIMEO,
    TWITTER,
    FACEBOOK,
    REDDIT,
    TWITCH,
    DAILYMOTION,
    UNKNOWN;
}
```

Includes a `fromUrl(String url)` static method that parses the domain and returns the matching platform. Also includes a `fromExtractor(String extractor)` static method that maps yt-dlp extractor names (e.g., `"youtube"`, `"TikTok"`, `"Instagram"`) to enum values.

### 2. Platform Detection Strategy

**Single `platform` column** — no separate `extractor` column.

- **At job creation:** Derive platform from the submitted URL using `VideoPlatform.fromUrl()`. Store on `TranscriptionJobEntity` so the iOS app can show the platform immediately while the job is pending.
- **After yt-dlp extraction:** Read the `extractor` field from yt-dlp's metadata JSON. Map it to `VideoPlatform` using `fromExtractor()`. This becomes the final value stored on `BaseTranscriptEntity.platform`. yt-dlp is the ground truth — it resolves redirects and knows the actual platform.
- **If yt-dlp extraction fails** or `extractor` is missing, fall back to the URL-derived value.

### 3. Rename `TikTokMetadata` → `VideoMetadata`

- Rename class to `VideoMetadata` in `data.dto` package
- Add field: `extractor` (String, `@JsonProperty("extractor")`) — used for platform detection, not persisted separately
- All existing fields remain unchanged — they are generic yt-dlp fields
- Update all references: `VideoService`, `VideoMapper`, tests

### 4. Conditional TikTok Extractor Args

In `VideoService.extractAudioAndMetadata()`:

```java
if (VideoPlatform.fromUrl(videoUrl) == VideoPlatform.TIKTOK) {
    command.add("--extractor-args");
    command.add("tiktok:api_hostname=api22-normal-c-useast1a.tiktokv.com");
}
// --user-agent stays for all platforms (good practice)
```

### 5. Database Migration

New migration `V21__add_platform_to_base_transcripts.sql`:

```sql
ALTER TABLE base_transcripts ADD COLUMN platform VARCHAR(20);
ALTER TABLE transcription_jobs ADD COLUMN platform VARCHAR(20);

-- Backfill existing rows as TIKTOK
UPDATE base_transcripts SET platform = 'TIKTOK' WHERE platform IS NULL;
UPDATE transcription_jobs SET platform = 'TIKTOK' WHERE platform IS NULL;
```

### 6. Entity & Domain Model Changes

**`BaseTranscriptEntity`** — add:
```java
@Column(name = "platform", length = 20)
private String platform;
```

**`TranscriptionJobEntity`** — add:
```java
@Column(name = "platform", length = 20)
private String platform;
```

**`BaseTranscript`** (domain model) — add `VideoPlatform platform` field.

**`VideoMapper`** — update `createBaseTranscriptEntity()` to:
- Accept `VideoPlatform` parameter
- Map `platform` field to entity

### 7. API DTO Changes

**`TranscriptDtoWithAliases`** — add `String platform` field.

**`JobStatusDto`** — add `String platform` field.

The `platform` value is the enum name as a string (e.g., `"YOUTUBE"`, `"TIKTOK"`, `"INSTAGRAM"`).

### 8. iOS App Changes

Minimal changes — all additive:

| File | Change |
|---|---|
| `TranscriptResponse.swift` | Add `let platform: String?` |
| `Transcript.swift` | Add `let platform: String?` |
| `TranscriptEntity.swift` (SwiftData) | Add `var platform: String?` |
| `TranscriptionJob` model | Add `let platform: String?` |
| Mappers | Propagate new field |
| `ActivityItem.swift` | Can use `platform` instead of URL-parsing for `displayTitle` if available |

### 9. OpenAI Prompt Update

Change:
```
"classifies short-form social media videos (e.g., TikToks)"
```
To:
```
"classifies short-form social media videos (e.g., TikToks, YouTube Shorts, Instagram Reels)"
```

### 10. Comment & Javadoc Cleanup

Update all TikTok-specific comments to be platform-generic:
- `VideoService` method javadocs
- `Dockerfile` comment
- Any other references

## Verified Metadata Field Compatibility

Confirmed via yt-dlp source code and live testing that all 8 metadata fields work across platforms:

| Field | YouTube | TikTok | Instagram |
|---|---|---|---|
| `title` (fulltitle) | ✅ Video title | ✅ Truncated desc | ✅ "Video by {user}" |
| `description` | ✅ Full description | ✅ Full desc | ✅ Caption text |
| `duration` | ✅ int (seconds) | ✅ int (seconds) | ✅ float (seconds) |
| `timestamp` | ✅ epoch | ✅ epoch | ✅ epoch |
| `uploader` → account | ✅ Channel name | ✅ unique_id | ✅ full_name |
| `uploader_id` → accountId | ✅ @handle | ✅ uid | ✅ pk |
| `channel` → identifier | ✅ Channel name | ✅ nickname | ✅ username |
| `channel_id` → identifierId | ✅ UC... ID | ✅ sec_uid | ❌ null (fine) |
| `extractor` | ✅ "youtube" | ✅ "TikTok" | ✅ "Instagram" |

## What's NOT Changing

- **`VideoUrlValidator`** — already supports all platforms
- **`AddLinkRequest`** — already platform-agnostic
- **`base_transcripts` existing columns** — no renames or type changes
- **No per-platform metadata tables** — one table with `platform` discriminator
- **No platform-specific yt-dlp config for YouTube/Instagram** — they work out of the box

## Future Considerations (Not In Scope)

- Instagram may need cookie/auth support for private posts
- Platform-specific metadata (view_count, thumbnails) — add a JSONB column if ever needed
- Platform icons in the iOS UI — the `platform` field enables this but icon assets are out of scope
