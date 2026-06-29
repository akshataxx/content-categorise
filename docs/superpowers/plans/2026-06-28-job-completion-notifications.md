# Job Completion Notifications (Finish & Fix) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish and fix the ~90%-implemented job completion/failure push notification feature so a completing transcription job produces a **visible** "Transcript ready" banner that, when tapped, deep-links to the correct transcript — while keeping the existing failure path and foreground refresh working.

**Architecture:** Do NOT redesign. The FCM → APNs mechanism is already wired end-to-end. Implement only the five scoped work items: (W1) fix the iOS notification type-string mismatch behind a single Swift `NotificationType` source-of-truth enum and remove the dead `TRANSCRIPT_READY` case; (W2) add a visible `notification` block to the backend completion push (single push carrying data) and route the foreground `willPresent` payload through the silent handler so Feed/Activity still refresh; (W3) render the production config-verification runbook as a checklist; (W4) change `DeviceController.unregister` from hard-delete to soft-deactivate; (W5) document the reliability model. This feature has **no migration** and sits at the **top** of the stack.

**Tech Stack:**
- **Backend** (`content-categorise`): Java 24, Spring Boot 3.5, Maven, PostgreSQL + Flyway, Firebase Admin SDK; tests JUnit 5 + Mockito + AssertJ + Testcontainers.
- **iOS** (`TranscribeAssistant-ios` / "Scoop"): SwiftUI, target iOS 18.x, Firebase Messaging + APNs; tests use the `swift-testing` framework (`import Testing`) under `ScoopTests`.

---

## Current Context

### Backend (`/Users/apushpavannan/personal/content-categorise`)

- `application/internal/NotificationServiceImpl.java`
  - `notifyJobCompleted(userId, jobId, transcriptId, title)` (L30–38) checks `FirebaseApp.getApps()` then calls `sendSilentNotification(userId, jobId, transcriptId)` — **the `title` argument is dropped**.
  - `sendSilentNotification(...)` (L50–77) builds a **data-only** `Message` (`type=TRANSCRIPT_COMPLETE`, `jobId`, `transcriptId`, `silent=true`, `content-available=true`, **no** `notification` block).
  - `sendFailedNotification(...)` (L80–104) builds a **visible** `Message` (`Notification` title/body, `type=TRANSCRIPT_FAILED`, sound `default`).
  - `sendToDevice(...)` (L106–119) sends async and routes errors to `handleSendError(...)` (L121–133) which **soft-deactivates** (`device.setActive(false); deviceRepository.save(device)`) on `UNREGISTERED` / `INVALID_ARGUMENT`.
- `domain/service/TranscriptionJobService.java`
  - `markCompleted(...)` (L106–123) resolves the title from `BaseTranscriptRepository` (fallback `"your video"`) and calls `notificationService.notifyJobCompleted(...)` in try/catch.
  - `handleFailure(...)` (L139–162): transient + retries remaining → re-queue `PENDING`, **no notify**; else (permanent) → `FAILED` + `notifyJobFailed(...)` in try/catch.
  - `isTransientFailure(...)` (L164–183) classifies via `collectCauseMessages(...)`.
- `api/controller/DeviceController.java`
  - `register(...)` (L33–72): token-refresh branch (L44–50) and token-rotation branch (L53–60) **do NOT set `active=true`**; only the new-registration branch (L63–71) sets `active=true`.
  - `unregister(...)` (L75–103): **hard-deletes** via `deviceRepository.delete(device)` (L91, L98).
- `config/NotificationConfig.java` — real impl `@ConditionalOnBean(FirebaseApp.class)`, else `NoOpNotificationService`.
- `config/FirebaseConfig.java` — `@ConditionalOnProperty(firebase.enabled=true)`, reads `firebase.service-account-path`.

### iOS (`/Users/apushpavannan/personal/TranscribeAssistant-ios`)

- `Scoop/service/NotificationManager.swift`
  - `DeepLink` enum (L40–43): `case transcript(id: UUID)`, `case activityTab`.
  - `handleNotificationTap(userInfo:)` (L177–205): switches on `type` — has a dead `case "TRANSCRIPT_READY"` (backend never emits it), `case "TRANSCRIPT_FAILED", "BATCH_COMPLETE"` → `.activityTab`, `default` → `.activityTab`. **There is no `TRANSCRIPT_COMPLETE` case, so a tapped completion lands on the Activity tab instead of the transcript (the W1 bug).**
  - `handleSilentNotification(_:)` (L219–247): switches on `type`, `case "TRANSCRIPT_COMPLETE"` increments counters and posts `.silentPushReceived`.
- `Scoop/AppDelegate.swift`
  - `willPresent` (L94–107): returns `[[.banner, .sound, .badge]]` but does **not** route `userInfo` through the silent handler.
  - `didReceive` (L110–130): posts `.notificationTapped` with `userInfo`.
  - `didReceiveRemoteNotification` (L51–64): forwards silent pushes to `handleSilentNotification`.
- `Scoop/service/TranscriptSyncCoordinator.swift` — foreground polling fallback (unchanged by this plan).
- `Scoop/Scoop.entitlements` — `aps-environment` currently `development` (W3 release item).
- `ScoopTests/NotificationHandlingTests.swift` — existing swift-testing suite nested in `extension ScoopTestSuiteContainer`; uses `@Suite(.serialized)`, `@Test @MainActor`, `NotificationManager.shared`, and `#expect(...)`. **Extend this file; do NOT create a new notification test file.**

## Files

**Backend:**
- Modify: `src/main/java/com/app/categorise/application/internal/NotificationServiceImpl.java`
- Modify: `src/main/java/com/app/categorise/api/controller/DeviceController.java`
- Modify: `src/test/java/com/app/categorise/application/internal/NotificationServiceImplTest.java`
- Modify: `src/test/java/com/app/categorise/domain/service/TranscriptionJobServiceTest.java`
- (No change required to `TranscriptionJobService.java` — wiring already correct; verified in Task 4.)

**iOS:**
- Modify: `Scoop/service/NotificationManager.swift`
- Modify: `Scoop/AppDelegate.swift`
- Modify: `ScoopTests/NotificationHandlingTests.swift`
- Modify (W3 checklist only): `Scoop/Scoop.entitlements` (release builds)

