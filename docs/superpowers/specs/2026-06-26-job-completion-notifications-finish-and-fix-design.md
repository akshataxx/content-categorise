# Design Spec — Job Completion/Failure Push Notifications (Finish & Fix)

- **Status:** Draft for review
- **Date:** 2026-06-26
- **Type:** Finish & Fix (NOT greenfield — feature is ~90% implemented end-to-end via FCM)
- **Backend repo:** `content-categorise` (Java 24, Spring Boot 3.5, Maven, PostgreSQL + Flyway, Firebase Admin SDK)
- **iOS repo:** `TranscribeAssistant-ios/Scoop` (SwiftUI app "Scoop", Firebase Messaging, APNs)

> **Stacked PR plan (3 features, merge bottom→top).** This feature has **no migration**, so it sits at the **top** of the stack (lowest-risk, no DB; rebases cleanly over the others):
> 1. Collections / playlists — **V28** (stack base)
> 2. Personal analytics (Phase 1, no migration; optional index **V29**)
> 3. **Job-completion notifications** — *this spec* — no migration (stack top)
>
> _Auto-tagging & search overhaul is deferred to its own separate stack._

---

## 1. Goal

When an asynchronous transcription job completes (or permanently fails), the user's
device should receive a push notification so the UI updates and/or the user is
informed, with a tap deep-linking to the relevant transcript.

The mechanism (FCM → APNs) is **already built and wired end-to-end**. This spec scopes
the *remaining* work: fix one confirmed deep-link bug, optionally add a user-visible
completion banner, verify production configuration, and tidy two minor inconsistencies.

---

## 2. Current State (already implemented)

### 2.1 End-to-end flow (in words)

**Completion (silent today):**

1. Poller `JobPollerService.executeJob` claims a PENDING job every ~5s and runs the pipeline.
2. On success, `TranscriptionJobService.markCompleted(...)` (`domain/service/TranscriptionJobService.java`, ~L107–123) sets status `COMPLETED`, resolves the video title from `BaseTranscriptRepository`, and calls `notificationService.notifyJobCompleted(userId, jobId, baseTranscriptId, title)` inside a try/catch (push failure never breaks the job).
   - The sync path uses `markCompletedForUrl(...)` (~L129–135) which delegates to `markCompleted`.
3. `NotificationServiceImpl.notifyJobCompleted` (`application/internal/NotificationServiceImpl.java`) sends a **SILENT** push per active device: data-only (`type=TRANSCRIPT_COMPLETE`, `jobId`, `transcriptId`, `silent=true`), `content-available=true`, **no** `notification` block.
4. FCM → APNs delivers to the device. `AppDelegate.didReceiveRemoteNotification` (`AppDelegate.swift`) forwards silent pushes to `NotificationManager.handleSilentNotification(_:)`.
5. `handleSilentNotification` (`service/NotificationManager.swift`) matches `TRANSCRIPT_COMPLETE`, increments badge/counters, and posts `.silentPushReceived` so ViewModels (Feed/Activity) refresh.

**Failure (visible today):**

1. `TranscriptionJobService.handleFailure(...)` (~L139–162): transient failures (with retries remaining) re-queue with exponential backoff and **do NOT notify**. Only **permanent** failures (else-branch, retries exhausted) set status `FAILED` and call `notifyJobFailed(...)` in a try/catch.
2. `NotificationServiceImpl.notifyJobFailed` sends a **VISIBLE** push: `notification` block (title "Transcription Failed", body), data `type=TRANSCRIPT_FAILED`, sound `default`.
3. iOS shows a banner. Tap → `AppDelegate.didReceive` posts `.notificationTapped` → `NotificationManager.handleNotificationTap` → routes `TRANSCRIPT_FAILED` to the Activity tab.

**Foreground fallback:** `TranscriptSyncCoordinator` (iOS) polls `GET /api/video/jobs` every ~20s and replays completions through the same `handleSilentNotification` path (started/stopped in `BottomNavBar.swift`).

### 2.2 Supporting infrastructure (verified present)

