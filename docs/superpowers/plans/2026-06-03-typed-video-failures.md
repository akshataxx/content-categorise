# Typed Video Failures Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `TranscriptionJobService` message scraping with typed, wrapped video-processing failures that carry retryability.

**Architecture:** Keep wrapped exceptions and keep the current user-facing error messages. Add a `VideoProcessingFailureKind` enum with retryability, attach it to `VideoProcessingException`, classify yt-dlp/process/validation failures at the `VideoService` boundary, and have `TranscriptionJobService` classify jobs from the typed exception cause chain.

**Tech Stack:** Java 21, Spring Boot 3.5, Maven, JUnit 5, AssertJ, Mockito, PostgreSQL Testcontainers.

---

## Current Context

The repo already has a `VideoProcessingException` in `src/main/java/com/app/categorise/exception/VideoProcessingException.java`, but it only stores a message and cause.

`VideoService` currently wraps many failures as `new VideoProcessingException(USER_FACING_..., cause)` and preserves the cause. That is good. The missing piece is a typed failure kind.

`TranscriptionJobService` currently decides retryability by walking exception messages and searching strings like `login required`, `unsupported url`, and `video is too long`. That was a useful defensive patch, but the clean design is:

- String parsing belongs at the boundary where yt-dlp/process output is first observed.
- Job retry logic should read typed failure metadata.
- Wrapped causes should stay intact for diagnostics.
- Public/user-facing messages should not leak raw yt-dlp output.

Run implementation in a clean worktree if possible. The current working tree may contain unrelated security/test changes and user planning files.

## Files

- Create: `src/main/java/com/app/categorise/exception/VideoProcessingFailureKind.java`
- Modify: `src/main/java/com/app/categorise/exception/VideoProcessingException.java`
- Create: `src/test/java/com/app/categorise/exception/VideoProcessingExceptionTest.java`
- Create: `src/main/java/com/app/categorise/domain/service/YtDlpFailureClassifier.java`
- Create: `src/test/java/com/app/categorise/domain/service/YtDlpFailureClassifierTest.java`
- Modify: `src/main/java/com/app/categorise/domain/service/VideoService.java`
- Modify: `src/test/java/com/app/categorise/domain/service/VideoServiceTest.java`
- Modify: `src/main/java/com/app/categorise/domain/service/TranscriptionJobService.java`
- Modify: `src/test/java/com/app/categorise/domain/service/TranscriptionJobServiceTest.java`
- Modify: `src/main/java/com/app/categorise/domain/service/JobPollerService.java`

## Test Command

Use the repo-specific Mockito Java agent.

Focused tests:

```bash
./mvnw -q test -DargLine="-javaagent:/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar" -Dtest=ClassNameTest
```

Full suite:

```bash
./mvnw -q test -DargLine="-javaagent:/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar"
```

If Testcontainers-backed tests run in Codex, run with Docker access/escalation. A sandbox Docker socket failure does not mean Docker is broken.

---

### Task 1: Add Typed Failure Metadata To `VideoProcessingException`

**Files:**
- Create: `src/main/java/com/app/categorise/exception/VideoProcessingFailureKind.java`
- Modify: `src/main/java/com/app/categorise/exception/VideoProcessingException.java`
- Create: `src/test/java/com/app/categorise/exception/VideoProcessingExceptionTest.java`

- [ ] **Step 1: Write the failing exception tests**

Create `src/test/java/com/app/categorise/exception/VideoProcessingExceptionTest.java`:

```java
package com.app.categorise.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VideoProcessingExceptionTest {

    @Test
    @DisplayName("stores failure kind and cause")
    void storesFailureKindAndCause() {
        RuntimeException cause = new RuntimeException("yt-dlp raw output");

        VideoProcessingException ex = new VideoProcessingException(
                VideoProcessingFailureKind.LOGIN_REQUIRED,
                "Could not process video URL",
                cause
        );

        assertThat(ex.getFailureKind()).isEqualTo(VideoProcessingFailureKind.LOGIN_REQUIRED);
        assertThat(ex.isRetryable()).isFalse();
        assertThat(ex.getMessage()).isEqualTo("Could not process video URL");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("legacy constructor defaults to UNKNOWN retryable failure")
    void legacyConstructorDefaultsToUnknownRetryableFailure() {
        VideoProcessingException ex = new VideoProcessingException("legacy message");

        assertThat(ex.getFailureKind()).isEqualTo(VideoProcessingFailureKind.UNKNOWN);
        assertThat(ex.isRetryable()).isTrue();
        assertThat(ex.getMessage()).isEqualTo("legacy message");
    }

    @Test
    @DisplayName("null failure kind defaults to UNKNOWN")
    void nullFailureKindDefaultsToUnknown() {
        VideoProcessingException ex = new VideoProcessingException(
                null,
                "message",
                new RuntimeException("cause")
        );

        assertThat(ex.getFailureKind()).isEqualTo(VideoProcessingFailureKind.UNKNOWN);
        assertThat(ex.isRetryable()).isTrue();
    }
}
```

- [ ] **Step 2: Run the failing test**

Run:

```bash
./mvnw -q test -DargLine="-javaagent:/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar" -Dtest=VideoProcessingExceptionTest
```

Expected: FAIL because `VideoProcessingFailureKind`, `getFailureKind()`, and `isRetryable()` do not exist.

- [ ] **Step 3: Add the failure kind enum**

Create `src/main/java/com/app/categorise/exception/VideoProcessingFailureKind.java`:

```java
package com.app.categorise.exception;

public enum VideoProcessingFailureKind {
    UNSUPPORTED_URL(false),
    LOGIN_REQUIRED(false),
    PRIVATE_VIDEO(false),
    NO_SPACE_LEFT(false),
    VIDEO_TOO_LONG(false),
    RATE_LIMITED(true),
    UPSTREAM_SERVER_ERROR(true),
    NETWORK_TIMEOUT(true),
    INVALID_METADATA(true),
    INCOMPLETE_DATA(true),
    CATEGORISATION_FAILED(true),
    UNKNOWN(true);

    private final boolean retryable;

    VideoProcessingFailureKind(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
```

- [ ] **Step 4: Extend `VideoProcessingException`**

Replace `src/main/java/com/app/categorise/exception/VideoProcessingException.java` with:

```java
package com.app.categorise.exception;

public class VideoProcessingException extends RuntimeException {

    private final VideoProcessingFailureKind failureKind;

    public VideoProcessingException(String message) {
        this(VideoProcessingFailureKind.UNKNOWN, message);
    }

    public VideoProcessingException(String message, Throwable cause) {
        this(VideoProcessingFailureKind.UNKNOWN, message, cause);
    }

    public VideoProcessingException(VideoProcessingFailureKind failureKind, String message) {
        super(message);
        this.failureKind = normalize(failureKind);
    }

    public VideoProcessingException(VideoProcessingFailureKind failureKind, String message, Throwable cause) {
        super(message, cause);
        this.failureKind = normalize(failureKind);
    }

    public VideoProcessingFailureKind getFailureKind() {
        return failureKind;
    }

    public boolean isRetryable() {
        return failureKind.isRetryable();
    }

    private static VideoProcessingFailureKind normalize(VideoProcessingFailureKind failureKind) {
        return failureKind == null ? VideoProcessingFailureKind.UNKNOWN : failureKind;
    }
}
```

- [ ] **Step 5: Verify the exception tests pass**

Run:

```bash
./mvnw -q test -DargLine="-javaagent:/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar" -Dtest=VideoProcessingExceptionTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/app/categorise/exception/VideoProcessingFailureKind.java \
  src/main/java/com/app/categorise/exception/VideoProcessingException.java \
  src/test/java/com/app/categorise/exception/VideoProcessingExceptionTest.java
git commit -m "refactor: add typed video processing failure kinds"
```

---

### Task 2: Classify yt-dlp Failure Output At The Video Boundary

**Files:**
- Create: `src/main/java/com/app/categorise/domain/service/YtDlpFailureClassifier.java`
- Create: `src/test/java/com/app/categorise/domain/service/YtDlpFailureClassifierTest.java`

- [ ] **Step 1: Write classifier tests**

Create `src/test/java/com/app/categorise/domain/service/YtDlpFailureClassifierTest.java`:

```java
package com.app.categorise.domain.service;

import com.app.categorise.exception.VideoProcessingFailureKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class YtDlpFailureClassifierTest {

    @Test
    @DisplayName("classifies unsupported URL from wrapped yt-dlp output")
    void classifiesUnsupportedUrl() {
        RuntimeException root = new RuntimeException("yt-dlp: ERROR: Unsupported URL: https://example.com");
        RuntimeException wrapped = new RuntimeException("Could not process video URL", root);

        assertThat(YtDlpFailureClassifier.classify(wrapped))
                .isEqualTo(VideoProcessingFailureKind.UNSUPPORTED_URL);
    }

    @Test
    @DisplayName("classifies login-required platform output")
    void classifiesLoginRequired() {
        RuntimeException ex = new RuntimeException(
                "ERROR: [Instagram] Requested content is not available, rate-limit reached or login required."
        );

        assertThat(YtDlpFailureClassifier.classify(ex))
                .isEqualTo(VideoProcessingFailureKind.LOGIN_REQUIRED);
    }

    @Test
    @DisplayName("classifies private video output")
    void classifiesPrivateVideo() {
        RuntimeException ex = new RuntimeException("ERROR: This is a private video");

        assertThat(YtDlpFailureClassifier.classify(ex))
                .isEqualTo(VideoProcessingFailureKind.PRIVATE_VIDEO);
    }

    @Test
    @DisplayName("classifies local disk exhaustion")
    void classifiesNoSpaceLeft() {
        RuntimeException ex = new RuntimeException("No space left on device");

        assertThat(YtDlpFailureClassifier.classify(ex))
                .isEqualTo(VideoProcessingFailureKind.NO_SPACE_LEFT);
    }

    @Test
    @DisplayName("classifies timeout output")
    void classifiesTimeout() {
        RuntimeException ex = new RuntimeException("Command timed out after 1 minute(s): yt-dlp");

        assertThat(YtDlpFailureClassifier.classify(ex))
                .isEqualTo(VideoProcessingFailureKind.NETWORK_TIMEOUT);
    }

    @Test
    @DisplayName("classifies rate-limit output as retryable")
    void classifiesRateLimit() {
        RuntimeException ex = new RuntimeException("HTTP Error 429: rate-limit reached");

        assertThat(YtDlpFailureClassifier.classify(ex))
                .isEqualTo(VideoProcessingFailureKind.RATE_LIMITED);
    }

    @Test
    @DisplayName("classifies upstream server errors")
    void classifiesServerError() {
        RuntimeException ex = new RuntimeException("HTTP Error 503: Service Unavailable");

        assertThat(YtDlpFailureClassifier.classify(ex))
                .isEqualTo(VideoProcessingFailureKind.UPSTREAM_SERVER_ERROR);
    }

    @Test
    @DisplayName("unknown process output defaults to UNKNOWN")
    void classifiesUnknown() {
        RuntimeException ex = new RuntimeException("Something unexpected happened");

        assertThat(YtDlpFailureClassifier.classify(ex))
                .isEqualTo(VideoProcessingFailureKind.UNKNOWN);
    }
}
```

- [ ] **Step 2: Run the failing classifier test**

Run:

```bash
./mvnw -q test -DargLine="-javaagent:/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar" -Dtest=YtDlpFailureClassifierTest
```

Expected: FAIL because `YtDlpFailureClassifier` does not exist.

- [ ] **Step 3: Implement the classifier**

Create `src/main/java/com/app/categorise/domain/service/YtDlpFailureClassifier.java`:

```java
package com.app.categorise.domain.service;

import com.app.categorise.exception.VideoProcessingFailureKind;

import java.util.Locale;

final class YtDlpFailureClassifier {

    private YtDlpFailureClassifier() {
    }

    static VideoProcessingFailureKind classify(Throwable failure) {
        String message = collectCauseMessages(failure).toLowerCase(Locale.ROOT);

        if (message.contains("unsupported url") || message.contains("is not a valid url")) {
            return VideoProcessingFailureKind.UNSUPPORTED_URL;
        }
        if (message.contains("login required")) {
            return VideoProcessingFailureKind.LOGIN_REQUIRED;
        }
        if (message.contains("private video")) {
            return VideoProcessingFailureKind.PRIVATE_VIDEO;
        }
        if (message.contains("no space left on device")) {
            return VideoProcessingFailureKind.NO_SPACE_LEFT;
        }
        if (message.contains("timed out") || message.contains("timeout")) {
            return VideoProcessingFailureKind.NETWORK_TIMEOUT;
        }
        if (message.contains("429") || message.contains("rate limit") || message.contains("rate-limit")) {
            return VideoProcessingFailureKind.RATE_LIMITED;
        }
        if (message.contains("500") || message.contains("502") || message.contains("503")) {
            return VideoProcessingFailureKind.UPSTREAM_SERVER_ERROR;
        }
        return VideoProcessingFailureKind.UNKNOWN;
    }

    private static String collectCauseMessages(Throwable failure) {
        StringBuilder message = new StringBuilder();
        Throwable current = failure;
        while (current != null) {
            if (current.getMessage() != null) {
                if (!message.isEmpty()) {
                    message.append(" | ");
                }
                message.append(current.getMessage());
            }
            current = current.getCause();
        }
        return message.toString();
    }
}
```

- [ ] **Step 4: Verify classifier tests pass**

Run:

```bash
./mvnw -q test -DargLine="-javaagent:/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar" -Dtest=YtDlpFailureClassifierTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/app/categorise/domain/service/YtDlpFailureClassifier.java \
  src/test/java/com/app/categorise/domain/service/YtDlpFailureClassifierTest.java
git commit -m "refactor: classify yt-dlp failures at source"
```

---

### Task 3: Make `VideoService` Throw Typed `VideoProcessingException`s

**Files:**
- Modify: `src/main/java/com/app/categorise/domain/service/VideoService.java`
- Modify: `src/test/java/com/app/categorise/domain/service/VideoServiceTest.java`

- [ ] **Step 1: Add failing assertions to existing `VideoServiceTest` cases**

In `src/test/java/com/app/categorise/domain/service/VideoServiceTest.java`, add this import:

```java
import com.app.categorise.exception.VideoProcessingFailureKind;
```

Update the yt-dlp failure test so it uses a supported host, actually reaches `processExecutor`, and asserts the typed kind:

```java
@Test
@DisplayName("Throws typed VideoProcessingException when yt-dlp fails")
void fetchMetadata_throwsOnYtDlpFailure() {
    testProcessExecutor.setException(
        new RuntimeException("yt-dlp: ERROR: Unsupported URL"));

    VideoProcessingException ex = assertThrows(VideoProcessingException.class,
        () -> videoService.fetchMetadata("https://www.youtube.com/watch?v=abc123"));

    assertEquals("Could not process video URL — please check the link and try again.",
        ex.getMessage());
    assertEquals(VideoProcessingFailureKind.UNSUPPORTED_URL, ex.getFailureKind());
    assertFalse(ex.isRetryable());
}
```

Update the malformed JSON test:

```java
assertEquals(VideoProcessingFailureKind.INVALID_METADATA, ex.getFailureKind());
assertTrue(ex.isRetryable());
```

Update the empty stdout test:

```java
assertEquals(VideoProcessingFailureKind.INVALID_METADATA, ex.getFailureKind());
assertTrue(ex.isRetryable());
```

In existing unsafe URL tests, add:

```java
assertEquals(VideoProcessingFailureKind.UNSUPPORTED_URL, ex.getFailureKind());
assertFalse(ex.isRetryable());
```

In the over-limit duration test, add:

```java
assertEquals(VideoProcessingFailureKind.VIDEO_TOO_LONG, ex.getFailureKind());
assertFalse(ex.isRetryable());
```

Add a new download failure test inside `Download Audio`:

```java
@Test
@DisplayName("Wraps yt-dlp audio download failures with typed VideoProcessingException")
void downloadAudio_wrapsYtDlpFailureWithKind() {
    testProcessExecutor.setException(
        new RuntimeException("ERROR: [Instagram] login required"));

    VideoProcessingException ex = assertThrows(VideoProcessingException.class,
        () -> videoService.downloadAudio("https://www.instagram.com/reel/abc123/"));

    assertEquals(VideoProcessingFailureKind.LOGIN_REQUIRED, ex.getFailureKind());
    assertFalse(ex.isRetryable());
    assertNotNull(ex.getCause());
}
```