**Docs:**
- Create: `docs/superpowers/runbooks/2026-06-28-job-completion-notifications-config.md` (W3 + W5)

## Test Commands

**Backend** — uses the repo Mockito Java agent.

Focused:

```bash
cd /Users/apushpavannan/personal/content-categorise
./mvnw -q test -DargLine="-javaagent:/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar" -Dtest=ClassNameTest
```

Full suite:

```bash
cd /Users/apushpavannan/personal/content-categorise
./mvnw -q test -DargLine="-javaagent:/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar"
```

> If Testcontainers-backed tests run in a sandbox, run with Docker access/escalation. A sandbox Docker socket failure does not mean Docker is broken.

**iOS** — uses `xcodebuild test` against the `Scoop` scheme on an iOS 18 simulator.

Focused (only the notification suite):

```bash
cd /Users/apushpavannan/personal/TranscribeAssistant-ios
xcodebuild test \
  -project Scoop.xcodeproj \
  -scheme Scoop \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' \
  -only-testing:ScoopTests/NotificationHandlingTests
```

Full ScoopTests target:

```bash
cd /Users/apushpavannan/personal/TranscribeAssistant-ios
xcodebuild test \
  -project Scoop.xcodeproj \
  -scheme Scoop \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' \
  -only-testing:ScoopTests
```

> If `iPhone 16 Pro` is unavailable, pick any booted iOS 18 simulator from `xcrun simctl list devices available`.

---

### Task 1: W1 — Fix iOS notification type-string mismatch (single source-of-truth enum)

**Why:** A tapped `TRANSCRIPT_COMPLETE` notification currently falls through to `.activityTab` instead of deep-linking to the transcript, because `handleNotificationTap` only knows the dead `TRANSCRIPT_READY` string. Introduce one `NotificationType` enum used by BOTH `handleNotificationTap` and `handleSilentNotification`, add the `transcriptComplete` tap case, and remove the dead `TRANSCRIPT_READY` / `BATCH_COMPLETE` cases (verified: no producer emits them).

**Files:**
- Modify: `Scoop/service/NotificationManager.swift`
- Modify: `ScoopTests/NotificationHandlingTests.swift`

- [ ] **Step 1: Write the failing tap-routing tests**

Append these tests inside the existing `NotificationHandlingTests` struct in `ScoopTests/NotificationHandlingTests.swift` (before the final closing brace of the struct). They drive the new `handleNotificationTap` behaviour via the existing `.notificationTapped` observer wired in `observeNotificationTaps()`:

```swift
    // MARK: - Notification Tap Deep Linking (W1)

    @Test @MainActor func notificationTap_transcriptComplete_deepLinksToTranscript() async throws {
        let manager = NotificationManager.shared
        manager.clearPendingDeepLink()

        let transcriptId = UUID()
        NotificationCenter.default.post(
            name: .notificationTapped,
            object: nil,
            userInfo: [
                "type": "TRANSCRIPT_COMPLETE",
                "jobId": UUID().uuidString,
                "transcriptId": transcriptId.uuidString
            ]
        )

        // The tap is delivered via a Combine publisher on the main queue.
        try await Task.sleep(nanoseconds: 100_000_000)

        #expect(manager.pendingDeepLink == .transcript(id: transcriptId))
        manager.clearPendingDeepLink()
    }

    @Test @MainActor func notificationTap_transcriptCompleteMissingId_fallsBackToActivityTab() async throws {
        let manager = NotificationManager.shared
        manager.clearPendingDeepLink()

        NotificationCenter.default.post(
            name: .notificationTapped,
            object: nil,
            userInfo: [
                "type": "TRANSCRIPT_COMPLETE",
                "jobId": UUID().uuidString
            ]
        )

        try await Task.sleep(nanoseconds: 100_000_000)

        #expect(manager.pendingDeepLink == .activityTab)
        manager.clearPendingDeepLink()
    }

    @Test @MainActor func notificationTap_transcriptFailed_routesToActivityTab() async throws {
        let manager = NotificationManager.shared
        manager.clearPendingDeepLink()

        NotificationCenter.default.post(
            name: .notificationTapped,
            object: nil,
            userInfo: [
                "type": "TRANSCRIPT_FAILED",
                "jobId": UUID().uuidString
            ]
        )

        try await Task.sleep(nanoseconds: 100_000_000)

        #expect(manager.pendingDeepLink == .activityTab)
        manager.clearPendingDeepLink()
    }

    @Test @MainActor func notificationTap_unknownType_routesToActivityTab() async throws {
        let manager = NotificationManager.shared
        manager.clearPendingDeepLink()

        NotificationCenter.default.post(
            name: .notificationTapped,
            object: nil,
            userInfo: [
                "type": "SOME_UNKNOWN_TYPE",
                "jobId": UUID().uuidString
            ]
        )

        try await Task.sleep(nanoseconds: 100_000_000)

        #expect(manager.pendingDeepLink == .activityTab)
        manager.clearPendingDeepLink()
    }

    @Test @MainActor func notificationTap_missingType_routesToActivityTab() async throws {
        let manager = NotificationManager.shared
        manager.clearPendingDeepLink()

        NotificationCenter.default.post(
            name: .notificationTapped,
            object: nil,
            userInfo: ["jobId": UUID().uuidString]
        )

        try await Task.sleep(nanoseconds: 100_000_000)

        #expect(manager.pendingDeepLink == .activityTab)
        manager.clearPendingDeepLink()
    }
```

- [ ] **Step 2: Run the failing tests**

```bash
cd /Users/apushpavannan/personal/TranscribeAssistant-ios
xcodebuild test \
  -project Scoop.xcodeproj \
  -scheme Scoop \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' \
  -only-testing:ScoopTests/NotificationHandlingTests
```

Expected: `notificationTap_transcriptComplete_deepLinksToTranscript` FAILS (current code routes `TRANSCRIPT_COMPLETE` to `.activityTab`). The other tap tests may pass already; that is fine.

- [ ] **Step 3: Add the `NotificationType` source-of-truth enum**

In `Scoop/service/NotificationManager.swift`, inside the `// MARK: - Types` section (just after the `DeepLink` enum, around L43), add:

