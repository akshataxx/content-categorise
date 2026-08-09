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
