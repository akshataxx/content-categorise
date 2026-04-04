# Multi-Platform Video Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Decouple the app from TikTok-specific naming/logic and add a `platform` field so the UI can display platform branding.

**Architecture:** Rename `TikTokMetadata` → `VideoMetadata`, add `VideoPlatform` enum with URL/extractor detection, add `platform` column to DB, propagate through API DTOs to iOS models. Make TikTok yt-dlp extractor args conditional.

**Tech Stack:** Java 24 / Spring Boot 3.5, PostgreSQL + Flyway, SwiftUI + SwiftData (iOS)

---

## File Structure

### New Files
- `src/main/java/com/app/categorise/domain/model/VideoPlatform.java` — Platform enum with detection methods
- `src/main/java/com/app/categorise/data/dto/VideoMetadata.java` — Renamed from TikTokMetadata
- `src/main/resources/db/migration/V21__add_platform_to_base_transcripts.sql` — Migration
- `src/test/java/com/app/categorise/domain/model/VideoPlatformTest.java` — Enum tests

### Deleted Files
- `src/main/java/com/app/categorise/data/dto/TikTokMetadata.java` — Replaced by VideoMetadata

### Modified Files (Backend)
- `BaseTranscriptEntity.java` — Add `platform` field
- `TranscriptionJobEntity.java` — Add `platform` field
- `BaseTranscript.java` — Add `platform` field
- `VideoMapper.java` — Accept `VideoPlatform`, map to entity
- `VideoService.java` — Use `VideoMetadata`, conditional TikTok args
- `TranscriptionJobService.java` — Set platform on job creation
- `JobMapper.java` — Map platform to DTO
- `JobStatusDto.java` — Add `platform` field
- `TranscriptDtoWithAliases.java` — Add `platform` field
- `OpenAIClientImpl.java` — Update prompt text
- `Dockerfile` — Update comment
- `VideoServiceTest.java` — Update to use `VideoMetadata`
- `TranscriptionJobServiceTest.java` — Verify platform is set

### Modified Files (iOS)
- `TranscriptResponse.swift` — Add `platform` field
- `Transcript.swift` — Add `platform` field
- `TranscriptEntity.swift` — Add `platform` field
- `TranscriptionJob.swift` — Add `platform` field
- `ActivityItem.swift` — Add `platform` field, use for display
- `TranscriptMapper.swift` — Propagate `platform`

---

## Task 1: Create `VideoPlatform` Enum with Detection Logic

**Files:**
- Create: `src/main/java/com/app/categorise/domain/model/VideoPlatform.java`
- Create: `src/test/java/com/app/categorise/domain/model/VideoPlatformTest.java`

- [ ] **Step 1: Write the test file**