```swift
    /// Canonical notification `type` strings shared by the backend FCM payloads.
    /// Single source of truth used by both `handleNotificationTap` and `handleSilentNotification`.
    enum NotificationType: String {
        case transcriptComplete = "TRANSCRIPT_COMPLETE"
        case transcriptFailed = "TRANSCRIPT_FAILED"
    }
```

- [ ] **Step 4: Rewrite `handleNotificationTap` to use the enum and add the completion case**

Replace the entire `handleNotificationTap(userInfo:)` method (L177–205) with:

```swift
    /// Extract deep link data from notification payload
    private func handleNotificationTap(userInfo: [AnyHashable: Any]) {
        print("📝 NotificationManager: Handling notification tap with data: \(userInfo)")

        guard let typeString = userInfo["type"] as? String,
              let type = NotificationType(rawValue: typeString) else {
            print("⚠️ NotificationManager: Unknown/missing type in notification, defaulting to Activity tab")
            pendingDeepLink = .activityTab
            return
        }

        switch type {
        case .transcriptComplete:
            if let idString = userInfo["transcriptId"] as? String,
               let id = UUID(uuidString: idString) {
                print("📝 NotificationManager: Deep link to transcript: \(id)")
                pendingDeepLink = .transcript(id: id)
            } else {
                print("⚠️ NotificationManager: Invalid transcriptId, fallback to Activity tab")
                pendingDeepLink = .activityTab
            }

        case .transcriptFailed:
            print("📝 NotificationManager: Deep link to Activity tab")
            pendingDeepLink = .activityTab
        }
    }
```

> Note: the `default` branch is gone because `NotificationType` is exhaustive; any unrecognized string is already handled by the `guard` returning `.activityTab`. This removes the dead `TRANSCRIPT_READY` and `BATCH_COMPLETE` cases.

- [ ] **Step 5: Use the enum in `handleSilentNotification`**

In `handleSilentNotification(_:)` (L219–247), change the string switch to use the enum so the two handlers cannot drift. Replace:

```swift
        guard let type = userInfo["type"] as? String else {
            print("⚠️ NotificationManager: Silent push missing 'type' field")
            return
        }

        print("📝 NotificationManager: Received silent push - type: \(type)")

        switch type {
        case "TRANSCRIPT_COMPLETE":
```

with:

```swift
        guard let typeString = userInfo["type"] as? String else {
            print("⚠️ NotificationManager: Silent push missing 'type' field")
            return
        }

        print("📝 NotificationManager: Received silent push - type: \(typeString)")

        switch NotificationType(rawValue: typeString) {
        case .transcriptComplete:
```

And change the trailing `default:` case body to remain unchanged but match the optional/enum switch:

```swift
        case .transcriptFailed, .none:
            print("📝 NotificationManager: Unhandled silent push type '\(typeString)'")
        }
```

> Rationale: `NotificationType(rawValue:)` returns an optional, so the switch covers `.transcriptComplete`, `.transcriptFailed`, and `.none` (unknown strings) — preserving the existing "ignore unknown/failed in silent path" behaviour.

- [ ] **Step 6: Verify the tap + silent tests pass**

```bash
cd /Users/apushpavannan/personal/TranscribeAssistant-ios
xcodebuild test \
  -project Scoop.xcodeproj \
  -scheme Scoop \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' \
  -only-testing:ScoopTests/NotificationHandlingTests
```

Expected: ALL `NotificationHandlingTests` pass (new tap tests + existing silent tests, which still use the `TRANSCRIPT_COMPLETE` string and remain green).

- [ ] **Step 7: Commit**

```bash
cd /Users/apushpavannan/personal/TranscribeAssistant-ios
git add Scoop/service/NotificationManager.swift ScoopTests/NotificationHandlingTests.swift
git commit -m "fix(ios): deep-link tapped TRANSCRIPT_COMPLETE to transcript via NotificationType enum"
```

---

### Task 2: W2 (backend) — Add a visible completion notification + extract type constants

**Why:** Completion is currently silent-only (no `notification` block) and the `title` passed to `notifyJobCompleted` is dropped. Add a single visible push that carries BOTH a `notification` block (title "Transcript ready", body `"<title>" is ready to view`) AND the existing data (`type=TRANSCRIPT_COMPLETE`, `jobId`, `transcriptId`) with APNs `sound=default`. Pass `title` through. Also extract the `TRANSCRIPT_COMPLETE` / `TRANSCRIPT_FAILED` strings into named constants to document the cross-platform contract (W1 backend symmetry).

**Files:**
- Modify: `src/main/java/com/app/categorise/application/internal/NotificationServiceImpl.java`
- Modify: `src/test/java/com/app/categorise/application/internal/NotificationServiceImplTest.java`

- [ ] **Step 1: Write the failing tests (captured `Message` has a notification block + data)**

Replace the body of the `NotifyJobCompletedTests` nested class in `src/test/java/com/app/categorise/application/internal/NotificationServiceImplTest.java` with the version below. It uses `MockedStatic<FirebaseMessaging>` + `ArgumentCaptor<Message>` and reads the captured payload via Firebase's package-private accessors using reflection helper `messageData(...)` / `messageNotification(...)` defined at the bottom of the test class.

Add these imports at the top of the test file (next to the existing imports):

```java
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.junit.jupiter.api.Nested;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
```

Replace the `NotifyJobCompletedTests` class with:

```java
    @Nested
    @DisplayName("notifyJobCompleted")
    class NotifyJobCompletedTests {

        @Test
        @DisplayName("sends a VISIBLE notification with title/body and TRANSCRIPT_COMPLETE data")
        void sendsVisibleNotificationWithData() throws Exception {
            when(deviceRepository.findByUserIdAndActiveTrue(userId))
                    .thenReturn(List.of(iosDevice));

            try (MockedStatic<FirebaseApp> firebaseAppMock = mockStatic(FirebaseApp.class);
                 MockedStatic<FirebaseMessaging> messagingMock = mockStatic(FirebaseMessaging.class)) {

                firebaseAppMock.when(FirebaseApp::getApps).thenReturn(List.of(mock(FirebaseApp.class)));

                FirebaseMessaging messaging = mock(FirebaseMessaging.class);
                messagingMock.when(FirebaseMessaging::getInstance).thenReturn(messaging);
                ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
                when(messaging.sendAsync(messageCaptor.capture()))
                        .thenReturn(CompletableFuture.completedFuture("projects/p/messages/1"));

                notificationService.notifyJobCompleted(userId, jobId, transcriptId, "My Great Video");

                Message sent = messageCaptor.getValue();

                // Visible: notification block present with the resolved title in the body
                Map<String, String> notification = messageNotification(sent);
                assertThat(notification).isNotNull();
                assertThat(notification.get("title")).isEqualTo("Transcript ready");
                assertThat(notification.get("body")).isEqualTo("\"My Great Video\" is ready to view");

                // Actionable: data payload still carries the contract fields
                Map<String, String> data = messageData(sent);
                assertThat(data).containsEntry("type", "TRANSCRIPT_COMPLETE");
                assertThat(data).containsEntry("jobId", jobId.toString());
                assertThat(data).containsEntry("transcriptId", transcriptId.toString());
            }
        }

        @Test
        @DisplayName("uses 'your video' body fallback when the title is blank")
        void usesFallbackTitleWhenBlank() throws Exception {
            when(deviceRepository.findByUserIdAndActiveTrue(userId))
                    .thenReturn(List.of(iosDevice));

            try (MockedStatic<FirebaseApp> firebaseAppMock = mockStatic(FirebaseApp.class);
                 MockedStatic<FirebaseMessaging> messagingMock = mockStatic(FirebaseMessaging.class)) {

                firebaseAppMock.when(FirebaseApp::getApps).thenReturn(List.of(mock(FirebaseApp.class)));
                FirebaseMessaging messaging = mock(FirebaseMessaging.class);
                messagingMock.when(FirebaseMessaging::getInstance).thenReturn(messaging);
                ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
                when(messaging.sendAsync(messageCaptor.capture()))
                        .thenReturn(CompletableFuture.completedFuture("ok"));

                notificationService.notifyJobCompleted(userId, jobId, transcriptId, "   ");

                Map<String, String> notification = messageNotification(messageCaptor.getValue());
                assertThat(notification.get("body")).isEqualTo("\"your video\" is ready to view");
            }
        }

        @Test
        @DisplayName("sends a SINGLE push per device (not a dual silent+visible pair)")
        void sendsSinglePushPerDevice() {
            when(deviceRepository.findByUserIdAndActiveTrue(userId))
                    .thenReturn(List.of(iosDevice));

            try (MockedStatic<FirebaseApp> firebaseAppMock = mockStatic(FirebaseApp.class);
                 MockedStatic<FirebaseMessaging> messagingMock = mockStatic(FirebaseMessaging.class)) {

                firebaseAppMock.when(FirebaseApp::getApps).thenReturn(List.of(mock(FirebaseApp.class)));
                FirebaseMessaging messaging = mock(FirebaseMessaging.class);
                messagingMock.when(FirebaseMessaging::getInstance).thenReturn(messaging);
                when(messaging.sendAsync(any(Message.class)))
                        .thenReturn(CompletableFuture.completedFuture("ok"));

                notificationService.notifyJobCompleted(userId, jobId, transcriptId, "Title");

                verify(messaging, times(1)).sendAsync(any(Message.class));
            }
        }

        @Test
        @DisplayName("does not send when no active devices exist")
        void doesNotSendWithoutActiveDevices() {
            when(deviceRepository.findByUserIdAndActiveTrue(userId)).thenReturn(List.of());

            try (MockedStatic<FirebaseApp> firebaseAppMock = mockStatic(FirebaseApp.class);
                 MockedStatic<FirebaseMessaging> messagingMock = mockStatic(FirebaseMessaging.class)) {

                firebaseAppMock.when(FirebaseApp::getApps).thenReturn(List.of(mock(FirebaseApp.class)));
                FirebaseMessaging messaging = mock(FirebaseMessaging.class);
                messagingMock.when(FirebaseMessaging::getInstance).thenReturn(messaging);

                notificationService.notifyJobCompleted(userId, jobId, transcriptId, "Title");

                verify(deviceRepository).findByUserIdAndActiveTrue(userId);
                verify(messaging, never()).sendAsync(any(Message.class));
            }
        }

        @Test
        @DisplayName("skips entirely when Firebase is not initialized")
        void skipsWhenFirebaseNotInitialized() {
            try (MockedStatic<FirebaseApp> firebaseAppMock = mockStatic(FirebaseApp.class)) {
                firebaseAppMock.when(FirebaseApp::getApps).thenReturn(List.of());

                notificationService.notifyJobCompleted(userId, jobId, transcriptId, "Title");

                verify(deviceRepository, never()).findByUserIdAndActiveTrue(any());
            }
        }
    }
```

Add these reflection helpers as `private static` methods at the bottom of the test class (before the final closing brace). Firebase's `Message` keeps `data`/`notification` package-private, so we read them reflectively:

```java
    @SuppressWarnings("unchecked")
    private static Map<String, String> messageData(Message message) throws Exception {
        Field f = Message.class.getDeclaredField("data");
        f.setAccessible(true);
        return (Map<String, String>) f.get(message);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> messageNotification(Message message) throws Exception {
        Field nf = Message.class.getDeclaredField("notification");
        nf.setAccessible(true);
        Object notification = nf.get(message);
        if (notification == null) return null;
        Field titleField = notification.getClass().getDeclaredField("title");
        Field bodyField = notification.getClass().getDeclaredField("body");
        titleField.setAccessible(true);
        bodyField.setAccessible(true);
        return Map.of(
                "title", String.valueOf(titleField.get(notification)),
                "body", String.valueOf(bodyField.get(notification))
        );
    }
```

> If a future Firebase Admin SDK version renames these private fields, the helpers are the only thing to update; the assertions describe the externally-observable contract.

- [ ] **Step 2: Run the failing tests**

```bash
cd /Users/apushpavannan/personal/content-categorise
./mvnw -q test -DargLine="-javaagent:/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar" -Dtest=NotificationServiceImplTest
```

Expected: `sendsVisibleNotificationWithData`, `usesFallbackTitleWhenBlank`, and `sendsSinglePushPerDevice` FAIL because completion is still silent (no notification block) and `title` is dropped.

- [ ] **Step 3: Add type constants and thread `title` through to a visible completion push**

