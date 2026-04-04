# AI-Generated Title Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an AI-generated title to every video, stored as `generated_title` on `base_transcripts`, returned via the API, and displayed in the iOS app as a fallback over the yt-dlp title.

**Architecture:** Piggyback on the existing `classifyAndSuggestAlias` OpenAI call to return a 4th field `generatedTitle`. Store it in a new DB column. Pass it through entity → domain → DTO → API response → iOS.

**Tech Stack:** Java 24, Spring Boot, PostgreSQL/Flyway, Swift/SwiftData (iOS)

---

### Task 1: Database Migration

**Files:**
- Create: `src/main/resources/db/migration/V22__add_generated_title_to_base_transcripts.sql`

- [ ] **Step 1: Create the migration file**

```sql
ALTER TABLE base_transcripts ADD COLUMN generated_title VARCHAR(255);
```

- [ ] **Step 2: Verify migration compiles**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V22__add_generated_title_to_base_transcripts.sql
git commit -m "feat: add generated_title column to base_transcripts"
```

---

### Task 2: Update TranscriptCategorisationResult DTO

The AI response DTO is a record that carries the OpenAI response. Add `generatedTitle` as a 4th field.

**Files:**
- Modify: `src/main/java/com/app/categorise/data/dto/TranscriptCategorisationResult.java`

- [ ] **Step 1: Add generatedTitle to the record**

Replace the entire record with:

```java
package com.app.categorise.data.dto;

/**
 * A DTO representing the result of an AI classification call.
 * This object holds the canonical categoryId (if found), a generic topic, a suggested alias,
 * and an AI-generated title from the AI.
 *
 * @param category        The special, predefined categoryId if the content matches one (e.g., "Recipe"). Can be null.
 * @param genericTopic    A stable, one-word keyword for the general topic (e.g., "tech"). Used for grouping non-special content.
 * @param suggestedAlias  A creative, trendy alias for the video suggested by the AI.
 * @param generatedTitle  A short, engaging AI-generated title capturing the video's tone and main point. Can be null.
 */
public record TranscriptCategorisationResult(String category, String genericTopic, String suggestedAlias, String generatedTitle) { }
```

- [ ] **Step 2: Verify it compiles (expect failures from callers — that's fine for now)**

Run: `./mvnw compile -q 2>&1 | head -20`
Expected: Compilation errors in `OpenAIClientImpl.java` and `MockOpenAIClient.java` (they construct this record with 3 args). This is expected — we'll fix them in Tasks 3 and 4.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/app/categorise/data/dto/TranscriptCategorisationResult.java
git commit -m "feat: add generatedTitle field to TranscriptCategorisationResult"
```

---

### Task 3: Update OpenAIClientImpl — Prompt and Parsing

Update the developer prompt to request `generatedTitle` and parse it from the response.

**Files:**
- Modify: `src/main/java/com/app/categorise/data/client/openai/OpenAIClientImpl.java`

- [ ] **Step 1: Update `buildDeveloperPrompt` to include generatedTitle instruction**

In `OpenAIClientImpl.java`, find the `buildDeveloperPrompt` method. Replace the entire method body with:

```java
public String buildDeveloperPrompt(List<String> categories) {
    String categoryList = String.join(", ", categories.stream().map(c -> "\"" + c + "\"").toArray(String[]::new));

    return "You are an AI that classifies short-form social media videos (e.g., TikToks, YouTube Shorts, Instagram Reels). You are given the title, description, and transcript of a video. Respond ONLY with a valid JSON object with **exactly** four keys: 'categoryId', 'genericTopic', 'suggestedAlias', and 'generatedTitle'.\n" +
        "1. 'categoryId': If the content primarily belongs to one of the following special categories, ["
        + categoryList +
        "], provide that categoryId name. Otherwise, this MUST be null.\n" +
        "2. \"genericTopic\": Return a single, lowercase, one-word keyword that describes the overall topic (e.g., \"tech\", \"fashion\", \"comedy\", \"health\"). This field MUST always be present.\n" +
        "3. 'suggestedAlias': Create a trendy, engaging, and short (1-3 words) alias for the video. This alias should be catchy and follow recent trends in social media. Make sure not be cringe. Make sure it isn't specific to the video. It's supposed to be an alias for the categoryId \n" +
        "4. 'generatedTitle': Generate a short, engaging title (max 60 characters) that captures the tone and main point of the video. Base this on the transcript and description content, NOT the original title. Think of it as a headline for a phone screen — punchy, clear, and true to the vibe of the video. This field MUST always be present.\n\n" +
        "DO NOT include any explanation or extra text. Just output the JSON object.\n\n";
}
```