- **Bean selection:** `config/NotificationConfig.java` — real `NotificationServiceImpl` is registered `@ConditionalOnBean(FirebaseApp.class)`; otherwise `NoOpNotificationService` via `@ConditionalOnMissingBean`.
- **Firebase init:** `config/FirebaseConfig.java` — `@ConditionalOnProperty(firebase.enabled=true)`, reads `firebase.service-account-path`.
- **Props:** `application.properties` — `firebase.enabled=${FIREBASE_ENABLED:false}`, `firebase.service-account-path=${FIREBASE_SERVICE_ACCOUNT_PATH:./firebase-service-account.json}`. **Default OFF.**
- **Devices:** migration `V18__create_devices_table.sql` (`user_id`, `platform`, `fcm_token` UNIQUE len 512, `device_id`, `active`, timestamps). `data/entity/DeviceEntity.java`. `api/controller/DeviceController.java` — `POST /api/device/register` (token refresh + rotation), `DELETE /api/device/unregister`. `data/repository/DeviceRepository.java` — `findByUserIdAndActiveTrue`, `findByFcmToken`, `findByUserIdAndDeviceId`.
- **Token deactivation:** `NotificationServiceImpl.handleSendError` soft-deactivates (`active=false`) on FCM `UNREGISTERED` / `INVALID_ARGUMENT`.
- **iOS plumbing:** `ScoopApp.swift` (`@UIApplicationDelegateAdaptor`), `AppDelegate.swift` (`FirebaseApp.configure()`, Messaging + UNUserNotificationCenter delegates, APNs→FCM forwarding, token refresh → `.fcmTokenReceived`), `NotificationManager` (permission flow, badge, deep-link parsing), `DeviceService` (register/unregister, triggered from `SessionManager.swift`).
- **iOS config present:** `Scoop.entitlements` has `aps-environment` (currently `development`), `Info.plist` has `UIBackgroundModes=[remote-notification]`, `GoogleService-Info.plist` present.

---

## 3. Gap Analysis

| Capability | Implemented? | File(s) | Gap |
|---|---|---|---|
| Backend completion push (silent) | ✅ | `NotificationServiceImpl.notifyJobCompleted` / `sendSilentNotification` | None (silent-only by design) |
| Backend failure push (visible) | ✅ | `NotificationServiceImpl.sendFailedNotification` | None |
| Job → notify hooks | ✅ | `TranscriptionJobService.markCompleted` (~L118), `handleFailure` (~L156) | None; transient failures intentionally silent |
| Bean gating / NoOp fallback | ✅ | `NotificationConfig`, `FirebaseConfig` | Verify prod props (else NoOp = no pushes) |
| Device register/token rotation | ✅ | `DeviceController.register` | None |
| Device unregister | ⚠️ | `DeviceController.unregister` | **Hard-deletes** row vs `handleSendError` soft-deactivate — inconsistent |
| iOS silent-push handling | ✅ | `NotificationManager.handleSilentNotification` (matches `TRANSCRIPT_COMPLETE`) | None |
| iOS tap deep-link on completion | ❌ | `NotificationManager.handleNotificationTap` (~L187) | **BUG**: no `TRANSCRIPT_COMPLETE` case → falls to Activity tab default, not transcript |
| Visible completion banner | ❌ | completion path is data-only | Optional (decision B) |
| Foreground present (`willPresent`) | ✅ | `AppDelegate.willPresent` returns `[.banner,.sound,.badge]` | Only fires for pushes with a `notification` block (i.e. needs W2) |
| Prod config (backend + iOS) | ❓ | props / entitlements / plist | Must verify (incl. `aps-environment` value for prod) |

---

## 4. Confirmed Bug Detail (W1)

Backend completion sends data `type=TRANSCRIPT_COMPLETE` (`NotificationServiceImpl.java` L62). On iOS:

- **Silent handler matches it** — `handleSilentNotification` (`NotificationManager.swift` L228–229): `switch type { case "TRANSCRIPT_COMPLETE": ... }`.
- **Tap handler does NOT** — `handleNotificationTap` (L187–207):

```swift
switch type {
case "TRANSCRIPT_READY":            // ← backend never sends this string
    // deep link to .transcript(id:)
case "TRANSCRIPT_FAILED", "BATCH_COMPLETE":
    pendingDeepLink = .activityTab
default:
    pendingDeepLink = .activityTab  // ← TRANSCRIPT_COMPLETE lands here
}
```

So a *tapped* completion notification deep-links to the Activity tab instead of the
transcript. (Today this is partially masked because completion is silent and rarely
tappable, but it becomes a real, user-visible defect the moment W2 adds a banner.)

**Fix:** align on ONE canonical constant set across backend + iOS silent-handler + tap-handler.
Recommended canonical type = `TRANSCRIPT_COMPLETE` (already emitted by backend & matched by silent handler), so only the **tap handler** changes — lowest risk. Centralize the string into a single Swift constants enum (e.g. `NotificationType`) to prevent future drift.