```java
package com.app.categorise.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VideoPlatformTest {

    @Nested
    @DisplayName("fromUrl")
    class FromUrl {

        @Test
        @DisplayName("detects YouTube from youtube.com")
        void detectsYoutube() {
            assertThat(VideoPlatform.fromUrl("https://www.youtube.com/watch?v=abc123")).isEqualTo(VideoPlatform.YOUTUBE);
        }

        @Test
        @DisplayName("detects YouTube from youtu.be")
        void detectsYoutubeShortUrl() {
            assertThat(VideoPlatform.fromUrl("https://youtu.be/abc123")).isEqualTo(VideoPlatform.YOUTUBE);
        }

        @Test
        @DisplayName("detects YouTube Shorts")
        void detectsYoutubeShorts() {
            assertThat(VideoPlatform.fromUrl("https://www.youtube.com/shorts/abc123")).isEqualTo(VideoPlatform.YOUTUBE);
        }

        @Test
        @DisplayName("detects TikTok")
        void detectsTikTok() {
            assertThat(VideoPlatform.fromUrl("https://www.tiktok.com/@user/video/123")).isEqualTo(VideoPlatform.TIKTOK);
        }

        @Test
        @DisplayName("detects TikTok vm short link")
        void detectsTikTokVm() {
            assertThat(VideoPlatform.fromUrl("https://vm.tiktok.com/abc123")).isEqualTo(VideoPlatform.TIKTOK);
        }

        @Test
        @DisplayName("detects Instagram")
        void detectsInstagram() {
            assertThat(VideoPlatform.fromUrl("https://www.instagram.com/reel/abc123/")).isEqualTo(VideoPlatform.INSTAGRAM);
        }

        @Test
        @DisplayName("detects Vimeo")
        void detectsVimeo() {
            assertThat(VideoPlatform.fromUrl("https://vimeo.com/123456")).isEqualTo(VideoPlatform.VIMEO);
        }

        @Test
        @DisplayName("detects Twitter/X")
        void detectsTwitter() {
            assertThat(VideoPlatform.fromUrl("https://x.com/user/status/123")).isEqualTo(VideoPlatform.TWITTER);
            assertThat(VideoPlatform.fromUrl("https://twitter.com/user/status/123")).isEqualTo(VideoPlatform.TWITTER);
        }

        @Test
        @DisplayName("detects Facebook")
        void detectsFacebook() {
            assertThat(VideoPlatform.fromUrl("https://www.facebook.com/watch?v=123")).isEqualTo(VideoPlatform.FACEBOOK);
        }

        @Test
        @DisplayName("detects Reddit")
        void detectsReddit() {
            assertThat(VideoPlatform.fromUrl("https://www.reddit.com/r/sub/comments/abc/title/")).isEqualTo(VideoPlatform.REDDIT);
        }

        @Test
        @DisplayName("returns UNKNOWN for unrecognized URLs")
        void returnsUnknownForUnrecognized() {
            assertThat(VideoPlatform.fromUrl("https://example.com/video")).isEqualTo(VideoPlatform.UNKNOWN);
        }

        @Test
        @DisplayName("returns UNKNOWN for null URL")
        void returnsUnknownForNull() {
            assertThat(VideoPlatform.fromUrl(null)).isEqualTo(VideoPlatform.UNKNOWN);
        }

        @Test
        @DisplayName("returns UNKNOWN for malformed URL")
        void returnsUnknownForMalformed() {
            assertThat(VideoPlatform.fromUrl("not-a-url")).isEqualTo(VideoPlatform.UNKNOWN);
        }
    }

    @Nested
    @DisplayName("fromExtractor")
    class FromExtractor {

        @Test
        @DisplayName("maps yt-dlp extractor names to platform")
        void mapsExtractorNames() {
            assertThat(VideoPlatform.fromExtractor("youtube")).isEqualTo(VideoPlatform.YOUTUBE);
            assertThat(VideoPlatform.fromExtractor("TikTok")).isEqualTo(VideoPlatform.TIKTOK);
            assertThat(VideoPlatform.fromExtractor("Instagram")).isEqualTo(VideoPlatform.INSTAGRAM);
            assertThat(VideoPlatform.fromExtractor("vimeo")).isEqualTo(VideoPlatform.VIMEO);
        }

        @Test
        @DisplayName("is case-insensitive")
        void isCaseInsensitive() {
            assertThat(VideoPlatform.fromExtractor("YOUTUBE")).isEqualTo(VideoPlatform.YOUTUBE);
            assertThat(VideoPlatform.fromExtractor("tiktok")).isEqualTo(VideoPlatform.TIKTOK);
        }

        @Test
        @DisplayName("returns UNKNOWN for null or unrecognized extractor")
        void returnsUnknownForNull() {
            assertThat(VideoPlatform.fromExtractor(null)).isEqualTo(VideoPlatform.UNKNOWN);
            assertThat(VideoPlatform.fromExtractor("SomeNewPlatform")).isEqualTo(VideoPlatform.UNKNOWN);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl . -Dtest=VideoPlatformTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `VideoPlatform` class does not exist

- [ ] **Step 3: Create the `VideoPlatform` enum**

```java
package com.app.categorise.domain.model;

