# Plan: Improve Search Relevance via Richer Structured Content

**Goal:** Make semantic search actually useful by ensuring every transcript's structured content includes rich semantic metadata (tags, categories, topics) that the embedding model can use to distinguish content types.

**Root cause of current bad results:** The general prompt (used for ~24 of 26 seeded categories) extracts only `keyPoints` — no metadata. So searching "breakfast" or "indian" produces vectors with nothing to distinguish them from unrelated content.

---

## Phase 0: Confirmed Facts

### Seeded categories (CategorySeeder.java:31-58)
26 categories: Recipes, DIY, Cleaning, Sport, Coding, Tech, Movies, TV, Music, Dance, Comedy, Investing, Travel, Lifestyle, Workouts, Diets, Health, Makeup, Fashion, Gossip, Education, Pets, Jewellery, Cars, AI, Politics

### Structured content logic (OpenAIClientImpl.java:145-167)
Only 2 specialized prompts exist:
- **isCooking** → matches "Cooking", "Recipe", "Recipes", "Food" → `buildCookingPrompt()` — has dishName, cuisine, meal, tags, ingredients, steps ✅
- **isBeauty** → matches "Skincare", "Makeup", "Beauty" → `buildBeautyPrompt()` — has concern, skinType, tags, products, steps ✅
- **Everything else** → `buildGeneralPrompt()` — only keyPoints, no metadata ❌

### Embedding input (VideoService.java:578-586)
`generatedTitle + "\n" + description + "\n" + structuredContent` truncated to 6000 chars. No schema changes needed — richer structured content automatically flows through.

### Re-extract gap
Structured content is extracted once in VideoService (only if `structuredContent == null || isEmpty()`). No re-extract endpoint exists. After updating prompts, existing transcripts need re-extraction + re-embedding.

### Admin endpoint (AdminController.java)
Only `POST /api/admin/backfill-embeddings` exists — regenerates embeddings from existing structured content but does NOT re-extract structured content.

### Search pipeline
`TranscriptService.semanticSearch` → `expandSearchQuery(query)` → `embed(expandedQuery)` → `searchByEmbedding` with `SIMILARITY_THRESHOLD = 0.8` (currently loose).

---

## Phase 1: Add Content Type Prompts for All Category Groups

**File:** `OpenAIClientImpl.java`
**Methods to add:** new `build*Prompt` methods + update `buildStructuredContentPrompt`

### Category groupings and their metadata fields

**Fitness** → matches: "Workouts", "Sport", "Diets"
```json
{
  "type": "fitness",
  "workoutType": "e.g. HIIT, yoga, strength training, cardio, pilates",
  "muscleGroups": ["e.g. legs, core, upper body"],
  "equipment": ["e.g. dumbbells, resistance bands, bodyweight"],
  "difficulty": "e.g. beginner, intermediate, advanced",
  "duration": "e.g. 10 minutes, 30 minutes",
  "goal": "e.g. weight loss, muscle gain, flexibility, endurance",
  "tags": ["e.g. no equipment, home workout, morning, low impact"],
  "keyPoints": ["step 1", "step 2"]
}
```

**Finance** → matches: "Investing"
```json
{
  "type": "finance",
  "topic": "e.g. stock market, crypto, real estate, budgeting, saving",
  "assetType": "e.g. stocks, ETFs, crypto, bonds, property",
  "strategy": "e.g. buy and hold, dollar cost averaging, day trading",
  "riskLevel": "e.g. low, medium, high",
  "audience": "e.g. beginner, intermediate, advanced investor",
  "tags": ["e.g. passive income, retirement, tax, recession"],
  "keyPoints": ["point 1", "point 2"]
}
```

**Tech** → matches: "Coding", "Tech", "AI"
```json
{
  "type": "tech",
  "topic": "e.g. machine learning, web development, cybersecurity",
  "language": "e.g. Python, JavaScript, Java, Swift",
  "framework": "e.g. React, Spring Boot, TensorFlow",
  "skillLevel": "e.g. beginner, intermediate, advanced",
  "tags": ["e.g. tutorial, tips, news, comparison, review"],
  "keyPoints": ["point 1", "point 2"]
}
```

**Education** → matches: "Education"
```json
{
  "type": "education",
  "subject": "e.g. history, science, mathematics, psychology",
  "topic": "e.g. World War II, quantum physics, cognitive bias",
  "skillLevel": "e.g. beginner, high school, university",
  "tags": ["e.g. explained, theory, facts, study tips"],
  "keyPoints": ["point 1", "point 2"]
}
```