- [ ] **Step 2: Update `parseResponse` to extract generatedTitle**

In `OpenAIClientImpl.java`, find the `parseResponse` method. Replace the entire method body with:

```java
private TranscriptCategorisationResult parseResponse(String jsonResponse) {
    try {
        // The model sometimes wraps the JSON in Markdown code blocks (e.g., ```json ... ```)
        // We need to extract the raw JSON string.
        if (jsonResponse.contains("```")) {
            jsonResponse = jsonResponse.substring(jsonResponse.indexOf('{'), jsonResponse.lastIndexOf('}') + 1);
        }

        JsonNode root = objectMapper.readTree(jsonResponse);
        String category = root.has("categoryId") && !root.get("categoryId").isNull()
                ? root.get("categoryId").asText()
                : null;
        String topic = root.path("genericTopic").asText("default-topic");
        String alias = root.path("suggestedAlias").asText("default-alias");
        String generatedTitle = root.path("generatedTitle").asText(null);
        return new TranscriptCategorisationResult(category, topic, alias, generatedTitle);
    } catch (JsonProcessingException e) {
        throw new RuntimeException("Failed to parse OpenAI response", e);
    }
}
```

- [ ] **Step 3: Verify it compiles (MockOpenAIClient will still fail — fixed in Task 4)**

Run: `./mvnw compile -q 2>&1 | head -10`
Expected: Only `MockOpenAIClient.java` should fail now.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/app/categorise/data/client/openai/OpenAIClientImpl.java
git commit -m "feat: update OpenAI prompt and parsing for generatedTitle"
```

---

### Task 4: Update MockOpenAIClient

The mock client is used in dev mode. Update it to return a mock `generatedTitle`.

**Files:**
- Modify: `src/main/java/com/app/categorise/data/client/openai/MockOpenAIClient.java`

- [ ] **Step 1: Find the `classifyAndSuggestAlias` method and update the return statement**

The method currently returns a 3-arg `TranscriptCategorisationResult`. Add a 4th argument for the mock generated title. Find the return statement in `classifyAndSuggestAlias` and change it to:

```java
return new TranscriptCategorisationResult("Recipe", "cooking", "Chef Mode", "Mock Generated Title");
```

- [ ] **Step 2: Verify full project compiles**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/app/categorise/data/client/openai/MockOpenAIClient.java
git commit -m "feat: update MockOpenAIClient with generatedTitle"
```

---

### Task 5: Update BaseTranscriptEntity

Add the `generatedTitle` field to the JPA entity.

**Files:**
- Modify: `src/main/java/com/app/categorise/data/entity/BaseTranscriptEntity.java`

- [ ] **Step 1: Add the field declaration**

After line 40 (`private String platform;`), add:

```java
@Column(name = "generated_title")
private String generatedTitle;
```

- [ ] **Step 2: Add getter and setter**

After the `setPlatform` method (after line 192), add:

```java
public String getGeneratedTitle() {
    return generatedTitle;
}

