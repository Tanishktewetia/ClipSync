# Phase 6 — Manual phone → PC sends and lifecycle follow-ups

**Date:** 2026-09-26 (Asia/Kolkata, verified on the build machine)
**Status:** Done — the user reports successful hardware testing and approves Phase 6 for commit/push. Final staging-list confirmation is pending under RULES.md; no Phase 6 commit/push yet.
**Previous phase:** Phase 5 was committed and pushed to origin/main as edf9060725cea37295e8ee0456f7661c140c8211 after the user confirmed the exact staging manifest.

## Human acceptance

On 2026-09-26 the user reported testing Phase 6 and that everything was working well, then approved committing and pushing it. This updates the earlier pending-test status; hardware success is human-reported, not independently verified by the agent. The detailed manual steps and implementation limitations below remain as reproducible reference material.

## Scope and decisions

This phase implements the planned manual phone → PC path and the additional work the user explicitly deferred from Phase 5: UI-independent sharing, Android background/reboot recovery, locked/unlocked behavior, and the Windows popover's first-open positioning/size.

The Phase 2 architectural finding remains in force: **there is no automatic phone clipboard detection or polling.** The existing Quick Settings reader is now connected to the real network. Phase 6 also adds the explicitly requested **Send clipboard now** notification action; Phase 2's quiet no-action notification was the proof-stage behavior, not an automatic-copy trigger. The notification body opens ClipSync; its Send action reads/sends. No architecture.md or plan.md edits were made.

The lifecycle request necessarily advances a narrow slice of connection recovery: the service retries **only the saved manual IP**, on Wi-Fi return, recoverable transport failures, service restart, and unlock. It does not discover any address. mDNS/hotspot probing, periodic PING/PONG, exponential backoff, and full newest-clip reconciliation remain Phase 7.

## Built

### Manual sending

- Both the persistent notification's **Send clipboard now** action and the existing **Send to PC** Quick Settings tile launch the same excluded transparent task.
- The reader waits for an actually focused and resumed window, checks the phone is unlocked, reads plain text once, queues it without waiting for network I/O, and closes its temporary task. It never opens the main app task or reads content URIs/images.
- External sensitive-flagged clips are skipped. Received ClipSync writes are recognized by hash and suppressed as echoes even though they carry the sensitive flag. No real clipboard contents are logged.
- The Kotlin core's state/scheduler/echo machinery now supports manual outbound text. Duplicate queued, in-flight and completed sends are suppressed; only the latest queued manual clip is retained. Paused/disconnected reads do not create a hidden persistent outbox. A failed send can be explicitly retried after reconnect.
- Outbound text uses the same pinned TLS 1.3 session and existing CSP1 frame format. Writes are serialized and a stalled send closes the connection after five seconds. No phone text is sent before TLS/pairing completes.
- Windows receives text through its existing server path; clipboard writes now retry transient clipboard locks and mark the echo hash only on success. The watcher keeps a hash rather than a retained text snapshot. Incoming-apply logs contain type/length/six-character hash only.

### Background, reboot, lock and unlock

- The foreground service owns both network workers and UI state. Closing/swiping the main Activity does not stop the service, clear the enabled preference, or cancel the connection. The manifest explicitly sets stopWithTask=false. Removing the UI task only logs the lifecycle event.
- Enabled service state is saved synchronously. BOOT_COMPLETED and MY_PACKAGE_REPLACED request the service after credential-protected storage becomes available. Startup denial is caught and logged, not allowed to crash the receiver.
- The saved, paired PC address is retried on available Wi-Fi and recoverable socket/connect failures, with a fixed five-second retry interval. Pins, malformed frames, local key failures, and provisional pairing failures do not create insecure retry loops. Stop background connection cancels workers/callbacks and disables reboot restart.
- **While the phone is locked, PC text is deferred deliberately**: at most one latest clip stays in process memory, no clipboard write or read is attempted on the lock screen. On unlock, the accepted clip is applied and the saved-IP connection is refreshed so a half-open sleep-era socket does not leave the app stuck. A failed deferred write retains the latest clip for the next retry. Pause, explicit Stop, or re-pairing clears the deferred slot.
- No secure-lock bypass, root, Accessibility, helper service, Developer options, or clipboard polling was introduced. A manual send from the tile/notification still requires normal device unlock.

### Windows popover