import java.net.URI;

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

    /**
     * Derives the platform from a video URL by matching the hostname.
     * Returns UNKNOWN if the URL is null, malformed, or unrecognized.
     */
    public static VideoPlatform fromUrl(String url) {
        if (url == null || url.isBlank()) return UNKNOWN;
        try {
            String host = URI.create(url).getHost();
            if (host == null) return UNKNOWN;
            host = host.toLowerCase();

            if (host.contains("youtube.com") || host.contains("youtu.be")) return YOUTUBE;
            if (host.contains("tiktok.com")) return TIKTOK;
            if (host.contains("instagram.com")) return INSTAGRAM;
            if (host.contains("vimeo.com")) return VIMEO;
            if (host.contains("twitter.com") || host.contains("x.com")) return TWITTER;
            if (host.contains("facebook.com") || host.contains("fb.watch")) return FACEBOOK;
            if (host.contains("reddit.com")) return REDDIT;
            if (host.contains("twitch.tv")) return TWITCH;
            if (host.contains("dailymotion.com")) return DAILYMOTION;

            return UNKNOWN;
        } catch (Exception e) {
            return UNKNOWN;
        }
    }

    /**
     * Maps a yt-dlp extractor name to a VideoPlatform.
     * yt-dlp extractor names are like "youtube", "TikTok", "Instagram", etc.
     */
    public static VideoPlatform fromExtractor(String extractor) {
        if (extractor == null || extractor.isBlank()) return UNKNOWN;
        String lower = extractor.toLowerCase();

        if (lower.contains("youtube")) return YOUTUBE;
        if (lower.contains("tiktok")) return TIKTOK;
        if (lower.contains("instagram")) return INSTAGRAM;
        if (lower.contains("vimeo")) return VIMEO;
        if (lower.contains("twitter") || lower.contains("x.com")) return TWITTER;
        if (lower.contains("facebook") || lower.contains("fb")) return FACEBOOK;
        if (lower.contains("reddit")) return REDDIT;
        if (lower.contains("twitch")) return TWITCH;
        if (lower.contains("dailymotion")) return DAILYMOTION;

        return UNKNOWN;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw test -pl . -Dtest=VideoPlatformTest`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/app/categorise/domain/model/VideoPlatform.java \
        src/test/java/com/app/categorise/domain/model/VideoPlatformTest.java
git commit -m "feat: add VideoPlatform enum with URL and extractor detection"
```

---

## Task 2: Rename `TikTokMetadata` → `VideoMetadata` and Add `extractor` Field

**Files:**
- Create: `src/main/java/com/app/categorise/data/dto/VideoMetadata.java`
- Delete: `src/main/java/com/app/categorise/data/dto/TikTokMetadata.java`

- [ ] **Step 1: Create `VideoMetadata.java`**

```java
package com.app.categorise.data.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.ToString;

@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class VideoMetadata {
    private String description;
    @JsonProperty("fulltitle")
    private String title;
    private int duration;
    @JsonProperty("timestamp")
    private long uploadedEpoch;
    // yt-dlp calls the account (the alias name) the uploader
    @JsonProperty("uploader_id")
    private String accountId;
    @JsonProperty("uploader")
    private String account;
    @JsonProperty("channel_id")
    private String identifierId;
    @JsonProperty("channel")
    private String identifier;
    // yt-dlp extractor name — used to determine the source platform
    private String extractor;

    public String getDescription() { return description; }
    public String getTitle() { return title; }
    public int getDuration() { return duration; }
    public long getUploadedEpoch() { return uploadedEpoch; }
    public String getAccountId() { return accountId; }
    public String getAccount() { return account; }
    public String getIdentifierId() { return identifierId; }
    public String getIdentifier() { return identifier; }
    public String getExtractor() { return extractor; }

    public void setDescription(String description) { this.description = description; }
    public void setTitle(String title) { this.title = title; }
    public void setDuration(int duration) { this.duration = duration; }
    public void setUploadedEpoch(long uploadedEpoch) { this.uploadedEpoch = uploadedEpoch; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public void setAccount(String account) { this.account = account; }
    public void setIdentifierId(String identifierId) { this.identifierId = identifierId; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }
    public void setExtractor(String extractor) { this.extractor = extractor; }
}
```

- [ ] **Step 2: Delete `TikTokMetadata.java`**

Delete file: `src/main/java/com/app/categorise/data/dto/TikTokMetadata.java`

- [ ] **Step 3: Update all imports and references**

In `VideoService.java`, replace all occurrences:
- `import com.app.categorise.data.dto.TikTokMetadata;` → `import com.app.categorise.data.dto.VideoMetadata;`
- `TikTokMetadata` → `VideoMetadata` (in method signatures, local variables, javadoc)

In `VideoMapper.java`, replace:
- `import com.app.categorise.data.dto.TikTokMetadata;` → `import com.app.categorise.data.dto.VideoMetadata;`
- `TikTokMetadata` → `VideoMetadata` (in method parameter)

In `VideoServiceTest.java`, replace:
- `import com.app.categorise.data.dto.TikTokMetadata;` → `import com.app.categorise.data.dto.VideoMetadata;` (if present)
- All `TikTokMetadata` → `VideoMetadata`

- [ ] **Step 4: Run tests to verify nothing broke**

Run: `./mvnw test -pl . -Dtest=VideoServiceTest`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: rename TikTokMetadata to VideoMetadata, add extractor field"
```

---

## Task 3: Database Migration and Entity Updates

**Files:**
- Create: `src/main/resources/db/migration/V21__add_platform_to_base_transcripts.sql`
- Modify: `src/main/java/com/app/categorise/data/entity/BaseTranscriptEntity.java`
- Modify: `src/main/java/com/app/categorise/data/entity/TranscriptionJobEntity.java`
- Modify: `src/main/java/com/app/categorise/domain/model/BaseTranscript.java`

- [ ] **Step 1: Create Flyway migration**

```sql
-- Add platform tracking to base_transcripts and transcription_jobs
ALTER TABLE base_transcripts ADD COLUMN platform VARCHAR(20);
ALTER TABLE transcription_jobs ADD COLUMN platform VARCHAR(20);

-- Backfill: all existing rows were TikTok (the only supported platform before this change)
UPDATE base_transcripts SET platform = 'TIKTOK' WHERE platform IS NULL;
UPDATE transcription_jobs SET platform = 'TIKTOK' WHERE platform IS NULL;
```

- [ ] **Step 2: Add `platform` to `BaseTranscriptEntity`**

Add field and getter/setter after `private Instant transcribedAt;`:

```java
    private String platform;
```

Add to the constructor parameter list (after `String identifier`):

Update constructor to accept and set `platform`. Since existing code calls the constructor without `platform`, add a second constructor that includes it, or simply add a setter call after construction. The cleanest approach: keep the existing constructor as-is (for backward compatibility) and set `platform` via setter in the mapper.

Add getter/setter:

```java
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
```

- [ ] **Step 3: Add `platform` to `TranscriptionJobEntity`**

Add field after `private Instant updatedAt;`:

```java
    @Column(name = "platform", length = 20)
    private String platform;
```

Add getter/setter:

```java
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
```

- [ ] **Step 4: Add `platform` to `BaseTranscript` domain model**

Add field `private String platform;` after `private Instant transcribedAt;`.

Update the all-args constructor to include `String platform` as the last parameter.

Add getter/setter:

```java
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
```

- [ ] **Step 5: Verify compilation**

Run: `./mvnw compile -q`
Expected: Compiles successfully

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: add platform column to base_transcripts and transcription_jobs"
```

---

## Task 4: Update Mappers and VideoService for Platform Support

**Files:**
- Modify: `src/main/java/com/app/categorise/application/mapper/VideoMapper.java`
- Modify: `src/main/java/com/app/categorise/domain/service/VideoService.java`
- Modify: `src/main/java/com/app/categorise/domain/service/TranscriptionJobService.java`

- [ ] **Step 1: Update `VideoMapper.createBaseTranscriptEntity`**

Change the method signature to accept `VideoPlatform`:

```java
    public BaseTranscriptEntity createBaseTranscriptEntity(String videoUrl, String transcriptText, VideoMetadata metadata, VideoPlatform platform) {
        BaseTranscriptEntity entity = new BaseTranscriptEntity(
            videoUrl,
            transcriptText,
            null,
            metadata.getDescription(),
            metadata.getTitle(),
            (double) metadata.getDuration(),
            Instant.ofEpochSecond(metadata.getUploadedEpoch()),
            metadata.getAccountId(),
            metadata.getAccount(),
            metadata.getIdentifierId(),
            metadata.getIdentifier()
        );
        entity.setPlatform(platform.name());
        return entity;
    }
```

Add imports:
```java
import com.app.categorise.domain.model.VideoPlatform;
import com.app.categorise.data.dto.VideoMetadata;
```

- [ ] **Step 2: Update `VideoService` to use platform detection and conditional TikTok args**

Add import:
```java
import com.app.categorise.domain.model.VideoPlatform;
```

Update `extractAudioAndMetadata` — make TikTok args conditional:

Replace the block:
```java
            // TikTok-specific fixes for anti-bot measures
            command.add("--extractor-args");
            command.add("tiktok:api_hostname=api22-normal-c-useast1a.tiktokv.com");
```

With:
```java
            // TikTok-specific args for anti-bot measures (only needed for TikTok URLs)
            if (VideoPlatform.fromUrl(videoUrl) == VideoPlatform.TIKTOK) {
                command.add("--extractor-args");
                command.add("tiktok:api_hostname=api22-normal-c-useast1a.tiktokv.com");
            }
```

Update `_processVideoAndCreateTranscript` — determine platform from metadata:

After `VideoMetadata metadata = extractMetadata(files.getMetadataFile());`, add:
```java
                VideoPlatform platform = metadata.getExtractor() != null
                    ? VideoPlatform.fromExtractor(metadata.getExtractor())
                    : VideoPlatform.fromUrl(videoUrl);
```

Update the `videoMapper.createBaseTranscriptEntity` call to pass `platform`:
```java
                baseTranscript = videoMapper.createBaseTranscriptEntity(videoUrl, transcriptText, metadata, platform);
```

Update javadoc on `extractAudioAndMetadata`:
```java
    /**
     * @param videoUrl The video URL to download and extract audio from.
     * @return A ProcessedVideoFiles containing the audio file (output.mp3) and metadata file (output.info.json).
     * @throws Exception If the download or extraction process fails.
     */
```

- [ ] **Step 3: Update `TranscriptionJobService.createOrGetExisting` to set platform**

In the `createOrGetExisting` method, set platform on all newly created jobs.

After `job.setVideoUrl(videoUrl);` in the "existing transcript" path (line ~79):
```java
            job.setPlatform(VideoPlatform.fromUrl(videoUrl).name());
```

After `job.setVideoUrl(videoUrl);` in the "new PENDING job" path (line ~88):
```java
            job.setPlatform(VideoPlatform.fromUrl(videoUrl).name());
```

Add import:
```java
import com.app.categorise.domain.model.VideoPlatform;
```

- [ ] **Step 4: Run all tests**

Run: `./mvnw test`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: wire platform detection through VideoService, VideoMapper, and TranscriptionJobService"
```

---

## Task 5: Update API DTOs

**Files:**
- Modify: `src/main/java/com/app/categorise/api/dto/TranscriptDtoWithAliases.java`
- Modify: `src/main/java/com/app/categorise/api/dto/JobStatusDto.java`
- Modify: `src/main/java/com/app/categorise/application/mapper/VideoMapper.java`
- Modify: `src/main/java/com/app/categorise/application/mapper/JobMapper.java`

- [ ] **Step 1: Add `platform` to `TranscriptDtoWithAliases`**

Replace the record definition:

```java
public record TranscriptDtoWithAliases(
    UUID id,
    String videoUrl,
    String transcript,
    String structuredContent,
    String description,
    String title,
    Double duration,
    Instant uploadedAt,
    String accountId,
    String account,
    String identifierId,
    String identifier,
    String alias,
    UUID categoryId,
    String category,
    Instant createdAt,
    String notes,
    String platform
) {}
```

- [ ] **Step 2: Add `platform` to `JobStatusDto`**

Replace the record definition:

```java
public record JobStatusDto(
    UUID id,
    String videoUrl,
    String status,
    String errorMessage,
    int retryCount,
    Instant updatedAt,
    UUID baseTranscriptId,
    UUID userTranscriptId,
    String transcriptTitle,
    String platform
) {}
```

- [ ] **Step 3: Update `VideoMapper.buildResponse` to include `platform`**

In the `buildResponse(BaseTranscriptEntity, UserTranscriptEntity, String, String)` method, add `baseTranscript.getPlatform()` as the last argument:

```java
    public TranscriptDtoWithAliases buildResponse(BaseTranscriptEntity baseTranscript, UserTranscriptEntity userTranscript,
                                                  String categoryName, String alias) {
        return new TranscriptDtoWithAliases(
            userTranscript.getId(),
            baseTranscript.getVideoUrl(),
            baseTranscript.getTranscript(),
            baseTranscript.getStructuredContent(),
            baseTranscript.getDescription(),
            baseTranscript.getTitle(),
            baseTranscript.getDuration() != null ? baseTranscript.getDuration() : 0.0,
            baseTranscript.getUploadedAt(),
            baseTranscript.getAccountId(),
            baseTranscript.getAccount(),
            baseTranscript.getIdentifierId(),
            baseTranscript.getIdentifier(),
            alias,
            userTranscript.getCategoryId(),
            categoryName,
            userTranscript.getCreatedAt(),
            userTranscript.getNotes(),
            baseTranscript.getPlatform()
        );
    }
```

- [ ] **Step 4: Update `JobMapper.toDto` to include `platform`**

```java
    public JobStatusDto toDto(TranscriptionJobEntity entity) {
        String title = null;
        UserTranscriptEntity ut = entity.getUserTranscript();
        if (ut != null) {
            BaseTranscriptEntity bt = ut.getBaseTranscript();
            if (bt != null) {
                title = bt.getTitle();
            }
        }

        return new JobStatusDto(
            entity.getId(),
            entity.getVideoUrl(),
            entity.getStatus().name(),
            entity.getErrorMessage(),
            entity.getRetryCount(),
            entity.getUpdatedAt(),
            entity.getBaseTranscriptId(),
            entity.getUserTranscriptId(),
            title,
            entity.getPlatform()
        );
    }
```

- [ ] **Step 5: Fix test compilation — update `TranscriptDtoWithAliases` constructor calls in tests**

In `VideoServiceTest.java`, update the `expectedResponse` construction in `setUp()` to add `null` (or a platform string) as the last argument:

```java
        expectedResponse = new TranscriptDtoWithAliases(
                userTranscriptId,
                videoUrl,
                transcriptText,
                null, // structuredContent
                "Test Description",
                "Test Video",
                30.0,
                Instant.now(),
                "testAccountId",
                "testAccount",
                "testChannelId",
                "testChannel",
                "recipe",
                categoryId,
                "testCategory",
                Instant.now(),
                null, // notes
                null  // platform
        );
```

Search all test files for other `TranscriptDtoWithAliases(` constructor calls and add the `platform` parameter.

- [ ] **Step 6: Run all tests**

Run: `./mvnw test`
Expected: All tests PASS

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: add platform field to TranscriptDtoWithAliases and JobStatusDto"
```

---

## Task 6: Update OpenAI Prompt and Dockerfile Comment

**Files:**
- Modify: `src/main/java/com/app/categorise/data/client/openai/OpenAIClientImpl.java`
- Modify: `Dockerfile`

- [ ] **Step 1: Update OpenAI prompt**

In `OpenAIClientImpl.buildDeveloperPrompt()`, change:

```java
"You are an AI that classifies short-form social media videos (e.g., TikToks). You are given the title, description, and transcript of a video.
```

To:

```java
"You are an AI that classifies short-form social media videos (e.g., TikToks, YouTube Shorts, Instagram Reels). You are given the title, description, and transcript of a video.
```

- [ ] **Step 2: Update Dockerfile comment**

Change:

```dockerfile
# Install yt-dlp via pip with curl_cffi for TikTok browser impersonation support
```

To:

```dockerfile
# Install yt-dlp via pip with curl_cffi for browser impersonation support
```

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "chore: update OpenAI prompt and Dockerfile comment for multi-platform"
```

---

## Task 7: Update VideoService Javadoc Comments

**Files:**
- Modify: `src/main/java/com/app/categorise/domain/service/VideoService.java`

- [ ] **Step 1: Clean up all TikTok-specific comments**

The class-level and method-level javadocs should be platform-agnostic. Verify and update:

1. Method `extractAudioAndMetadata` javadoc — already updated in Task 4
2. Any remaining `@param videoUrl The TikTok video URL` → `@param videoUrl The video URL`
3. Any remaining inline comments mentioning TikTok unnecessarily

- [ ] **Step 2: Run tests**

Run: `./mvnw test`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "chore: clean up TikTok-specific comments in VideoService"
```

---

## Task 8: iOS App — Add `platform` Field Across Models and Mappers

**Files (all in `/Users/apushpavannan/personal/TranscribeAssistant-ios/Scoop/`):**
- Modify: `models/api/TranscriptResponse.swift`
- Modify: `models/domain/Transcript.swift`
- Modify: `models/entity/TranscriptEntity.swift`
- Modify: `models/api/TranscriptionJob.swift`
- Modify: `models/domain/ActivityItem.swift`
- Modify: `models/mapper/TranscriptMapper.swift`

- [ ] **Step 1: Add `platform` to `TranscriptResponse.swift`**

Add after `let notes: String?`:

```swift
    let platform: String?
```

- [ ] **Step 2: Add `platform` to `Transcript.swift`**

Add field after `var notes: String?`:

```swift
    let platform: String?
```

Update the main `init` — add `platform: String? = nil` as the last parameter, and `self.platform = platform` in the body.

Update the `init(from entity: TranscriptEntity)` — add:
```swift
        self.platform = entity.platform
```

- [ ] **Step 3: Add `platform` to `TranscriptEntity.swift`**

Add field after `var notes: String?`:

```swift
    var platform: String?
```

Update `init` — add `platform: String? = nil` as the last parameter, and `self.platform = platform` in the body.

- [ ] **Step 4: Add `platform` to `TranscriptionJob.swift`**

Add field after `let transcriptTitle: String?`:

```swift
    let platform: String?
```

- [ ] **Step 5: Add `platform` to `ActivityItem.swift`**

Add field after `let updatedAt: String`:

```swift
    let platform: String?
```

Update `displayTitle` computed property — add a platform-based label option that takes priority over URL parsing (but still falls back to URL parsing if platform is nil):

Replace the existing `displayTitle` computed property with:

```swift
    var displayTitle: String {
        // 1. Use the title from the backend if available
        if let title = transcriptTitle, !title.isEmpty {
            return title
        }

        // 2. Friendly label from platform field (from backend)
        if let platform = platform?.uppercased() {
            switch platform {
            case "YOUTUBE": return "YouTube video"
            case "TIKTOK": return "TikTok video"
            case "INSTAGRAM": return "Instagram video"
            case "VIMEO": return "Vimeo video"
            case "TWITTER": return "X post"
            case "FACEBOOK": return "Facebook video"
            case "REDDIT": return "Reddit post"
            case "TWITCH": return "Twitch clip"
            case "DAILYMOTION": return "Dailymotion video"
            default: break
            }
        }

        // 3. Fallback: friendly label from URL host
        if let url = URL(string: videoUrl), let host = url.host?.lowercased() {
            if host.contains("youtube.com") || host.contains("youtu.be") {
                return "YouTube video"
            } else if host.contains("tiktok.com") {
                return "TikTok video"
            } else if host.contains("vimeo.com") {
                return "Vimeo video"
            } else if host.contains("instagram.com") {
                return "Instagram video"
            } else if host.contains("twitter.com") || host.contains("x.com") {
                return "X post"
            } else if host.contains("reddit.com") {
                return "Reddit post"
            }

            // 4. For direct file URLs, show filename without extension
            let pathComponent = url.lastPathComponent
            if !pathComponent.isEmpty && pathComponent != "/" && pathComponent.contains(".") {
                return (pathComponent as NSString).deletingPathExtension
            }

            // 5. Fallback to cleaned-up domain
            return host.replacingOccurrences(of: "www.", with: "")
        }

        // 6. Last resort
        return videoUrl
    }
```

Update the `ActivityItem.from(_ job:)` mapper to include `platform`:

```swift
    static func from(_ job: TranscriptionJob) -> ActivityItem? {
        guard let status = ActivityStatus(rawValue: job.status) else {
            print("⚠️ ActivityItem: Unknown status '\(job.status)' for job \(job.id)")
            return nil
        }
        
        return ActivityItem(
            id: job.id,
            videoUrl: job.videoUrl,
            status: status,
            baseTranscriptId: job.baseTranscriptId,
            userTranscriptId: job.userTranscriptId,
            transcriptTitle: job.transcriptTitle,
            errorMessage: job.errorMessage,
            retryCount: job.retryCount,
            updatedAt: job.updatedAt,
            platform: job.platform
        )
    }
```

- [ ] **Step 6: Update `TranscriptMapper.swift`**

In `Transcript.from(_ response:)`, add `platform: response.platform` as the last argument.

In `TranscriptEntity.from(_ model:)`, add `platform: model.platform` as the last argument.

In `TranscriptEntity.toDomain()`, add `platform: self.platform` as the last argument.

- [ ] **Step 7: Build the iOS project in Xcode to verify compilation**

Open the project and build (Cmd+B). Fix any compilation errors from missing parameters.

- [ ] **Step 8: Commit**

```bash
cd /Users/apushpavannan/personal/TranscribeAssistant-ios
git add -A
git commit -m "feat: add platform field across models, mappers, and ActivityItem display"
```

---

## Task 9: Final Verification

- [ ] **Step 1: Run all backend tests**

Run: `./mvnw test`
Expected: All tests PASS

- [ ] **Step 2: Verify the full compilation and no remaining TikTok references in wrong places**

Run: `grep -rn "TikTokMetadata" src/main/ src/test/`
Expected: No results (all renamed to VideoMetadata)

Run: `grep -rn "TikTok" src/main/java/ --include="*.java" | grep -v "tiktok.com" | grep -v "TikToks," | grep -v "TIKTOK" | grep -v "VideoPlatform"`
Expected: No results (all TikTok-specific references cleaned up, only domain names and enum values remain)

- [ ] **Step 3: Final commit**

```bash
git add -A
git commit -m "feat: multi-platform video support — complete"
```