public void setGeneratedTitle(String generatedTitle) {
    this.generatedTitle = generatedTitle;
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/app/categorise/data/entity/BaseTranscriptEntity.java
git commit -m "feat: add generatedTitle field to BaseTranscriptEntity"
```

---

### Task 6: Update BaseTranscript Domain Model

Add the `generatedTitle` field to the domain model.

**Files:**
- Modify: `src/main/java/com/app/categorise/domain/model/BaseTranscript.java`

- [ ] **Step 1: Add the field declaration**

After line 24 (`private String platform;`), add:

```java
private String generatedTitle;
```

- [ ] **Step 2: Add getter and setter**

After the `setPlatform` method (after line 167), add:

```java
public String getGeneratedTitle() {
    return generatedTitle;
}

public void setGeneratedTitle(String generatedTitle) {
    this.generatedTitle = generatedTitle;
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/app/categorise/domain/model/BaseTranscript.java
git commit -m "feat: add generatedTitle field to BaseTranscript domain model"
```

---

### Task 7: Update TranscriptDtoWithAliases API DTO

Add `generatedTitle` to the API response DTO.

**Files:**
- Modify: `src/main/java/com/app/categorise/api/dto/TranscriptDtoWithAliases.java`

- [ ] **Step 1: Add generatedTitle field to the record**

Replace the entire record with:

```java
package com.app.categorise.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for Transcript with aliases for categories.
 * This class is used to represent a transcript with its associated metadata,
 * including aliases for categories.
 * Not saved to db, only used in API response.
 */
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
    /** The final, user-visible alias for the transcript (e.g., "Big-Back", "Tech-Tok"). */
    String alias,
    /** The special, predefined categoryId */
    UUID categoryId,
    /** The special, predefined category name (e.g., "Recipe"). */
    String category,
    Instant createdAt,
    /** User's personal notes for this transcript. */
    String notes,
    String platform,
    /** AI-generated title capturing the video's tone and main point. Null for older transcripts. */
    String generatedTitle
) {}
```

- [ ] **Step 2: Verify it compiles (expect failures from callers — VideoMapper, tests)**

Run: `./mvnw compile -q 2>&1 | head -20`
Expected: Compilation errors in `VideoMapper.java` and test files that construct this record. This is expected — we'll fix them next.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/app/categorise/api/dto/TranscriptDtoWithAliases.java
git commit -m "feat: add generatedTitle field to TranscriptDtoWithAliases"
```

---

### Task 8: Update VideoMapper

Update the mapper to pass `generatedTitle` through from entity to DTO.

**Files:**
- Modify: `src/main/java/com/app/categorise/application/mapper/VideoMapper.java`

- [ ] **Step 1: Update the `buildResponse(BaseTranscriptEntity, UserTranscriptEntity, String, String)` method**

Find the `buildResponse` method that takes 4 parameters (line 64-86). Replace it with:

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
        baseTranscript.getPlatform(),
        baseTranscript.getGeneratedTitle()
    );
}
```

- [ ] **Step 2: Verify it compiles (test files may still fail — fixed in Task 10)**

Run: `./mvnw compile -q 2>&1 | head -20`
Expected: Test compilation errors only (test files construct `TranscriptDtoWithAliases` with old arg count).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/app/categorise/application/mapper/VideoMapper.java
git commit -m "feat: pass generatedTitle through VideoMapper"
```

---

### Task 9: Update VideoService to Store Generated Title

After categorisation, store the `generatedTitle` from the AI response on the base transcript entity.

**Files:**
- Modify: `src/main/java/com/app/categorise/domain/service/VideoService.java`

- [ ] **Step 1: Set generatedTitle after categorisation**

In `_processVideoAndCreateTranscript`, find the block after `categorisationService.classifyAndSuggestAlias(...)` (around line 240-246). After line 246, add logic to set the generated title on the base transcript. The section should look like:

```java
// Create new user association with categorization
TranscriptCategorisationResult categorisationResult =
    categorisationService.classifyAndSuggestAlias(
        baseTranscript.getTranscript(),
        baseTranscript.getTitle(),
        baseTranscript.getDescription()
    );

// Set the AI-generated title if not already present
if (baseTranscript.getGeneratedTitle() == null && categorisationResult.generatedTitle() != null) {
    baseTranscript.setGeneratedTitle(categorisationResult.generatedTitle());
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./mvnw compile -q 2>&1 | head -10`
Expected: Only test compilation errors remain.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/app/categorise/domain/service/VideoService.java
git commit -m "feat: store generatedTitle from AI categorisation result"
```

---

### Task 10: Fix Tests

Update existing tests to account for the new `generatedTitle` field in DTOs and records.

**Files:**
- Modify: `src/test/java/com/app/categorise/domain/service/VideoServiceTest.java`
- Modify: any other test files that fail compilation

- [ ] **Step 1: Update `expectedResponse` in VideoServiceTest.setUp()**

In `VideoServiceTest.java`, find the `expectedResponse` construction in `setUp()` (line 86-105). Add `null` as the last argument for `generatedTitle`:

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
        null, // platform
        null  // generatedTitle
);
```