- Corrected the first-open bug: the previous code subtracted Window.Height before SizeToContent resolved it, allowing an undefined (NaN) height to influence placement. The panel now lays out first, then anchors from its measured size.
- Placement uses the monitor under the pointer, its working area, and the current WPF device transform; it repeats once after a DPI boundary and clamps placement for smaller screens. Content growth repositions the panel.
- Old fade-out completions cannot hide a newly reopened panel. Tray-click/Show Status consistently shows rather than racing a toggle against deactivation.
- The existing panel is slightly roomier (400 logical pixels, bounded by the work area), with a clear hide button, readable version/status/pairing hierarchy, and a scroll fallback. Hide/Alt+F4 keeps the tray app running. **Quit ClipSync (stop sharing)** is explicit.
- Windows socket broadcasting is off the WPF dispatcher, with a bounded latest-only queue and a socket write deadline, so a slow phone is not allowed to indefinitely freeze the panel. A duplicate app instance no longer tries to release an unowned mutex or open the live instance's log.

## Design note

The Android layout remains the Phase 5 Material 3 status-first screen and indigo/teal identity: copy now explains automatic PC receiving versus manual phone sending, Pause applies to both directions, and optional tile setup stays in Advanced rather than becoming another screen. The Windows panel retains the same dark/light palette and tray-first form, but has a less cramped 400-pixel layout, a clearly separated version line, visible hide control, and unambiguous quit wording. State color and icon are paired with text; motion remains a short fade and honors Windows animation settings. Eight offscreen renders were generated; light/error and dark/connected previews were visually inspected. Actual desktop placement and Android rendering still require manual approval.

## Verification

| Check | Result |
| --- | --- |
| Android app unit tests | 29 passed |
| Kotlin core tests | 40 passed |
| Windows core tests | 5 passed |
| Windows TLS/UI tests | 21 passed |
| **Total** | **95 passed, zero failed** |
| Android debug + instrumentation APK compilation | Passed |
| Android lint | 0 errors / 8 warnings |
| Windows Release build | 0 errors / 0 warnings |
| APK signature | Verified, v2 signature |
| Offscreen Windows panels | Eight rendered: four states × two themes |
| PowerShell launcher parse / artifacts-path support | Verified |
| Physical Android / real notifications / reboot / screen lock / tray-click placement | Not tested by the agent |

New regression coverage includes sensitive skips, own-write echoes, ten alternating bidirectional TLS exchanges, fragmented/combined phone frames, queued/in-flight dedup, paused/disconnected sends, remote changes during a send, latest-only locked receiving, failed deferred write retry, explicit-stop clearing, trusted-IP recovery policy, initial NaN-height placement, multi-monitor/scale-size bounds and stale-fade invalidation.

The two existing device-only Android Keystore tests compiled but were **not executed** and are not counted as passed. No emulator/connected test phone is available. The Windows render test constructs an isolated UI with synthetic data; it does not start the application/server or touch the real Windows clipboard. TLS tests use isolated identities and ephemeral ports.

Lint warnings: four dependency-update notices, explicit battery-exemption request, deliberate custom certificate pin manager, an existing redundant API-24 resource folder and the deliberate synchronous enabled-preference write. The guarded API 24–33 TileService launch is annotated because its PendingIntent overload exists only on API 34+. No library dependency was added or upgraded.

## Artifacts and exact commands

- APK: **C:\Users\tanis\Desktop\ClipSync\dist\ClipSync-debug-0.6.0.apk** (15.76 MiB, versionCode 7)
- SHA-256: 08543d7f4654acf1d32ac2f337c4dc9e5b323c9cf73b41d2c60b789a62a83df4
- Windows executable: **C:\Users\tanis\Desktop\ClipSync\dist\windows-current\bin\ClipSync.Windows\release\ClipSync.exe**
- Verification logs/summary: **C:\Users\tanis\Desktop\ClipSync\dist\phase6-verification**
- Panel renders: **C:\Users\tanis\Desktop\ClipSync\dist\phase6-verification\visual**

The current Phase 5 Release process was left running. To avoid its file locks and accidentally launching an old build, Phase 6 Windows outputs are isolated under dist/windows-current. The launch script now builds/runs that output location and refuses to compete with an existing instance. Keep the executable together with its DLL/runtime files; it is not yet a standalone release package.