Edit `src/main/java/com/app/categorise/application/internal/NotificationServiceImpl.java`.

Add named constants near the top of the class (after `private final DeviceRepository deviceRepository;`):

```java
    /** Canonical notification type strings — must match the iOS NotificationType enum. */
    static final String TYPE_TRANSCRIPT_COMPLETE = "TRANSCRIPT_COMPLETE";
    static final String TYPE_TRANSCRIPT_FAILED = "TRANSCRIPT_FAILED";
    private static final String DEFAULT_TITLE = "your video";
```

Change `notifyJobCompleted` to pass `title` through and call a renamed visible sender:

```java
    @Override
    public void notifyJobCompleted(UUID userId, UUID jobId, UUID transcriptId, String title) {
        if (FirebaseApp.getApps().isEmpty()) {
            log.debug("Firebase not initialized, skipping notification for job {}", jobId);
            return;
        }
        // Single VISIBLE push carrying data so the user is informed AND the UI can deep-link/refresh.
        sendCompletionNotification(userId, jobId, transcriptId, title);
    }
```

Replace the entire `sendSilentNotification(...)` method (L50–77) with the visible `sendCompletionNotification(...)`:

```java
    private void sendCompletionNotification(UUID userId, UUID jobId, UUID transcriptId, String title) {
        List<DeviceEntity> devices = deviceRepository.findByUserIdAndActiveTrue(userId);
        if (devices.isEmpty()) {
            log.debug("No active devices for user {}, skipping notification", userId);
            return;
        }

        String safeTitle = (title != null && !title.isBlank()) ? title : DEFAULT_TITLE;
        String body = "\"" + safeTitle + "\" is ready to view";

        // SINGLE visible push: notification block (user-facing) + data (deep-link / UI refresh).
        for (DeviceEntity device : devices) {
            Message message = Message.builder()
                    .setToken(device.getFcmToken())
                    .setNotification(Notification.builder()
                            .setTitle("Transcript ready")
                            .setBody(body)
                            .build())
                    .putData("type", TYPE_TRANSCRIPT_COMPLETE)
                    .putData("jobId", jobId.toString())
                    .putData("transcriptId", transcriptId.toString())
                    .setApnsConfig(ApnsConfig.builder()
                            .setAps(Aps.builder().setSound("default").build())
                            .build())
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .build())
                    .build();
            sendToDevice(device, message);
        }
    }
```

Also update `sendFailedNotification` to use the constant (replace the literal `.putData("type", "TRANSCRIPT_FAILED")` with `.putData("type", TYPE_TRANSCRIPT_FAILED)`).

> The `markCompleted` caller in `TranscriptionJobService` already resolves the title (fallback `"your video"`) and passes it; the `DEFAULT_TITLE` fallback here is defensive for the blank/null case.

- [ ] **Step 4: Verify the completion tests pass**

```bash
cd /Users/apushpavannan/personal/content-categorise
./mvnw -q test -DargLine="-javaagent:/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar" -Dtest=NotificationServiceImplTest
```

Expected: PASS (all `NotifyJobCompletedTests` + the existing `NotifyJobFailedTests`).

- [ ] **Step 5: Commit**

```bash
cd /Users/apushpavannan/personal/content-categorise
git add src/main/java/com/app/categorise/application/internal/NotificationServiceImpl.java \
  src/test/java/com/app/categorise/application/internal/NotificationServiceImplTest.java
git commit -m "feat(notifications): send visible 'Transcript ready' completion push with data payload"
```

---

### Task 3: W2 (iOS) — Route foreground `willPresent` payload through the silent handler

**Why:** With W2, the completion push now has a `notification` block, so when the app is in the **foreground** iOS calls `willPresent` (which shows the banner) but does NOT call `didReceiveRemoteNotification`. Today the Feed/Activity UI only refreshes via `handleSilentNotification` (badge counters + `.silentPushReceived` broadcast). So we must ALSO route the `willPresent` `userInfo` through `handleSilentNotification` so in-app UI updates while the banner shows.

**Files:**
- Modify: `Scoop/AppDelegate.swift`
- Modify: `ScoopTests/NotificationHandlingTests.swift`

- [ ] **Step 1: Write the failing foreground-refresh test**

This is a unit-level regression for the behaviour `willPresent` must trigger: routing a completion payload through `handleSilentNotification` increments the completion count and broadcasts `.silentPushReceived`. (The `UNUserNotificationCenterDelegate` callback itself is exercised manually in the E2E task; here we lock in the handler contract the delegate will call.)

Append inside the `NotificationHandlingTests` struct in `ScoopTests/NotificationHandlingTests.swift`:

```swift
    // MARK: - Foreground Presentation Refresh (W2)

    @Test @MainActor func foregroundCompletion_routedThroughSilentHandler_refreshesUI() async throws {
        let manager = NotificationManager.shared
        manager.markActivityViewed()
        #expect(manager.newCompletionCount == 0)

        var broadcastReceived = false
        let observer = NotificationCenter.default.addObserver(
            forName: .silentPushReceived,
            object: nil,
            queue: .main
        ) { _ in broadcastReceived = true }

        // Simulates exactly what AppDelegate.willPresent now forwards.
        let userInfo: [AnyHashable: Any] = [
            "type": "TRANSCRIPT_COMPLETE",
            "jobId": UUID().uuidString,
            "transcriptId": UUID().uuidString
        ]
        manager.handleSilentNotification(userInfo)

        try await Task.sleep(nanoseconds: 50_000_000)

        #expect(manager.newCompletionCount == 1)
        #expect(broadcastReceived == true)

        NotificationCenter.default.removeObserver(observer)
        manager.markActivityViewed()
    }
```

- [ ] **Step 2: Run the test (should already pass at the handler level)**

```bash
cd /Users/apushpavannan/personal/TranscribeAssistant-ios
xcodebuild test \
  -project Scoop.xcodeproj \
  -scheme Scoop \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' \
  -only-testing:ScoopTests/NotificationHandlingTests
```

Expected: PASS — it documents the contract `willPresent` must satisfy. The actual wiring in Step 3 is verified by this contract test plus manual E2E (Task 8).

- [ ] **Step 3: Forward the foreground payload through the silent handler in `willPresent`**