---

## 5. Scoped Work Items

### W1 — Fix notification type-string mismatch (required, small)

- **iOS** `service/NotificationManager.swift` `handleNotificationTap`: add a `TRANSCRIPT_COMPLETE` case that deep-links to `.transcript(id:)` using `transcriptId` (mirror the existing `TRANSCRIPT_READY` body). Keep `TRANSCRIPT_READY` as a tolerated alias only if needed for back-compat; otherwise remove it (backend never sends it).
- **iOS** introduce one source of truth for type strings (e.g. `enum NotificationType: String { case transcriptComplete = "TRANSCRIPT_COMPLETE"; case transcriptFailed = "TRANSCRIPT_FAILED" }`) and use it in both `handleNotificationTap` and `handleSilentNotification`.
- **Backend** (optional symmetry): extract `"TRANSCRIPT_COMPLETE"` / `"TRANSCRIPT_FAILED"` into named constants in `NotificationServiceImpl` so the contract is documented in code.
- **Files:** `NotificationManager.swift`; (optional) `NotificationServiceImpl.java`.

### W2 — Add visible completion notification (recommended; additive)

- **Backend** `NotificationServiceImpl.sendSilentNotification` (or a new `sendCompletionNotification`): add a `notification` block alongside the existing data so the completion message is both **visible and actionable**:

```java
Message.builder()
    .setToken(device.getFcmToken())
    .setNotification(Notification.builder()
        .setTitle("Transcript ready")
        .setBody("\"" + title + "\" is ready to view")   // title already passed to notifyJobCompleted
        .build())
    .putData("type", "TRANSCRIPT_COMPLETE")
    .putData("jobId", jobId.toString())
    .putData("transcriptId", transcriptId.toString())
    .setApnsConfig(ApnsConfig.builder()
        .setAps(Aps.builder().setSound("default").build())   // visible alert; drop content-available-only
        .build())
    .build();
```
  - Note: pass `title` through `sendSilentNotification` (currently dropped) — `notifyJobCompleted` already receives it.
  - Decision needed: send a **single** visible push (simplest) vs **both** a silent data push (for background UI refresh) and a visible alert. A single push with `notification` + data is preferred; iOS `willPresent`/`didReceive` both deliver `userInfo`, so the silent-refresh path can be triggered from the tap/foreground handlers too. (See W5.)
- **iOS** confirm foreground presentation: `AppDelegate.willPresent` already returns `[.banner,.sound,.badge]` (good). Ensure the foreground handler ALSO routes the data through `handleSilentNotification` so the Feed/Activity UI updates even when the banner shows in-app (today `willPresent` only presents; it does not call the silent handler).
- **iOS** tap deep-link: covered by W1.
- **Failure path:** leave as-is (already visible).
- **Files:** `NotificationServiceImpl.java`, `AppDelegate.swift`, `NotificationManager.swift`.

### W3 — Config verification runbook (required; checklist)

**Backend (prod):**
- [ ] `FIREBASE_ENABLED=true` set (env or prod props) — otherwise `NoOpNotificationService` is silently used and **no pushes are sent**.
- [ ] `FIREBASE_SERVICE_ACCOUNT_PATH` points to a valid, readable service-account JSON on the prod host.
- [ ] App logs at startup show `FirebaseApp` initialized (no `Firebase not initialized` debug lines when jobs complete).
- [ ] Service account project matches the iOS `GoogleService-Info.plist` project.

**iOS:**
- [ ] `Scoop.entitlements` `aps-environment` = **`production`** for App Store/TestFlight builds (currently `development`).
- [ ] Push Notifications capability enabled in the target; `UIBackgroundModes` includes `remote-notification` (present).
- [ ] `GoogleService-Info.plist` present and matches the prod Firebase project (present).
- [ ] APNs Auth Key / cert uploaded to Firebase console for the matching bundle id.
- [ ] Permission prompt actually requested at the intended moment (via `NotificationManager`/`NotificationPermissionSheet`); device successfully `POST /api/device/register`s an FCM token after login.

### W4 — Align unregister to soft-deactivate (DECIDED: included; consistency)

- `DeviceController.unregister` currently `deviceRepository.delete(device)`. Change to `device.setActive(false); deviceRepository.save(device)` to match `handleSendError` semantics and preserve history. (Re-register already reactivates via token-refresh/rotation branches — verify the rotation branch also sets `active=true`.)
- **Files:** `DeviceController.java` (and confirm `register` sets `active=true` on the update branches).