- [ ] **Step 2: Run focused `VideoServiceTest` and confirm failure**

Run:

```bash
./mvnw -q test -DargLine="-javaagent:/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar" -Dtest=VideoServiceTest
```

Expected: FAIL because `VideoService` still creates untyped `VideoProcessingException`s.

- [ ] **Step 3: Add imports to `VideoService`**

In `src/main/java/com/app/categorise/domain/service/VideoService.java`, add:

```java
import com.app.categorise.exception.VideoProcessingFailureKind;
```

- [ ] **Step 4: Wrap yt-dlp metadata failures with classifier output**

In `fetchMetadata`, replace:

```java
throw new VideoProcessingException(USER_FACING_FETCH_ERROR, e);
```

with:

```java
throw videoProcessingExceptionFromYtDlp(e);
```

Replace empty stdout failure:

```java
throw new VideoProcessingException(USER_FACING_FETCH_ERROR);
```

with:

```java
throw new VideoProcessingException(VideoProcessingFailureKind.INVALID_METADATA, USER_FACING_FETCH_ERROR);
```

Replace malformed JSON failure:

```java
throw new VideoProcessingException(USER_FACING_FETCH_ERROR, e);
```

with:

```java
throw new VideoProcessingException(VideoProcessingFailureKind.INVALID_METADATA, USER_FACING_FETCH_ERROR, e);
```

- [ ] **Step 5: Wrap audio download process failures**

In `downloadAudio`, replace the catch block:

```java
} catch (Exception e) {
    deleteTempDirectory(tempDir);
    throw e;
}
```

with:

```java
} catch (VideoProcessingException e) {
    deleteTempDirectory(tempDir);
    throw e;
} catch (Exception e) {
    deleteTempDirectory(tempDir);
    throw videoProcessingExceptionFromYtDlp(e);
}
```

- [ ] **Step 6: Type validation and pipeline failures**

In `validateDurationLimit`, replace:

```java
throw new VideoProcessingException(
    String.format(USER_FACING_DURATION_LIMIT_ERROR_TEMPLATE, durationMinutes, maxVideoDurationMinutes)
);
```

with:

```java
throw new VideoProcessingException(
    VideoProcessingFailureKind.VIDEO_TOO_LONG,
    String.format(USER_FACING_DURATION_LIMIT_ERROR_TEMPLATE, durationMinutes, maxVideoDurationMinutes)
);
```

In `failWithGenericProcessingError`, replace:

```java
throw new VideoProcessingException(USER_FACING_PROCESSING_ERROR);
```

with:

```java
throw new VideoProcessingException(VideoProcessingFailureKind.INCOMPLETE_DATA, USER_FACING_PROCESSING_ERROR);
```

In `rejectUnsafeVideoUrl`, replace:

```java
throw new VideoProcessingException(USER_FACING_FETCH_ERROR);
```

with:

```java
throw new VideoProcessingException(VideoProcessingFailureKind.UNSUPPORTED_URL, USER_FACING_FETCH_ERROR);
```

In `determineCategory`, replace:

```java
throw new VideoProcessingException(USER_FACING_CATEGORISATION_ERROR);
```

with:

```java
throw new VideoProcessingException(VideoProcessingFailureKind.CATEGORISATION_FAILED, USER_FACING_CATEGORISATION_ERROR);
```

- [ ] **Step 7: Add helper for classified yt-dlp failures**

Add this private helper in `VideoService` near the other private helpers:

```java
private VideoProcessingException videoProcessingExceptionFromYtDlp(Throwable cause) {
    return new VideoProcessingException(
        YtDlpFailureClassifier.classify(cause),
        USER_FACING_FETCH_ERROR,
        cause
    );
}
```

- [ ] **Step 8: Verify `VideoServiceTest` passes**

Run:

```bash
./mvnw -q test -DargLine="-javaagent:/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar" -Dtest=VideoServiceTest
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/app/categorise/domain/service/VideoService.java \
  src/test/java/com/app/categorise/domain/service/VideoServiceTest.java
git commit -m "refactor: emit typed video processing failures"
```

---

### Task 4: Make `TranscriptionJobService` Use Typed Retryability