In `Scoop/AppDelegate.swift`, update `willPresent` (L94–107) to route the payload through `NotificationManager.shared.handleSilentNotification` before presenting. Replace the method body with:

```swift
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        let userInfo = notification.request.content.userInfo

        #if DEBUG
        print("[AppDelegate] Notification received in foreground: \(userInfo)")
        #endif

        // Refresh in-app UI (Feed/Activity badges + broadcast) while the banner shows.
        // Foreground pushes do NOT trigger didReceiveRemoteNotification, so do it here.
        Task { @MainActor in
            NotificationManager.shared.handleSilentNotification(userInfo)
        }

        // Show notification even when app is in foreground
        completionHandler([[.banner, .sound, .badge]])
    }
```

- [ ] **Step 4: Verify tests still pass**

```bash
cd /Users/apushpavannan/personal/TranscribeAssistant-ios
xcodebuild test \
  -project Scoop.xcodeproj \
  -scheme Scoop \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' \
  -only-testing:ScoopTests/NotificationHandlingTests
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
cd /Users/apushpavannan/personal/TranscribeAssistant-ios
git add Scoop/AppDelegate.swift ScoopTests/NotificationHandlingTests.swift
git commit -m "feat(ios): refresh in-app UI on foreground completion via silent handler in willPresent"
```

---

### Task 4: Lock in `TranscriptionJobService` notify wiring with tests (no source change)

**Why:** The job→notify hooks are already correct (`markCompleted` notifies on completion; `handleFailure` notifies ONLY on permanent failure, never on transient retries). Add regression tests so W2's behaviour change cannot silently break this contract. No production code change is expected in this task — if a test fails, the wiring regressed and must be restored, not redesigned.

**Files:**
- Modify: `src/test/java/com/app/categorise/domain/service/TranscriptionJobServiceTest.java`

- [ ] **Step 1: Add the notify-wiring tests**

The test class already mocks `NotificationService` (`@Mock private NotificationService notificationService;`) and builds `service` in `setUp()`. Per backend AGENTS.md, group by method-under-test with ONE nesting layer.

Inside the existing `HandleFailure` nested class, append:

```java
        @Test
        @DisplayName("transient failure with retries remaining re-queues PENDING and never notifies")
        void transientFailure_requeues_andDoesNotNotify() {
            TranscriptionJobEntity job = jobWithStatus(JobStatus.PROCESSING);
            job.setRetryCount(0);

            Exception ex = new RuntimeException("Connection timeout while downloading");

            service.handleFailure(job, ex);

            assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
            verify(notificationService, never()).notifyJobFailed(any(), any(), any());
        }

        @Test
        @DisplayName("permanent failure marks FAILED and notifies once")
        void permanentFailure_marksFailed_andNotifiesOnce() {
            TranscriptionJobEntity job = jobWithStatus(JobStatus.PROCESSING);
            job.setRetryCount(0);

            Exception ex = new RuntimeException("login required for this content");

            service.handleFailure(job, ex);

            assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
            verify(notificationService, times(1))
                    .notifyJobFailed(eq(job.getUserId()), eq(job.getId()), eq(ex.getMessage()));
        }

        @Test
        @DisplayName("exhausted retries on a transient error notifies exactly once")
        void exhaustedRetries_notifiesOnce() {
            TranscriptionJobEntity job = jobWithStatus(JobStatus.PROCESSING);
            job.setRetryCount(3); // at MAX_RETRIES

            Exception ex = new RuntimeException("Connection timeout while downloading");

            service.handleFailure(job, ex);

            assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
            verify(notificationService, times(1)).notifyJobFailed(any(), any(), any());
        }
```

Add a new sibling nested class for `markCompleted` (one nesting layer), placed after the `HandleFailure` class:

```java
    @Nested
    @DisplayName("markCompleted")
    class MarkCompleted {

        @Test
        @DisplayName("notifies completion with the resolved base-transcript title")
        void notifiesWithResolvedTitle() {
            TranscriptionJobEntity job = jobWithStatus(JobStatus.PROCESSING);
            job.setId(UUID.randomUUID());
            UUID baseTranscriptId = UUID.randomUUID();
            UUID userTranscriptId = UUID.randomUUID();

            BaseTranscriptEntity base = new BaseTranscriptEntity();
            base.setId(baseTranscriptId);
            base.setTitle("Resolved Title");
            when(baseTranscriptRepository.findById(baseTranscriptId)).thenReturn(Optional.of(base));

            service.markCompleted(job, baseTranscriptId, userTranscriptId);

            assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
            verify(notificationService, times(1))
                    .notifyJobCompleted(job.getUserId(), job.getId(), baseTranscriptId, "Resolved Title");
        }

        @Test
        @DisplayName("falls back to 'your video' title when the base transcript title is blank")
        void fallsBackToYourVideoTitle() {
            TranscriptionJobEntity job = jobWithStatus(JobStatus.PROCESSING);
            job.setId(UUID.randomUUID());
            UUID baseTranscriptId = UUID.randomUUID();

            BaseTranscriptEntity base = new BaseTranscriptEntity();
            base.setId(baseTranscriptId);
            base.setTitle("   ");
            when(baseTranscriptRepository.findById(baseTranscriptId)).thenReturn(Optional.of(base));

            service.markCompleted(job, baseTranscriptId, UUID.randomUUID());

            verify(notificationService).notifyJobCompleted(any(), any(), eq(baseTranscriptId), eq("your video"));
        }

        @Test
        @DisplayName("does not notify when there is no base transcript id")
        void doesNotNotifyWithoutBaseTranscript() {
            TranscriptionJobEntity job = jobWithStatus(JobStatus.PROCESSING);
            job.setId(UUID.randomUUID());

            service.markCompleted(job, null, UUID.randomUUID());

            assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
            verify(notificationService, never()).notifyJobCompleted(any(), any(), any(), any());
        }
    }
```

> `eq(...)` is already statically imported via `import static org.mockito.Mockito.*;`. `BaseTranscriptEntity`, `Optional`, and `UUID` are already imported in this test file.

- [ ] **Step 2: Run the tests**

```bash
cd /Users/apushpavannan/personal/content-categorise
./mvnw -q test -DargLine="-javaagent:/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar" -Dtest=TranscriptionJobServiceTest
```