### W5 — Reliability note (optional; documentation)

iOS silent (`content-available`) pushes are throttled and **not guaranteed** when the app is
backgrounded/terminated. Therefore silent-only completion (current state) can miss updates.
The combination of **(a)** the visible completion push (W2) and **(b)** the foreground
`TranscriptSyncCoordinator` polling gives robust coverage: visible push reaches backgrounded
users; polling covers foreground/edge cases. Document this as the intended reliability model.

---

## 6. Testing Strategy

Repo uses **JUnit5 + Mockito + AssertJ + Testcontainers**. Group tests by method-under-test
(one nesting layer) per backend AGENTS.md.

**Backend unit (extend `NotificationServiceImplTest`, mock `FirebaseMessaging` static):**
- `notifyJobCompleted` (W2): captured `Message` contains a `notification` block AND data `type=TRANSCRIPT_COMPLETE`, `transcriptId`, `jobId`. (Use `MockedStatic<FirebaseMessaging>` + `ArgumentCaptor<Message>`; existing tests already mock `FirebaseApp`.)
- `notifyJobCompleted` with no active devices → no send.
- `handleSendError` deactivates device on `UNREGISTERED`/`INVALID_ARGUMENT` (existing).

**Backend unit (`TranscriptionJobServiceTest`):**
- `handleFailure` transient + retries remaining → re-queues (`PENDING`) and `notifyJobFailed` is **never** called (`verify(..., never())`).
- `handleFailure` permanent / retries exhausted → status `FAILED` and `notifyJobFailed` called once.
- `markCompleted` → `notifyJobCompleted` called with resolved title.

**iOS unit (extend existing `ScoopTests/NotificationHandlingTests.swift` — do NOT create a new file):**
- Type-constant matching: `handleNotificationTap` with `type=TRANSCRIPT_COMPLETE` + valid `transcriptId` sets `pendingDeepLink == .transcript(id:)` (regression test for W1).
- Unknown/missing type → `.activityTab` (existing default).
- After removing the dead `TRANSCRIPT_READY` case, confirm no test depends on it (verified: only `TRANSCRIPT_COMPLETE` is used across iOS tests).

**Manual end-to-end (real device, post-W2):**
1. Ensure `FIREBASE_ENABLED=true` in target backend env; log in on a real iOS device; confirm device registered.
2. Submit `POST /api/video/transcribe-async`; **background the app**.
3. Confirm a visible "Transcript ready" banner arrives on completion.
4. Tap it → app opens and deep-links to the correct transcript (not Activity tab).
5. Force a permanent failure (e.g. invalid URL) → confirm "Transcription Failed" banner; tap → Activity tab.
6. Foreground the app mid-job → confirm `TranscriptSyncCoordinator` still reflects completion.

---

## 7. Rollout, Verification & Rollback

- **Gating:** entire feature is config-gated by `firebase.enabled`. W2 is an **additive payload** change (adds a `notification` block); no schema or API contract change.
- **Rollout:** ship W1 + W3 first (bug fix + config verify). Ship W2 once backend config verified in prod. W4 (soft-deactivate) included; W5 is documentation.
- **Verification:** use the W3 checklist + manual E2E. Watch backend logs for FCM send warnings/`UNREGISTERED` deactivations.
- **Rollback:** set `FIREBASE_ENABLED=false` to disable all pushes (falls back to NoOp; foreground polling still updates UI). W2 payload can be reverted by removing the `notification` block (returns to silent-only). iOS W1 is a pure client fix shipped via app update.

---

## 8. Open Questions / Assumptions

1. ~~**Decision B confirmation**~~ **RESOLVED:** YES — ship the visible completion banner (W2).
2. ~~**Single vs dual push**~~ **RESOLVED:** **single** visible push carrying data; trigger UI
   refresh from the foreground/tap handlers (no separate silent push).
3. ~~**`TRANSCRIPT_READY` alias**~~ **RESOLVED:** **remove it** — verified no producer emits
   `TRANSCRIPT_READY` or `BATCH_COMPLETE` (backend or iOS); they are dead consumer cases.
4. ~~**Android**~~ **RESOLVED:** iOS-only scope; leave `setAndroidConfig` as-is, untested.
5. ~~**`aps-environment`**~~ **RESOLVED:** must be **`production`** for release/TestFlight builds
   (release checklist item in W3, not optional).
6. ~~**Title quality**~~ **RESOLVED:** keep the `"your video"` fallback when the resolved title
   is blank (body: `"your video" is ready to view`).
