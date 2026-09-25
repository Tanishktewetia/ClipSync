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



## Phase 5 — Android receive-only sync: clipboard UI constraint — 2026-09-26
**Status:** Blocked
**Built:**
- Preflight only: reread RULES.md, architecture.md, and plan.md in full; confirmed the expected ClipSync Git remote and initially clean working tree.
- Verified Android's official clipboard documentation before implementation. EXTRA_IS_SENSITIVE prevents sensitive content from appearing in the copied-content preview; it does not promise suppression of the system visual confirmation.
- No application code, architecture, dependencies, or build artifacts changed; no build/test run performed for this preflight.
**Files added/changed:** LOG.md only.
**Design note (if UI):** No UI changed. Intended Phase 5 design remains the existing Material 3 identity, a status-first screen, six-digit pairing UI, Pause, and collapsed Advanced settings. The architectural question is whether an Android-owned redacted clipboard confirmation is acceptable; the app would not add a toast, snackbar, or clipboard notification.
**Known issues:**
- Phase 5's strict zero-popup acceptance criterion cannot be guaranteed by EXTRA_IS_SENSITIVE. Awaiting approval to proceed with automatic PC -> phone writes, sensitive preview redaction, and no app-generated copy feedback, while checking actual Motorola overlay behavior during manual testing. This is not a request for root, debugging, helper apps, or any other setup workaround.
- Official source: https://developer.android.com/develop/ui/views/touch-and-input/copy-paste — sections “Provide feedback when copying to the clipboard” and “Add sensitive content to the clipboard.”
- Phone -> PC remains out of Phase 5 scope. Preserve Phase 2's manual trigger approach, not auto-detection. The updated Phase 2 sections specify a quiet status notification without a send action and a primary Quick Settings tile; any notification-action change belongs to the later explicitly authorized send phase.
**Manual test required:** yes — after implementation and APK build; no new APK is available from this preflight.

