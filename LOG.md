# ClipSync Build Log

All phases are logged here in append-only format.

## Phase 1 — Network Spike (Wi-Fi + TLS) — 2026-09-25
**Status:** Partial
**Built:**
- Added a Windows TLS 1.3 test server listening on port `48653`.
- Added first-run self-signed certificate generation and persistence under `%APPDATA%\ClipSync`.
- Added Windows startup/shutdown wiring and logging, including a Firewall hint on client failures.
- Added the Android connection-test card with PC IP, port, message, send action, response, and round-trip timing.
- Added the Android TLS test client with the planned temporary trust-all behavior.
**Files added/changed:**
- `windows/ClipSync.Windows/Transport/TlsTestServer.cs`
- `windows/ClipSync.Windows/App.xaml.cs`
- `android/app/src/main/java/com/clipsync/android/ui/ConnectionTestClient.kt`
- `android/app/src/main/java/com/clipsync/android/ui/MainActivity.kt`
- `android/app/src/main/java/com/clipsync/android/ui/MainScreen.kt`
- `android/app/src/main/AndroidManifest.xml`
- `android/app/build.gradle.kts`
- `android/gradle.properties`
- `android/local.properties` (local SDK configuration; do not commit)
**Design note (if UI):** The connection test is presented as a rounded Material 3 card matching the existing ClipSync status/diagnostics cards, with consistent spacing and full-width controls. It remains explicitly temporary Phase 1 UI rather than introducing final navigation.
**Known issues:**
- Physical phone-to-PC Wi-Fi tests have not been completed yet.
- Android uses a trust-all TLS callback temporarily; certificate pinning is deferred to Phase 5.
- Android SDK tooling emits a non-blocking SDK XML compatibility warning.
- No commit or push has been performed yet; those remain deferred until manual testing is complete and you explicitly approve them.
**Manual test required:** yes — run the Phase 1 shared-router and Windows-hotspot tests from `plan.md`; record round-trip times and verify `pong: <text>`.


### Phase 1 manual-test update — 2026-09-25
- **Hotspot test (Test B): Passed.** Phone connected through the Windows laptop hotspot at `192.168.137.1`; the connection test returned `pong: hello` in `21 ms`.
- **Shared-router test (Test A): Pending.**
- **Commit/push:** still deferred until the remaining manual verification is complete and explicitly approved.
- **Shared-router test (Test A): Passed.** Phone and laptop connected on the same Wi-Fi network; using the laptop Wi-Fi address `192.168.1.35`, the connection test succeeded with the expected `pong: hello` response.
- **Phase 1 manual verification:** Test A and Test B are now both passed. Commit/push remain deferred until explicit approval.

## Phase 2 — Notification-Triggered Clipboard Read — 2026-09-25
**Status:** Done (ready for manual test)
**Built:**
- Replaced the discarded Shizuku work with a normal Android foreground clipboard-watch service.
- Added one-tap notification flow: clipboard change posts a styled `Copied text ready` notification; tapping it opens a transparent, immediately-finishing reader activity.
- Added local-only count and last-read length for verification; no networking or sync is used in this phase.
- Added notification permission request on first reader start and a persistent foreground-service notification.
- Added length + six-character hash logging without logging clipboard content.
**Files added/changed:**
- `android/app/src/main/java/com/clipsync/android/clipboard/ClipboardReadStore.kt`
- `android/app/src/main/java/com/clipsync/android/service/ClipboardWatchService.kt`
- `android/app/src/main/java/com/clipsync/android/ui/ClipboardReadActivity.kt`
- `android/app/src/main/java/com/clipsync/android/ui/MainActivity.kt`
- `android/app/src/main/java/com/clipsync/android/ui/MainScreen.kt`
- `android/app/src/main/AndroidManifest.xml`
- `android/app/src/main/res/values/themes.xml`
**Design note (if UI):** The screen is status-first: `Watching clipboard` or `Reader stopped`, with the counter and one clear start/stop action. The notification uses the existing indigo/dark Material 3 direction and a readable title/action instead of a bare default notification. The reader activity has a transparent theme and no UI so the tap returns immediately.
**Known issues:**
- Physical Android notification/clipboard behavior has not been tested by me; it requires the human's phone.
- Android tooling still emits the non-blocking SDK XML compatibility warning.
- The debug APK remains large; APK optimization is deferred as previously agreed.
- No networking, Shizuku, root, Accessibility Service, or Developer Options are used in this phase.
**Manual test required:** yes — install the APK, grant notification permission once, start the reader, copy text in another app, tap the ClipSync notification, and verify the local counter/length/log behavior.
**Cleaned up:** discarded uncommitted Shizuku files via the requested reset; build outputs remain ignored and no scratch attachment is present.