~~~powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:ANDROID_HOME = 'C:\Users\tanis\AppData\Local\Android\Sdk'
Set-Location 'C:\Users\tanis\Desktop\ClipSync\android'
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :core:testDebugUnitTest :app:lintDebug --console=plain
& 'C:\Users\tanis\Desktop\ClipSync\scripts\build-android.ps1'
dotnet build 'C:\Users\tanis\Desktop\ClipSync\windows\ClipSync.sln' -c Release --artifacts-path 'C:\Users\tanis\Desktop\ClipSync\dist\windows-current'
dotnet test 'C:\Users\tanis\Desktop\ClipSync\windows\ClipSync.sln' -c Release --no-build --artifacts-path 'C:\Users\tanis\Desktop\ClipSync\dist\windows-current'
# Exit the old Windows ClipSync through its tray first, then:
& 'C:\Users\tanis\Desktop\ClipSync\scripts\run-windows.ps1'
~~~

## Exact manual tests — no USB or debugging setup

1. **Install and launch current builds.** Send ClipSync-debug-0.6.0.apk through Telegram Saved Messages and install over 0.5.1 (do not uninstall or clear data). Alternatively run scripts/serve-apk.ps1 and use the phone browser's printed Wi-Fi URL, then Ctrl+C to stop serving. On Windows, quit the old tray instance and close TestClient; run scripts/run-windows.ps1 using the absolute command above. Verify **v0.6.0 on both platforms**. The v2 phone identity and peer pin are unchanged, so no new pairing should be needed. If stopped, tap Connect on the phone using the saved IP; keep both devices on the same Wi-Fi.

2. **Windows first-click position.** With Windows ClipSync newly launched, click its tray icon once. Expected: a fully sized panel appears near the bottom-right working area on that monitor, not at the top-right and not as a tiny sliver. Click outside, reopen ten times, and use the tray's Show Status too. If you have another monitor or scaling setting, repeat there. Expected: no second click needed to correct the position and no old fade hides the newly opened panel.

3. **Hide is not Quit.** Use the panel's top-right hide button or Alt+F4, then copy text on the PC. Expected: the tray app remains and phone paste still works. Only **Quit ClipSync (stop sharing)** should stop the Windows process/listener. Do not use Task Manager End task as a UI-close test.

4. **Notification-tap phone → PC.** Leave ClipSync in the background on the phone and copy a harmless short test text in Chrome or Notes. Open the notification shade and tap **Send clipboard now** in the existing ClipSync notification (expand it if Android hides actions). Expected: no main ClipSync screen appears; return to the source app promptly, then paste on Windows. The text should match. Repeat ten times with distinct text, including a newline/non-ASCII test. Do not send private passwords for this test.

5. **Quick Settings alternative.** If already installed, use the existing Send to PC tile. Otherwise use Advanced → Add Send to PC tile, or swipe down twice → Edit/pencil → drag Send to PC into active tiles. Copy fresh phone text, tap the tile, then paste on Windows. Expected: same result, no main-task reopening. Copying alone must never send or spawn a copy-triggered notification.

6. **Duplicates and echo.** Tap Send twice without changing the phone clipboard. Expected: one send, then duplicate feedback; no loop. Next copy text on the PC, let it arrive on the phone, and tap Send there without changing it. Expected: the received clip is recognized as an echo and not sent back. Diagnostics should show QUEUED/DUPLICATE/ECHO metadata, not copied contents. There must still be only one quiet persistent service notification.

7. **Sensitive filtering (optional if a suitable test source exists).** Use a source that marks harmless dummy text sensitive, then tap Send. Expected: sensitive-skipped feedback and no Windows clipboard change. Do not use an actual secret. If no test source exists, report this as not manually exercised; automated policy tests cover it.

8. **Pause both directions.** Enable Pause syncing on the phone. Copy on the PC and attempt a manual phone send; neither should replace the other clipboard. Resume and make fresh copies; both paths should work. Repeat Windows Pause/Resume to confirm Windows does not apply/forward text while paused. Previously paused text is not queued for surprise replay.

9. **Phone UI dismissal/background.** Swipe the main ClipSync screen out of Recents, without Force stop. Expected: the ongoing notification remains, PC copies still arrive, and notification/tile manual sending works. Share the log if dismissing the main UI stops the service. Android's Force stop or system Stop-app control is deliberately different and must not be used for this check.

10. **Lock then unlock.** Keep the connection active and lock the phone for at least two minutes. Copy several distinct short texts on the PC while it is locked. Expected: no clipboard activity on the lock screen; the service keeps only the latest received text in memory. Unlock and paste: the latest retained text should be applied without opening ClipSync. The connection is refreshed automatically. Then immediately make a fresh PC copy and verify it arrives without tapping Reconnect. Logs should include the latest-only defer/apply/unlock recovery events. Socket reconnection may take a short network-dependent interval; no instantaneous timing guarantee is claimed.