**Travel** → matches: "Travel"
```json
{
  "type": "travel",
  "destination": "e.g. Bali, Paris, Japan, New York",
  "region": "e.g. Southeast Asia, Europe, North America",
  "tripType": "e.g. solo, couple, family, backpacking, luxury",
  "budget": "e.g. budget, mid-range, luxury",
  "activities": ["e.g. hiking, food tour, sightseeing, beaches"],
  "tags": ["e.g. travel tips, hidden gems, must-see, packing"],
  "keyPoints": ["tip 1", "tip 2"]
}
```

**Entertainment** → matches: "Movies", "TV", "Music", "Dance", "Comedy", "Gossip"
```json
{
  "type": "entertainment",
  "contentSubtype": "e.g. movie review, TV recap, music video, dance tutorial, comedy skit, celebrity gossip",
  "genre": "e.g. horror, romance, pop, hip-hop, stand-up",
  "mood": "e.g. funny, emotional, hype, relaxing",
  "title": "e.g. name of movie/show/song if mentioned",
  "artist": "e.g. artist or creator name if mentioned",
  "tags": ["e.g. review, reaction, tutorial, trending"],
  "keyPoints": ["point 1", "point 2"]
}
```

**Lifestyle** → matches: "Lifestyle", "Fashion", "Jewellery", "Pets", "Cars", "DIY", "Cleaning"
```json
{
  "type": "lifestyle",
  "subcategory": "e.g. fashion, home, pets, cars, DIY, cleaning",
  "topic": "e.g. outfit ideas, dog training, car maintenance, room makeover",
  "tags": ["e.g. budget, aesthetic, tips, before and after, review"],
  "keyPoints": ["point 1", "point 2"]
}
```

**General (improved)** → everything else: "Politics", unmatched categories
```json
{
  "type": "general",
  "topic": "e.g. main subject of the video",
  "tone": "e.g. informative, opinion, debate, motivational",
  "audience": "e.g. general public, young adults, professionals",
  "tags": ["e.g. news, opinion, tips, story, motivational"],
  "keyPoints": ["point 1", "point 2"]
}
```

### Implementation steps

1. Add boolean checks to `buildStructuredContentPrompt` (OpenAIClientImpl.java:145-167):
   ```java
   boolean isFitness = category != null && (
       category.equalsIgnoreCase("Workouts") ||
       category.equalsIgnoreCase("Sport") ||
       category.equalsIgnoreCase("Diets")
   );
   boolean isFinance = category != null && category.equalsIgnoreCase("Investing");
   boolean isTech = category != null && (
       category.equalsIgnoreCase("Coding") ||
       category.equalsIgnoreCase("Tech") ||
       category.equalsIgnoreCase("AI")
   );
   boolean isEducation = category != null && category.equalsIgnoreCase("Education");
   boolean isTravel = category != null && category.equalsIgnoreCase("Travel");
   boolean isEntertainment = category != null && (
       category.equalsIgnoreCase("Movies") ||
       category.equalsIgnoreCase("TV") ||
       category.equalsIgnoreCase("Music") ||
       category.equalsIgnoreCase("Dance") ||
       category.equalsIgnoreCase("Comedy") ||
       category.equalsIgnoreCase("Gossip")
   );
   boolean isLifestyle = category != null && (
       category.equalsIgnoreCase("Lifestyle") ||
       category.equalsIgnoreCase("Fashion") ||
       category.equalsIgnoreCase("Jewellery") ||
       category.equalsIgnoreCase("Pets") ||
       category.equalsIgnoreCase("Cars") ||
       category.equalsIgnoreCase("DIY") ||
       category.equalsIgnoreCase("Cleaning")
   );
   ```

2. Add corresponding `buildFitnessPrompt`, `buildFinancePrompt`, `buildTechPrompt`, `buildEducationPrompt`, `buildTravelPrompt`, `buildEntertainmentPrompt`, `buildLifestylePrompt` methods following the same pattern as `buildCookingPrompt` (lines 169-194).

3. Update the if/else chain in `buildStructuredContentPrompt` to route to new methods.

4. Update `buildGeneralPrompt` to include topic, tone, audience, tags fields.

### Verification
- Grep for all new boolean variable names to confirm they're wired up
- Manually call `POST /api/video/transcribe` with a fitness video URL and check `base_transcripts.structured_content` in DB

---

## Phase 2: Add Re-extract Structured Content Admin Endpoint

**Why needed:** Existing transcripts have structured content extracted with the old minimal prompts. Updating prompts only helps new transcripts unless we re-extract.

### New method in VideoService.java