**Files:**
- Modify: `src/main/java/com/app/categorise/domain/service/TranscriptionJobService.java`
- Modify: `src/test/java/com/app/categorise/domain/service/TranscriptionJobServiceTest.java`

- [ ] **Step 1: Rewrite failure classification tests to use typed exceptions**

In `src/test/java/com/app/categorise/domain/service/TranscriptionJobServiceTest.java`, add imports:

```java
import com.app.categorise.exception.VideoProcessingException;
import com.app.categorise.exception.VideoProcessingFailureKind;

import java.util.concurrent.CompletionException;
```

Replace the raw string-based permanent failure tests with typed tests:

```java
@Test
@DisplayName("marks typed login-required video failures as FAILED immediately")
void typedLoginRequired_permanentFailure() {
    TranscriptionJobEntity job = jobWithStatus(JobStatus.PROCESSING);
    job.setRetryCount(0);

    RuntimeException cause = new RuntimeException("raw yt-dlp output is preserved as cause");
    Exception ex = new VideoProcessingException(
            VideoProcessingFailureKind.LOGIN_REQUIRED,
            "Could not process video URL — please check the link and try again.",
            cause
    );

    service.handleFailure(job, ex);

    assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
    verify(jobRepository).save(job);
}

@Test
@DisplayName("finds typed video failure through CompletionException wrapper")
void wrappedTypedVideoFailure_permanentFailure() {
    TranscriptionJobEntity job = jobWithStatus(JobStatus.PROCESSING);
    job.setRetryCount(0);

    VideoProcessingException root = new VideoProcessingException(
            VideoProcessingFailureKind.UNSUPPORTED_URL,
            "Could not process video URL — please check the link and try again.",
            new RuntimeException("yt-dlp: ERROR: Unsupported URL")
    );
    Exception ex = new RuntimeException(new CompletionException(root));

    service.handleFailure(job, ex);

    assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
}

@Test
@DisplayName("retries typed rate-limited video failures")
void typedRateLimitedFailure_retriesWithBackoff() {
    TranscriptionJobEntity job = jobWithStatus(JobStatus.PROCESSING);
    job.setRetryCount(0);

    Exception ex = new VideoProcessingException(
            VideoProcessingFailureKind.RATE_LIMITED,
            "Could not process video URL — please check the link and try again.",
            new RuntimeException("HTTP Error 429")
    );

    service.handleFailure(job, ex);

    assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
    assertThat(job.getRetryCount()).isEqualTo(1);
    assertThat(job.getNextRetryAt()).isNotNull();
}
```

Keep the existing max-retries, unknown error, timeout, and null-message tests. The unknown error test should continue to default to retry.

- [ ] **Step 2: Run focused `TranscriptionJobServiceTest` and confirm failure**

Run:

```bash
./mvnw -q test -DargLine="-javaagent:/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar" -Dtest=TranscriptionJobServiceTest
```

Expected: FAIL until `TranscriptionJobService` uses `VideoProcessingException#getFailureKind()` / `isRetryable()`.

- [ ] **Step 3: Update imports in `TranscriptionJobService`**

Add:

```java
import com.app.categorise.exception.VideoProcessingException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
```

- [ ] **Step 4: Replace message-scraping retry classification**

Replace the current `isTransientFailure` method and `collectCauseMessages` helper with:

```java
private boolean isTransientFailure(Exception ex) {
    VideoProcessingException videoFailure = findCause(ex, VideoProcessingException.class);
    if (videoFailure != null) {
        return videoFailure.isRetryable();
    }

    if (findCause(ex, SocketTimeoutException.class) != null) {
        return true;
    }
    if (findCause(ex, ConnectException.class) != null) {
        return true;
    }

    return true;
}

private <T extends Throwable> T findCause(Throwable ex, Class<T> type) {
    Throwable current = ex;
    while (current != null) {
        if (type.isInstance(current)) {
            return type.cast(current);
        }
        current = current.getCause();
    }
    return null;
}
```

This intentionally removes video-specific string matching from job retry logic. The job service still defaults unknown failures to retry, preserving existing behavior for generic app errors.

- [ ] **Step 5: Verify `TranscriptionJobServiceTest` passes**