11. **Phone reboot.** With the foreground connection enabled, reboot the phone, unlock it once, and let it rejoin Wi-Fi while the PC app is running. Expected: the notification and saved-PC connection return without opening ClipSync or pairing again; a fresh PC copy pastes on the phone. Identity/settings are credential-protected, so recovery before the first unlock after reboot is not promised. If Wi-Fi is late, the callback/fixed retry should restore the connection. Keep the existing battery exemption enabled.

12. **Explicit Stop really stops.** Choose Advanced → Stop background connection. Expected: notification disappears and no retry or reboot resurrection occurs. Restart the phone once if desired to verify it stays disabled. Start Connect in ClipSync to enable it again. Merely hiding/swiping the screen must not act like Stop.

13. **Recoverable link loss.** With a saved paired IP, briefly toggle phone Wi-Fi off/on or restart the Windows app at the same IP. Expected: recoverable socket/connection errors retry that address without new pairing. While disconnected, a manual send should clearly say it was not sent; once Connected, tap Send again. No phone outbox is replayed automatically. Address changes still require editing Advanced; discovery is not implemented.

14. **Diagnostics and appearance.** Check Copy/Share/Save to Downloads and light/dark mode. Make sure the two-way/manual-trigger instructions and paused/error states are readable. Report notification-action flash, main-task reopening, clipped text or misplaced windows with the exact step and a screenshot with unrelated private material hidden.

## Known limitations and non-goals

- Actual device/OEM behavior is unverified by the agent. A foreground service, battery exemption and boot receiver are not a promise to bypass Force stop, manufacturer task killers or all power restrictions. Startup denials are logged for diagnosis.
- Locked PC → phone updates are **deferred**, not written while locked. At most one latest accepted clip is held in RAM. If the OS kills the process, it is lost by design. PC copies made while the link is disconnected are not guaranteed to replay; full reconnect reconciliation remains future work.
- Phone clipboard reads remain explicit and unlocked. The lock-screen notification/tile must use normal authentication; no silent background reads are attempted.
- The existing CSP1 text cap is **1 MiB UTF-8**. No wire-protocol migration, image sending, file transfer or discovery was added. Periodic heartbeat, full Lamport metadata exchange/ACK retry and newest-copy reconnect reconciliation are not introduced by this phase.
- “Clipboard sent” records a successful TLS stream write, not a durable application-level delivery receipt. Confirm by Windows paste or its “Phone clipboard applied” log. A failed platform clipboard write is logged; no private content is included.
- The approved sensitive-preview limitation remains: ClipDescription.EXTRA_IS_SENSITIVE redacts Android's clipboard preview, not necessarily the entire OS confirmation overlay.
- Manual sending while disconnected does not retain data for later sending. Tap again after Connected. A new remote update can supersede a still-queued local manual clip; no infinite history is stored.
- No fresh installation/pairing is required for an existing successful 0.5.1 setup. Secure sync still requires the platform TLS 1.3 provider; no downgrade is added for Android 7–9 despite retained minSdk 24 for app compatibility.

## If something fails, send

- **Phase 6, step number, expected vs actual**, whether the phone was locked/backgrounded, and router vs hotspot.
- Phone: **Advanced → Diagnostics → Share logs** (or Copy/Save logs).
- PC: newest log under **%APPDATA%\ClipSync\logs**, accessible from Open log folder.
- For a tray/UI problem: a screenshot and monitor/scaling details. For a reboot failure: whether the phone had been unlocked and Wi-Fi rejoined, and whether the app was force-stopped beforehand.

No USB, ADB, Developer options, private keys or actual clipboard secrets are needed.

## Files and cleanup

Changed/new implementation files cover the core manual-send/defer logic; Android reader, tile, service, lifecycle policy, runtime, existing screen/activity and manifest; Windows clipboard retries, transport worker queue, panel geometry/visibility and existing UI; tests; version metadata; launcher; reports/LOG.

Cleaned up: APKs, binary outputs, test reports, local test identities, render PNGs and verification logs remain in ignored build/bin/obj/TestResults/dist locations. No new dependency, secret/key artifact or clipboard history file was added. The Phase 5 commit is already on origin/main; **none of these Phase 6 changes is staged, committed or pushed**.

User approved Phase 6 as successful. Awaiting confirmation of the exact staging list, then commit/push to origin/main.