Expected: PASS with NO production change. If `notifiesWithResolvedTitle` fails because `markCompleted` does not call `findById`, re-read `markCompleted` (L106–123) — it should already; do not redesign the service.

- [ ] **Step 3: Commit**

```bash
cd /Users/apushpavannan/personal/content-categorise
git add src/test/java/com/app/categorise/domain/service/TranscriptionJobServiceTest.java
git commit -m "test(jobs): lock in completion/failure notify wiring (transient never notifies)"
```

---

### Task 5: W4 — Change `DeviceController.unregister` to soft-deactivate

**Why:** `unregister` hard-deletes the device row, which is inconsistent with `handleSendError` (which soft-deactivates via `setActive(false)`). Soft-deactivation preserves history and matches the rest of the device lifecycle. Also ensure `register` reactivates (`active=true`) on the token-refresh and token-rotation branches so a previously soft-deactivated device comes back on re-register.

**Files:**
- Modify: `src/main/java/com/app/categorise/api/controller/DeviceController.java`

> Note: This controller has no dedicated unit test today and uses `@AuthenticationPrincipal`, so a pure controller unit test would require a security harness disproportionate to a 2-line change. The behaviour is exercised by the W3 manual checklist (register/unregister round-trip). Keep the change minimal and reviewable.

- [ ] **Step 1: Soft-deactivate in `unregister`**

In `unregister(...)` (L88–101), replace BOTH `deviceRepository.delete(device)` calls with soft-deactivation. The `deviceId` branch becomes:

```java
        if (deviceId != null && !deviceId.isBlank()) {
            deviceRepository.findByUserIdAndDeviceId(userId, deviceId)
                    .ifPresent(device -> {
                        device.setActive(false);
                        deviceRepository.save(device);
                        log.info("Deactivated device {} for user {}", device.getId(), userId);
                    });
        } else {
            deviceRepository.findByFcmToken(fcmToken)
                    .filter(d -> d.getUserId().equals(userId))
                    .ifPresent(device -> {
                        device.setActive(false);
                        deviceRepository.save(device);
                        log.info("Deactivated device {} for user {}", device.getId(), userId);
                    });
        }
```

- [ ] **Step 2: Ensure `register` reactivates on update/rotation branches**

In `register(...)`, set `active=true` in BOTH the token-refresh branch (L44–50) and the token-rotation branch (L53–60) so re-registering a soft-deactivated device reactivates it.

Token-refresh branch:

```java
        if (byToken.isPresent()) {
            DeviceEntity existing = byToken.get();
            existing.setDeviceId(request.deviceId());
            existing.setActive(true);
            deviceRepository.save(existing);
            log.info("Updated existing device {} for user {} (token refresh)", existing.getId(), userId);
            return ResponseEntity.ok(new DeviceRegistrationResponse(existing.getId(), false));
        }
```

Token-rotation branch:

```java
        if (byUserAndDevice.isPresent()) {
            DeviceEntity existing = byUserAndDevice.get();
            existing.setFcmToken(request.fcmToken());
            existing.setActive(true);
            deviceRepository.save(existing);
            log.info("Updated token for device {} for user {} (token rotation)", existing.getId(), userId);
            return ResponseEntity.ok(new DeviceRegistrationResponse(existing.getId(), false));
        }
```

- [ ] **Step 3: Compile + run the full backend suite (no test regressions)**

```bash
cd /Users/apushpavannan/personal/content-categorise
./mvnw -q test -DargLine="-javaagent:/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar"
```

Expected: PASS (no behavioural test depends on hard-delete).

- [ ] **Step 4: Commit**

```bash
cd /Users/apushpavannan/personal/content-categorise
git add src/main/java/com/app/categorise/api/controller/DeviceController.java
git commit -m "fix(device): soft-deactivate on unregister and reactivate on re-register"
```

---

### Task 6: W3 + W5 — Config-verification runbook & reliability note (NON-CODE / documentation)

**Why:** If `firebase.enabled` is false in prod, `NoOpNotificationService` is silently used and NO pushes are sent; and the iOS `aps-environment` must be `production` for release builds. Capture the verification checklist and the intended reliability model as a runbook. **This task writes documentation only — no source code, no tests.**

**Files:**
- Create: `docs/superpowers/runbooks/2026-06-28-job-completion-notifications-config.md`

- [ ] **Step 1: Create the runbook file**

Create `docs/superpowers/runbooks/2026-06-28-job-completion-notifications-config.md` with:

```markdown
# Job Completion Notifications — Config Verification Runbook

> Companion to `docs/superpowers/plans/2026-06-28-job-completion-notifications.md` (W3 + W5).

## W3 — Production config checklist

### Backend (`content-categorise`)
- [ ] `FIREBASE_ENABLED=true` set in the prod environment / props. **If false, `NoOpNotificationService` is used and NO pushes are sent.**
- [ ] `FIREBASE_SERVICE_ACCOUNT_PATH` points to a valid, readable service-account JSON on the prod host.
- [ ] Startup logs show `FirebaseApp` initialized (no "Firebase not initialized" debug lines when jobs complete).
- [ ] The service-account Firebase project matches the iOS `GoogleService-Info.plist` project.

### iOS (Scoop)
- [ ] `Scoop/Scoop.entitlements` `aps-environment` = **`production`** for App Store / TestFlight builds (currently `development`).
- [ ] Push Notifications capability enabled in the target; `Info.plist` `UIBackgroundModes` includes `remote-notification` (present).
- [ ] `GoogleService-Info.plist` present and matches the prod Firebase project.
- [ ] APNs Auth Key / cert uploaded to the Firebase console for the matching bundle id.
- [ ] Permission prompt requested at the intended moment; device successfully `POST /api/device/register`s an FCM token after login.

## W5 — Reliability model (intended design)

iOS silent (`content-available`) pushes are throttled and not guaranteed when the app is
backgrounded/terminated. Robust coverage comes from combining:
1. **Visible completion push (W2):** reaches backgrounded users and is tappable to deep-link.
2. **Foreground refresh (W2 iOS):** `willPresent` routes the payload through `handleSilentNotification` so in-app UI updates while the banner shows.
3. **Foreground polling fallback (`TranscriptSyncCoordinator`, ~20s):** covers foreground/edge cases and replays completions through the same silent path.

Rollback: set `FIREBASE_ENABLED=false` to disable all pushes (falls back to NoOp; polling still updates UI). The W2 payload can be reverted by removing the `notification` block (returns to silent-only). iOS W1 is a pure client fix shipped via app update.
```