```java
public CompletableFuture<int[]> reextractAndReembedAll() {
    return CompletableFuture.supplyAsync(() -> {
        // Fetch all base transcripts that have a transcript to work from
        List<BaseTranscriptEntity> transcripts = baseTranscriptRepository.findAllByTranscriptIsNotNull();
        log.info("[reextract] starting count={}", transcripts.size());
        int success = 0, failed = 0;
        for (BaseTranscriptEntity transcript : transcripts) {
            try {
                // Determine category from the first user_transcript that references this base transcript
                String categoryName = resolveCategoryName(transcript);
                // Re-extract structured content
                String structuredContent = openAIClient.extractStructuredContent(
                    transcript.getTranscript(),
                    transcript.getTitle(),
                    categoryName,
                    transcript.getDescription()
                );
                transcript.setStructuredContent(structuredContent);
                baseTranscriptRepository.save(transcript);
                // Re-generate embedding from new structured content
                generateAndStoreEmbedding(transcript);
                success++;
            } catch (Exception e) {
                log.error("[reextract] failed base_transcript_id={}", transcript.getId(), e);
                failed++;
            }
        }
        log.info("[reextract] done success={} failed={}", success, failed);
        return new int[]{success, failed};
    }, mediaExecutor);
}
```

### New repository method in BaseTranscriptRepository.java
```java
List<BaseTranscriptEntity> findAllByTranscriptIsNotNull();
```

### Category resolution
Need a `resolveCategoryName(BaseTranscriptEntity)` helper in VideoService:
- Join to `user_transcripts` → `categories` to get the category name for this base transcript
- Use `jdbcTemplate.queryForObject("SELECT c.name FROM categories c JOIN user_transcripts ut ON ut.category_id = c.id WHERE ut.base_transcript_id = ? LIMIT 1", String.class, transcript.getId())`
- If no category found, pass null (falls through to general prompt)

### New endpoint in AdminController.java
```java
@PostMapping("/reextract-and-reembed")
public CompletableFuture<ResponseEntity<Map<String, Object>>> reextractAndReembed() {
    log.info("POST /api/admin/reextract-and-reembed triggered");
    return videoService.reextractAndReembedAll()
        .thenApply(counts -> ResponseEntity.ok(Map.of(
            "success", counts[0],
            "failed", counts[1]
        )));
}
```

### Verification
- Call `POST /api/admin/reextract-and-reembed`
- Check a fitness transcript in DB — `structured_content` should now have `workoutType`, `muscleGroups`, `tags` fields
- Confirm `embedding` column is also updated (non-null, changed from before)

---

## Phase 3: Threshold Tuning After Re-embed

**File:** `UserTranscriptRepositoryImpl.java:70`
**Current value:** `SIMILARITY_THRESHOLD = 0.8` (very loose, set for debugging)

After re-embedding with richer structured content:
1. Test a few representative searches ("breakfast", "indian", "HIIT workout", "crypto investing")
2. Start at `0.6` and tighten incrementally
3. Target: relevant content appears, unrelated content is cut off
4. Recommended starting point: `0.5` (cosine distance < 0.5 = similarity > 50%)

### How richer content improves threshold effectiveness
With the old general prompt, all transcripts had similar-looking embeddings (just a list of bullet points). The cosine distances between unrelated content were small, making it impossible to find a threshold that kept relevant results and cut irrelevant ones.

With category-specific metadata, embeddings are more separated in vector space — a fitness video embedding sits far from a finance video embedding. A threshold of 0.5 will naturally cut cross-category noise while keeping relevant results.

---

## Phase 4: Query Expansion Verification

**File:** `TranscriptService.java:71`
**Already implemented:** `openAIClient.expandSearchQuery(query)` before embedding

After Phase 1-2, verify expansion is working well by checking debug logs:
```
[search] query_expansion original='indian' expanded='...'
```

If the expanded query is still too vague, tighten the expansion prompt in `OpenAIClientImpl.expandSearchQuery` to be more domain-aware:
```
"Expand this search query into a descriptive phrase for finding video content. 
Be specific about the topic, category, and context. 
Return only the expanded phrase.\n\nQuery: " + query
```

---

## Execution Order

1. **Phase 1** — Add all new prompts (code change, deploy)
2. **Phase 2** — Add re-extract endpoint (code change, deploy), then call `POST /api/admin/reextract-and-reembed` in prod
3. **Phase 3** — Test searches, tune threshold from 0.8 → 0.5 or tighter
4. **Phase 4** — Check query expansion logs, tighten prompt if needed

---

## What NOT to do
- Do not add a separate `labels` column to the DB — the metadata belongs in `structured_content` and flows through `buildEmbeddingInput` automatically
- Do not change `buildEmbeddingInput` — it already uses `structuredContent` correctly
- Do not skip Phase 2 — new prompts only help new transcripts without re-extraction
- Do not set threshold below 0.3 without testing — too tight will return zero results