## Phase 5 — Android pairing and automatic PC → phone — 2026-09-26
**Status:** Done (built and ready for manual phone testing; uncommitted)
**Built:**
- Keystore EC identity, TLS 1.3 client authentication, locally verified six-digit pairing, exact peer-certificate pinning, and AES-GCM-protected pin storage with backup exclusions. Removed Android's trust-all spike.
- Receive-only connectedDevice foreground service, quiet persistent notification, Wi-Fi lock, one-time battery-exemption request, saved manual IPv4 connection, and preserved enabled/boot behavior.
- Sensitive-flagged automatic clipboard writes with the Kotlin core state machine, bounded hash echo guard, consecutive dedup, and failed-write safety. Existing manual tile reads suppress our own writes and do not send.
- Final status-first Material 3 screen, Pause, six-digit confirmation dialog, collapsed Advanced/IP/Diagnostics, scoped-storage log export, light/dark contrast fixes and vector app glyphs.
- Required Windows integration fixes: LAN listener, explicit bounded replacement pairing for the real phone after TestClient, normal-handshake pin rejection, one active confirmed session, and accurate resumed status. Preserved Phase 4 wire compatibility.
- Verification: 26 Android JVM/core tests and 14 Windows core/TLS tests passed (40 total); APK built and v2 signature verified; Android lint 0 errors/8 warnings; Windows Release build 0 warnings/0 errors; git diff --check clean. APK: dist/ClipSync-debug-0.5.0.apk (16.08 MiB). No new library dependencies.
**Files added/changed:** .gitignore; android/app/build.gradle.kts; android/app/src/main/AndroidManifest.xml; Android clipboard/security/store/transport/service/ui/theme files and drawable/xml resources; android/app/src/test/.../PairingProtocolTest.kt; android/core/src/main/.../Protocol.kt and ReceiveSession.kt; android/core/src/test/.../ReceiveSessionTest.kt; android/gradlew.bat; windows/ClipSync.Windows/{App.xaml.cs,Security/CertificateStore.cs,Transport/SyncServer.cs}; windows/ClipSync.Windows.Tests/*; windows/ClipSync.sln and ClipSync.slnx; docs/phase-5-report.md; LOG.md. Detailed purpose groups are in the report.
**Design note (if UI):** Kept the existing indigo/teal identity and light/dark surfaces. One large rounded status card uses green/amber/violet/red plus meaningful icons and plain copy; Pause stays visible and technical controls collapse into Advanced. The code dialog uses six balanced digit cells and a combined accessibility label. A restrained status-color transition replaces distracting motion, and dark-mode button foregrounds were adjusted for contrast. Physical visual QA remains required.
**Known issues:**
- The preflight blocker above was resolved by the user's approval to continue. EXTRA_IS_SENSITIVE redacts the preview but cannot guarantee suppression of Android-owned clipboard confirmation. No architecture/plan edits were made.
- Physical Motorola/Wi-Fi/screen-off/notification/battery/reboot/UI behavior remains untested. No emulator is configured. The existing running Windows Debug app was not stopped; exit it and run the newly built Release executable for testing.
- Retained minSdk 24; secure sync requires platform TLS 1.3 (normally Android 10+), without insecure fallback. The real target is Android 13. Existing CSP1 framing retains its 1 MiB text cap.
- Auto-discovery/retry/PING and phone sending/images remain out of scope. Phase 2's manual trigger was preserved; no auto-copy detection or new send action was added.
- Eight lint warnings remain: four dependency-update notices, two existing obsolete SDK checks/resources, the explicitly requested battery exemption and reviewed custom pin trust manager.
- Fixed the inherited malformed .sln and wrapper OS-variable quoting so verification commands work. Generated artifacts, test identities/reports, and local logs stay ignored; no staging, commit, or push.
**Manual test required:** yes — docs/phase-5-report.md contains exact no-USB sideload, updated-Windows launch, pairing, receive, pause, screen-off, reconnect, Diagnostics, visual and optional boot/echo checks.

### Phase 5 manual-test follow-up — v0.5.1 TLS correction
**Status:** Partial — initial phone pairing failed; fix built, awaiting phone retest.
**Evidence:** The user's Android diagnostics stamped 2026-09-26 01:44 show SSLHandshakeException before confirmation. Read-only inspection found the PC running bin/Debug/ClipSync.exe with older server behavior, not the delivered Release executable. The 0.5.0 Android Keystore policy also omitted DIGEST_NONE, required for Conscrypt's prehashed ECDSA signing. This is a concrete code defect and likely cause, but the old class-only logs cannot establish the full device exception chain.
**Built:**
- Fixed signing authorizations and switched to a stable v2 identity alias without deleting v1 identity, pins, or settings; upgrading needs no uninstall/data clear and uses explicit re-pairing. Added an on-device pre-connect key-authorization/signature self-test. TLS 1.3 and peer pinning remain mandatory.
- Added privacy-safe stage/cause-class diagnostics and corrected generic TLS vs actual pin-mismatch error copy. Fixed stale Android startup version; Android and Windows now report 0.5.1.
- Added a Windows version label and made run-windows.ps1 default to Release, with an explicit warning rather than killing an existing instance.
- Added 12 JVM regression tests plus 2 device-only Keystore tests. All 52 local tests passed (38 Android/Kotlin, 14 Windows). Device tests compiled, not run. APK built and signature verified; lint 0 errors/8 warnings; Windows Release build 0 errors/0 warnings.
**Files added/changed:** Android KeyStoreIdentity.kt, TlsIdentityPolicy.kt, PinnedTrustManager.kt, TlsClipboardClient.kt, ConnectionDiagnostics.kt, ClipboardWatchService.kt, ClipSyncApplication.kt, app/build.gradle.kts; new TLS policy/diagnostics unit tests and KeystoreTlsSigningTest instrumentation source; Windows App.xaml.cs, project version metadata, StatusWindow.xaml/.cs; scripts/run-windows.ps1; docs/phase-5-report.md; LOG.md.
**Design note (if UI):** Retained the palette and status-first layout. Improved diagnostic error copy and added a spaced Windows build identifier so the tested version can be verified visually.
**Known issues:** Hardware pairing success remains unverified. The two device-only tests are not counted as passed. PC process left running untouched; user must exit the old instance and launch the current Release build. No new dependencies or out-of-phase features.
**Manual test required:** yes — install dist/ClipSync-debug-0.5.1.apk as an update, launch Windows showing v0.5.1, explicitly re-pair, then test PC → phone paste. Exact commands and new diagnostic markers are at the top of docs/phase-5-report.md. No staging, commit, or push.

### Phase 5 acceptance — user-approved closure with Phase 6 follow-ups
**Status:** Done — user explicitly approved committing and pushing Phase 5. Final file-list confirmation is pending under RULES.md; no commit or push has occurred yet.
**Manual evidence:** User-supplied 0.5.1 phone diagnostics show Keystore signing success, six-digit pairing confirmation, saved peer trust, pinned reconnect, and multiple PC clipboard writes on the phone. Paste/lock/reboot and full UI quality are not claimed independently verified.
**Deferred to Phase 6 at the user's explicit request:**
- Closing the ClipSync UI must not stop sharing; investigate lifecycle behavior separately from explicitly stopping the service or quitting the Windows tray process.
- Android should keep running in the background and resume after a device restart.
- Attempt sharing while locked where Android permits; if unavailable, restore sharing immediately on unlock. Preserve the manual phone-to-PC read trigger and do not promise silent locked/background clipboard reads.
- Fix the Windows popover's first-open position/size: the user reports an initial top-right/misplaced small panel and correct placement only on a subsequent click.
**Scope:** These are recorded for Phase 6 only; no fixes for them were started in this closure step. Automatic discovery and unrelated Phase 7 work remain deferred.
**Cleanup:** Generated APKs, build output, local test identities, verification logs, and test reports stay ignored. Architecture and plan documents remain unchanged.