Run:

```bash
./mvnw -q test -DargLine="-javaagent:/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar" -Dtest=TranscriptionJobServiceTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/app/categorise/domain/service/TranscriptionJobService.java \
  src/test/java/com/app/categorise/domain/service/TranscriptionJobServiceTest.java
git commit -m "refactor: classify job retries from typed video failures"
```

---

### Task 5: Type The Poller Rate-Limit Failure

**Files:**
- Modify: `src/main/java/com/app/categorise/domain/service/JobPollerService.java`

- [ ] **Step 1: Add imports**

In `src/main/java/com/app/categorise/domain/service/JobPollerService.java`, add:

```java
import com.app.categorise.exception.VideoProcessingException;
import com.app.categorise.exception.VideoProcessingFailureKind;
```

- [ ] **Step 2: Replace raw rate-limit runtime exception**

Replace:

```java
jobService.handleFailure(job,
        new RuntimeException("Rate limit exceeded at processing time: " + rateLimitResult.getReason()));
```

with:

```java
jobService.handleFailure(job,
        new VideoProcessingException(
                VideoProcessingFailureKind.RATE_LIMITED,
                "Rate limit exceeded at processing time: " + rateLimitResult.getReason()
        ));
```

This keeps the retry semantics explicit instead of relying on `TranscriptionJobService` to search for the words `rate limit`.

- [ ] **Step 3: Run focused job tests**

Run:

```bash
./mvnw -q test -DargLine="-javaagent:/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar" -Dtest=TranscriptionJobServiceTest
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/app/categorise/domain/service/JobPollerService.java
git commit -m "refactor: type poller rate limit failures"
```

---

### Task 6: Full Verification

**Files:**
- Verify all changed files.

- [ ] **Step 1: Run the focused set**

Run:

```bash
./mvnw -q test -DargLine="-javaagent:/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar" -Dtest=VideoProcessingExceptionTest,YtDlpFailureClassifierTest,VideoServiceTest,TranscriptionJobServiceTest
```

Expected: PASS.

- [ ] **Step 2: Run the full suite**

Run with Docker/Testcontainers access:

```bash
./mvnw -q test -DargLine="-javaagent:/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar"
```

Expected: PASS.

- [ ] **Step 3: Check Surefire reports if the full run looks noisy**

Run:

```bash
ls -lt target/surefire-reports | head
```

Then:

```bash
grep -R "Tests run:" target/surefire-reports/*.txt
```

Expected: all current report summaries show failures/errors/skipped as zero. Treat old `*.dumpstream` files as stale unless their timestamp matches the current run.

- [ ] **Step 4: Inspect final diff**

Run:

```bash
git diff --stat HEAD
```

Expected: only the files listed in this plan changed since the task branch started.

- [ ] **Step 5: Commit any final test-only adjustments**

```bash
git add src/main/java/com/app/categorise src/test/java/com/app/categorise
git commit -m "test: verify typed video failure retry behavior"
```

Skip this commit if there are no uncommitted changes after Task 5.

---

## Acceptance Criteria

- `VideoProcessingException` carries a `VideoProcessingFailureKind`.
- Every `VideoProcessingFailureKind` has an explicit retryability value.
- `VideoService` assigns typed failure kinds for URL validation, yt-dlp metadata failures, yt-dlp audio download failures, malformed/empty metadata, over-limit videos, incomplete data, and categorisation failures.
- Raw yt-dlp output remains in the wrapped cause, not in the public exception message.
- `TranscriptionJobService` no longer checks video-specific permanent failure strings.
- `TranscriptionJobService` finds typed `VideoProcessingException` instances through wrapper exceptions such as `CompletionException`.
- Rate-limit failures from `JobPollerService` are typed as retryable.
- Focused tests and the full Maven test suite pass with the Mockito Java agent.

## Self-Review

- Spec coverage: The plan moves video failure classification to the video/yt-dlp boundary, keeps wrapped errors, preserves user-facing messages, and makes job retry classification typed.
- Placeholder scan: No implementation step relies on deferred or vague instructions.
- Type consistency: The enum is named `VideoProcessingFailureKind`; the exception methods are `getFailureKind()` and `isRetryable()`; all later tasks use those names.