- [ ] **Step 2: Commit**

```bash
cd /Users/apushpavannan/personal/content-categorise
git add docs/superpowers/runbooks/2026-06-28-job-completion-notifications-config.md
git commit -m "docs: add job-completion notifications config runbook and reliability model"
```

---

### Task 7: Full verification (both repos)

**Why:** Confirm the whole feature compiles and all tests pass together before manual E2E.

**Files:**
- Verify all changed files.

- [ ] **Step 1: Full backend suite**

```bash
cd /Users/apushpavannan/personal/content-categorise
./mvnw -q test -DargLine="-javaagent:/Users/apushpavannan/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar"
```

Expected: BUILD SUCCESS, all tests green (incl. `NotificationServiceImplTest`, `TranscriptionJobServiceTest`).

- [ ] **Step 2: Full iOS ScoopTests target**

```bash
cd /Users/apushpavannan/personal/TranscribeAssistant-ios
xcodebuild test \
  -project Scoop.xcodeproj \
  -scheme Scoop \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' \
  -only-testing:ScoopTests
```

Expected: TEST SUCCEEDED, all `NotificationHandlingTests` green and no regressions elsewhere.

- [ ] **Step 3: Confirm dead strings are gone**

```bash
cd /Users/apushpavannan/personal/TranscribeAssistant-ios
grep -rn "TRANSCRIPT_READY\|BATCH_COMPLETE" Scoop ScoopTests
```

Expected: NO matches (the dead cases were removed and nothing referenced them).

---

### Task 8: Manual end-to-end verification (NON-CODE / real device)

**Why:** Push delivery and tap deep-linking can only be fully validated on a real device with prod config. **This task is manual — no code or automated tests.** Complete the Task 6 W3 checklist first.

**Files:** none.

- [ ] **Step 1:** Ensure `FIREBASE_ENABLED=true` in the target backend env; log in on a real iOS device; confirm `POST /api/device/register` succeeded (device row `active=true`).
- [ ] **Step 2:** Submit `POST /api/video/transcribe-async`; **background the app**.
- [ ] **Step 3:** On completion, confirm a visible **"Transcript ready"** banner arrives with body `"<title>" is ready to view`.
- [ ] **Step 4:** Tap the banner → app opens and deep-links to the correct **transcript** (not the Activity tab).
- [ ] **Step 5:** Force a permanent failure (e.g. invalid URL) → confirm a **"Transcription Failed"** banner; tap → Activity tab.
- [ ] **Step 6:** Foreground the app mid-job → confirm the banner shows AND Feed/Activity refresh (W2 `willPresent` routing), and that `TranscriptSyncCoordinator` reflects completion.
- [ ] **Step 7:** Unregister the device (logout) → confirm the device row is `active=false` (soft-deactivated, not deleted); re-login → confirm it returns to `active=true`.

---

## Acceptance Criteria

- **W1:** iOS has ONE `NotificationType` enum used by both `handleNotificationTap` and `handleSilentNotification`; a tapped `TRANSCRIPT_COMPLETE` with a valid `transcriptId` deep-links to `.transcript(id:)`; unknown/missing type and `TRANSCRIPT_FAILED` route to `.activityTab`; the dead `TRANSCRIPT_READY` / `BATCH_COMPLETE` cases are removed (no references remain).
- **W2 (backend):** the completion push is a SINGLE visible message per active device containing a `notification` block (title "Transcript ready", body `"<title>" is ready to view`, `"your video"` fallback when blank) AND data `type=TRANSCRIPT_COMPLETE`, `jobId`, `transcriptId`, with APNs `sound=default`; `title` is threaded through (no longer dropped); no send when there are no active devices; no send when Firebase is uninitialized.
- **W2 (iOS):** foreground `willPresent` routes `userInfo` through `handleSilentNotification` so Feed/Activity refresh while the banner shows.
- **W4:** `DeviceController.unregister` soft-deactivates (`active=false`) instead of deleting; `register` reactivates (`active=true`) on the token-refresh and token-rotation branches.
- **Wiring:** `handleFailure` notifies ONLY on permanent failure (never on transient retries); `markCompleted` notifies with the resolved title.
- **W3/W5:** config runbook + reliability model documented.
- All backend tests pass with the Mockito Java agent; all `ScoopTests/NotificationHandlingTests` pass via `xcodebuild test`.

## Self-Review

- **Spec coverage:** Every scoped work item maps to a task — W1 → Task 1; W2 backend → Task 2; W2 iOS foreground → Task 3; W4 → Task 5; W3 + W5 → Task 6; plus wiring regression (Task 4), full verification (Task 7), and manual E2E (Task 8). No redesign beyond the scoped items.
- **Placeholder scan:** No step relies on deferred or vague instructions; every code step contains complete code.
- **Type/string consistency:** The canonical strings are `"TRANSCRIPT_COMPLETE"` / `"TRANSCRIPT_FAILED"`; iOS enum is `NotificationType` (cases `transcriptComplete`, `transcriptFailed`); backend constants are `TYPE_TRANSCRIPT_COMPLETE` / `TYPE_TRANSCRIPT_FAILED`; the iOS `DeepLink` cases used are `.transcript(id:)` and `.activityTab` (matching the existing enum). Body copy is `"<title>" is ready to view` with `"your video"` fallback in both backend code and the backend test, matching the spec.
- **TDD ordering:** Each code task writes a failing test first, runs it, implements, re-runs, then commits. Non-code tasks (Task 6 docs, Task 8 manual E2E) are clearly marked.
- **AGENTS.md compliance:** Backend tests are grouped by method-under-test with exactly one nesting layer (`NotifyJobCompletedTests`, `HandleFailure`, `MarkCompleted`). iOS tests extend the existing `ScoopTests/NotificationHandlingTests.swift` (no new notification test file).
- **No-migration / stack-top:** No Flyway migration or DB/API contract change is introduced; W2 is an additive payload change only.