### Phase 2 manual-test update — missing clip notification — 2026-09-25
- The foreground-service notification appeared, proving the service started, but the copied-text notification did not.
- Added explicit logs for listener registration and clipboard callback delivery.
- Separated the persistent service notification and the copied-text notification into separate Android channels; the copied-text channel is high importance for this test.
- Rebuilt the APK successfully; retest is required.

### Phase 2 design update — manual triggers — 2026-09-25
**Status:** Ready for manual test
- Confirmed the automatic clipboard listener did not fire on the Motorola Edge 20 Pro / Android 13 despite successful foreground-service registration.
- Updated local `architecture.md` and `plan.md` to reject auto-triggered copy detection.
- Replaced it with a persistent notification action, **Send clipboard now**, plus an optional Quick Settings Tile, **Send to PC**; both trigger the same transparent one-tap clipboard read.
- Rebuilt successfully with `:app:assembleDebug :core:test`.

### Phase 2 manual-test update — manual action diagnostic — 2026-09-25
- The persistent service started, but the manual action did not produce a `Clipboard read` log or counter increment.
- Added activity-launch, no-clip, empty-text, successful-read-length, and read-failure diagnostics to distinguish notification-action delivery from clipboard-read behavior.
- Rebuilt successfully; the next manual run must use the new APK and share the resulting diagnostics.

## Phase 2 — Quick Settings primary trigger and reboot resume — 2026-09-25
**Status:** Done / ready for manual test
**Built:**
- Removed the clipboard-send action from the persistent foreground-service notification; it is now a quiet status-only notification.
- Added first-run guidance with exact Quick Settings tile setup steps and an **Add Send to PC tile** button.
- Kept **Send to PC** as the primary manual clipboard-read trigger.
- Added an enabled-state preference and `BOOT_COMPLETED` receiver so the foreground service requests restart after reboot once the user has started it; stopping the reader clears that preference.
- Isolated the transparent clipboard-read activity in an excluded task and removes that task after reading, preventing the main ClipSync screen from being brought forward by the tile flow.
- Changed the on-screen verification label from **Clips sent** to **Clipboard reads**; Phase 2 still performs local count/length/hash logging only.
**Files added/changed:**
- `android/app/src/main/AndroidManifest.xml`
- `android/app/src/main/java/com/clipsync/android/clipboard/ClipboardReadStore.kt`
- `android/app/src/main/java/com/clipsync/android/service/ClipboardWatchService.kt`
- `android/app/src/main/java/com/clipsync/android/service/BootCompletedReceiver.kt`
- `android/app/src/main/java/com/clipsync/android/ui/ClipboardReadActivity.kt`
- `android/app/src/main/java/com/clipsync/android/ui/MainActivity.kt`
- `android/app/src/main/java/com/clipsync/android/ui/MainScreen.kt`
- `android/app/src/main/java/com/clipsync/android/ui/SendClipboardTileService.kt`
- `android/app/src/main/res/values/bools.xml`
- `android/app/src/main/res/values-v24/bools.xml`
- Local planning update: `architecture.md`, `plan.md`
**Design note (if UI):** The status-first screen now explains the real Phase 2 flow: start the reader, add the tile using numbered steps, copy text, and tap **Send to PC**. The notification is intentionally quiet and status-only, while the tile is the visible manual trigger. The screen also explains that the tile is unavailable on older Android versions and provides an in-app fallback path where that versioned UI is reachable.
**Known issues:**
- The current repository still has `minSdk = 31`, so Android 4.4 support is **not yet honestly implemented**. Quick Settings tiles do not exist on Android 4.4, and the current Compose dependency requires a higher minimum; a separate legacy-UI compatibility pass is required for API 19. This change does not claim API 19 support.
- Reboot behavior, notification appearance, tile behavior, and the recent-task issue require the human's phone to verify.
- Android tooling emits the existing non-blocking SDK XML compatibility warning.
- Debug APK remains approximately 63.2 MB; optimization is deferred.
- No networking, Shizuku, root, Accessibility Service, or Developer Options are used in this phase.
**Manual test required:** yes — install this new APK; verify the quiet notification, exact tile onboarding, tile read without reopening ClipSync, enabled-service resume after reboot, and disabled-service behavior after stopping it.

