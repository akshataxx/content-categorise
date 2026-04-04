# AI-Generated Title Feature

**Date:** 2026-04-04  
**Status:** Approved

## Problem

Instagram reels (and some other platforms) don't have meaningful titles. yt-dlp returns a generic placeholder like `"Video by <username>"`, which is displayed prominently in the iOS app. The actual content description lives in the `description` field, but the app shows `title`.

## Solution

Generate an AI title for every video during the existing categorisation OpenAI call. Store it in a new `generated_title` field on `base_transcripts`. The iOS app displays `generatedTitle` when available, falling back to the original yt-dlp `title`.

## Design

### Approach

Piggyback on the existing `classifyAndSuggestAlias` OpenAI call (`gpt-4o`, temp 0.7). The model already receives the transcript, title, and description — everything needed to generate a good title. Adding a 4th field to the JSON response adds zero latency and negligible token cost.

### Title Requirements

- Max 60 characters — fits on 2 lines on a phone screen
- Captures the tone, vibe, and main point of the video
- Based on transcript and description content, not the original yt-dlp title
- Generated for all videos (YouTube, TikTok, Instagram), not just those with bad titles

### Data Flow

#### Database

New Flyway migration `V22__add_generated_title.sql`:

```sql
ALTER TABLE base_transcripts ADD COLUMN generated_title VARCHAR(255);
```

#### Backend Layers

| Layer | File | Change |
|-------|------|--------|
| Entity | `BaseTranscriptEntity.java` | Add `generatedTitle` field with `@Column(name = "generated_title")` |
| Domain Model | `BaseTranscript.java` | Add `generatedTitle` field with getter/setter |
| AI Response DTO | `TranscriptCategorisationResult.java` | Add `generatedTitle` to the record |
| OpenAI Client | `OpenAIClientImpl.java` | Update developer prompt + parse `generatedTitle` from response |
| OpenAI Mock | `MockOpenAIClient.java` | Return mock generated title |
| Mapper | `VideoMapper.java` | Map `generatedTitle` between entity and domain model |
| API DTO | `TranscriptDtoWithAliases.java` | Add `generatedTitle` field to the record |

#### OpenAI Prompt Change

Add to the developer prompt as instruction 4:

```
4. 'generatedTitle': Generate a short, engaging title (max 60 characters) that captures the tone
and main point of the video. Base this on the transcript and description content. Think of it as
a headline for a phone screen — punchy, clear, and true to the vibe of the video. This field
MUST always be present.
```

Expected JSON response shape:

```json
{
  "categoryId": "Recipe",
  "genericTopic": "cooking",
  "suggestedAlias": "Chef Mode",
  "generatedTitle": "The Wrong Way to Smash a Burger 🍔"
}
```

#### Response Parsing

In `parseResponse`, extract with a null fallback:

```java
String generatedTitle = root.path("generatedTitle").asText(null);
```

If the model omits the field, `generatedTitle` remains `null` and the iOS app falls back to the yt-dlp title.

#### iOS Layers

| Layer | File | Change |
|-------|------|--------|
| API Response | `TranscriptResponse.swift` | Add `let generatedTitle: String?` |
| Domain Model | `Transcript.swift` | Add `let generatedTitle: String?` |
| SwiftData Entity | `TranscriptEntity.swift` | Add `generatedTitle` property |
| UI — Card | `TranscriptCard.swift` | Display `generatedTitle ?? title` |
| UI — Details | `TranscribeDetailsScreen.swift` | Display `generatedTitle ?? title` where title is shown |

### Existing Data

Existing transcripts will have `generated_title = NULL`. They continue showing the yt-dlp title. No backfill migration needed — can be added later if desired.

### Error Handling

- If OpenAI omits `generatedTitle` from the response: field is `null`, iOS falls back to `title`
- If `generatedTitle` exceeds 255 chars (unlikely given prompt): database truncation will occur — the prompt constrains to 60 chars, and VARCHAR(255) provides ample buffer
- No change to existing error handling paths — the field is purely additive

### Testing

- Update `VideoServiceTest` to verify `generatedTitle` flows through processing
- Update `TranscriptControllerTest` to verify API response includes `generatedTitle`
- Update `MockOpenAIClient` to return a test generated title
- Update `VideoPlatformTest` or add new test for title fallback logic if applicable