- [ ] **Step 2: Check for any other test files that construct TranscriptDtoWithAliases or TranscriptCategorisationResult**

Run: `grep -rn "TranscriptDtoWithAliases\|TranscriptCategorisationResult" src/test/ --include="*.java"`

Fix any other constructor calls by adding the `generatedTitle` argument (use `null` for `TranscriptDtoWithAliases`, use `"Test Generated Title"` for `TranscriptCategorisationResult`).

- [ ] **Step 3: Verify all tests compile and pass**

Run: `./mvnw test -q`
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add src/test/
git commit -m "test: update tests for generatedTitle field"
```

---

### Task 11: Update iOS App — API Response and Domain Models

Add `generatedTitle` to the iOS data models and mapper.

**Files:**
- Modify: `/Users/apushpavannan/personal/TranscribeAssistant-ios/Scoop/models/api/TranscriptResponse.swift`
- Modify: `/Users/apushpavannan/personal/TranscribeAssistant-ios/Scoop/models/domain/Transcript.swift`
- Modify: `/Users/apushpavannan/personal/TranscribeAssistant-ios/Scoop/models/entity/TranscriptEntity.swift`
- Modify: `/Users/apushpavannan/personal/TranscribeAssistant-ios/Scoop/models/mapper/TranscriptMapper.swift`

- [ ] **Step 1: Add `generatedTitle` to `TranscriptResponse.swift`**

After `let platform: String?` (line 20), add:

```swift
let generatedTitle: String?
```

- [ ] **Step 2: Add `generatedTitle` to `Transcript.swift`**

After `let platform: String?` (line 20), add the property:

```swift
let generatedTitle: String?
```

Update the `init(...)` to include `generatedTitle: String? = nil` as the last parameter, and add `self.generatedTitle = generatedTitle` in the body.

Update the `init(from entity: TranscriptEntity)` to include:

```swift
self.generatedTitle = entity.generatedTitle
```

- [ ] **Step 3: Add `generatedTitle` to `TranscriptEntity.swift`**

After `var platform: String?` (line 22), add:

```swift
var generatedTitle: String?
```

Update the `init(...)` to include `generatedTitle: String? = nil` as the last parameter, and add `self.generatedTitle = generatedTitle` in the body.

- [ ] **Step 4: Update `TranscriptMapper.swift`**

In the `Transcript.from(_ response:)` method, add after `platform: response.platform`:

```swift
generatedTitle: response.generatedTitle
```

In the `TranscriptEntity.from(_ model:)` method, add after `platform: model.platform`:

```swift
generatedTitle: model.generatedTitle
```

In the `toDomain()` method, add after `platform: self.platform`:

```swift
generatedTitle: self.generatedTitle
```

- [ ] **Step 5: Commit**

```bash
cd /Users/apushpavannan/personal/TranscribeAssistant-ios
git add Scoop/models/
git commit -m "feat: add generatedTitle to iOS data models"
```

---

### Task 12: Update iOS UI — Display Generated Title

Update `TranscriptCard.swift` and `TranscribeDetailsScreen.swift` to prefer `generatedTitle` over `title`.

**Files:**
- Modify: `/Users/apushpavannan/personal/TranscribeAssistant-ios/Scoop/views/Feed/TranscriptCard.swift`
- Modify: `/Users/apushpavannan/personal/TranscribeAssistant-ios/Scoop/views/TranscriptDetails/TranscribeDetailsScreen.swift`

- [ ] **Step 1: Update `TranscriptCard.swift`**

Find where `transcript.title` is displayed (line 8). Replace:

```swift
Text(transcript.title)
```

With:

```swift
Text(transcript.generatedTitle ?? transcript.title)
```

- [ ] **Step 2: Update `TranscribeDetailsScreen.swift`**

Search for any usage of `transcript.title` in this file and replace with `transcript.generatedTitle ?? transcript.title`.

- [ ] **Step 3: Build the iOS project in Xcode to verify**

Open Xcode and build (Cmd+B). Expected: BUILD SUCCEEDED.

- [ ] **Step 4: Commit**

```bash
cd /Users/apushpavannan/personal/TranscribeAssistant-ios
git add Scoop/views/
git commit -m "feat: display AI-generated title with fallback to yt-dlp title"
```