## Phase 2 — Android 7+ compatibility expansion — 2026-09-25
**Status:** Done / ready for manual test
**Built:**
- Lowered the Android app and shared core minimum SDK from API 31 to API 24 (Android 7.0).
- Kept the Quick Settings **Send to PC** tile as the supported manual trigger for every targeted Android version.
- Retained version-specific service startup: `startService()` on Android 7, `startForegroundService()` on Android 8+; notification permission remains Android 13+ only.
- Retained reboot resume through the enabled-state preference and `BOOT_COMPLETED` receiver.
- Fixed the API 24+ lint issue around the deprecated pre-Android-14 tile collapse overload with a narrowly scoped suppression; newer Android uses the PendingIntent overload.
**Files added/changed:**
- `android/app/build.gradle.kts`
- `android/core/build.gradle.kts`
- `android/app/src/main/java/com/clipsync/android/ui/SendClipboardTileService.kt`
- `architecture.md`
- `plan.md`
- `LOG.md`
**Design note (if UI):** The existing tile-first onboarding remains the same across the supported range: users are shown exact steps to add **Send to PC** to Quick Settings. Android 7–12 do not request notification permission; Android 13+ may request it. The persistent notification remains a quiet service-status indicator.
**Known issues:**
- Only Android SDK Platform 34 is installed locally and no Android emulator AVDs are configured, so API 24/API 28/API 31 device runtime behavior could not be verified locally.
- The APK manifest now reports `minSdkVersion 24`, `targetSdkVersion 34`.
- Android 15+ and future Android versions still require runtime verification.
- Debug APK remains approximately 15.6 MB after the minSdk change; release-size optimization is deferred.
- No networking, Shizuku, root, Accessibility Service, or Developer Options are used in this phase.
**Manual test required:** yes — install the new APK on the real phone, verify the tile flow, no main-app reopen, and reboot resume/stop behavior.

## Phase 3 � Shared Protocol and Sync Core � 2026-09-25
**Status:** Implemented; awaiting user approval before commit/push.
**Built:** Added shared JSON protocol vectors, C# and Kotlin frame codecs, message types, incremental frame readers, Lamport ordering, hash/pending/scheduler primitives, and cross-language unit tests.
**Validation:** `dotnet test` passed (4 tests) and `./gradlew :core:test` passed after fixing the shared-vector and Kotlin compilation issues. No commit or push has been performed.
**Scope:** No sockets, real clipboard, or UI changes.
## Phase 4 — Windows App + Test Client — 2026-09-25
**Status:** Done
**Built:**
- Native Windows clipboard watcher using `AddClipboardFormatListener`, message-only window, 125 ms debounce, and locked-clipboard retries.
- mTLS identity storage protected with Windows DPAPI and persistent peer fingerprint pinning.
- Framed TLS sync server wired to the Phase 3 engine protocol; text clipboard broadcast and incoming text writes.
- Pairing window and 6-digit fingerprint-derived code display; local mDNS service advertisement.
- Polished tray popover with state card, distinct status icon/color, pause/resume, pairing, log folder, and exit actions.
- `ClipSync.TestClient` console tool retained in-repo for PC-only pairing and text exchange.
**Files added/changed:** `windows/ClipSync.Windows/App.xaml.cs`, `Clipboard/ClipboardWatcher.cs`, `Security/CertificateStore.cs`, `Transport/SyncServer.cs`, `UI/StatusWindow.xaml`, `UI/StatusWindow.xaml.cs`, `UI/TrayIconManager.cs`, `windows/ClipSync.TestClient/*`.
**Design note (if UI):** Kept the Phase 0 indigo/teal/warm-neutral palette, using a compact rounded popover, a high-contrast status card, and icon/color pairs (green connected, amber waiting, violet paused, red error) so state is scannable before reading text.
**Known issues:** The local mDNS announcement is a lightweight multicast advertisement rather than a full DNS-SD responder. Manual visual UI validation remains recommended.
**Manual test required:** yes


Pairing follow-up (2026-09-25): fixed pairing-code display/handshake to carry the server-computed fingerprint-derived code in the PAIR message, preventing stale or independently recomputed codes from disagreeing.

Pairing UX follow-up (2026-09-25): Pair new device now explicitly opens a two-minute waiting state; the popover remains visible with Waiting for TestClient... until the client presents its certificate, then replaces it with the authoritative six-digit code from the server handshake.

Bugfix follow-up (2026-09-25): tray popover opening is now guarded against the initial deactivation race; TestClient pairing defaults to the server-provided code after explicit confirmation, preventing manual digit-entry mistakes.

Tray follow-up (2026-09-25): Show Status context-menu activation is now deferred until the menu closes, and popover deactivation ignores the initial 500 ms focus transition so the first activation is not lost.

Tray final follow-up (2026-09-25): Show Status now always forces the popover open instead of toggling a still-visible fade-out window, eliminating the first-click no-op after deactivation.


