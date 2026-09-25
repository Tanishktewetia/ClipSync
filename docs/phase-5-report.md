# Phase 5 — Android pairing and automatic PC → phone text

Date: 2026-09-26 (local, Asia/Kolkata)
Status: **Phase 5 accepted by the user for commit/push, with lifecycle/lock/unlock and Windows popover issues explicitly deferred to Phase 6. Final pre-commit file-list confirmation pending.**

## User acceptance and Phase 6 follow-ups

The user approved Phase 5 based on the successful pairing/receive path and explicitly deferred the following work to Phase 6:

- Keep sharing active when only the UI is closed; distinguish this from explicitly stopping the service or quitting the tray process.
- Preserve Android background operation and restore service after reboot.
- Support locked-phone sharing where possible; otherwise resume immediately on unlock, without bypassing Android clipboard-read restrictions or replacing the manual phone-to-PC trigger.
- Correct Windows popover first-click positioning/size and its inconsistent second-click placement.

These items remain known issues, not completed Phase 5 features. No Phase 6 implementation was started during closure. User logs demonstrate successful TLS pairing, pin persistence/reconnect and received clipboard writes; they do not prove every original lifecycle/visual acceptance check passed.

## Current retry: v0.5.1 TLS fix

### What the phone test exposed

The supplied phone diagnostics report SSLHandshakeException before pairing. The Windows log for those attempts uses the old listener/pairing messages, and the running process was verified as the repository's **bin/Debug** executable, not the Phase 5 **bin/Release** executable. A code still visible in that old Windows window is not proof the current phone handshake completed.

Separately, the Android 0.5.0 key-generation policy omitted **DIGEST_NONE**. Android's TLS provider signs an already-computed TLS transcript digest using NONEwithECDSA; the old key only authorized SHA-256/384/512 signing. This is a concrete Android Keystore configuration defect and a likely explanation for the reported failure. The old log only recorded the exception class, so the exact on-device causal chain cannot be proven until retesting. The previous Windows EC/TLS tests did not exercise Android Keystore authorization enforcement.

### Fix and upgrade behavior

- Authorize prehashed ECDSA signing while keeping TLS 1.3, certificate verification/pinning, and non-exportable private keys. DIGEST_NONE is a key-operation permission, not an instruction to disable TLS hashing.
- Select a new stable v2 identity alias because installed key authorizations cannot be modified in place. The v1 key and saved settings/pins are not deleted. **Install as an update; do not uninstall or clear app data.** Pair again explicitly so the Windows app can pin the new phone identity.
- Before opening the socket, inspect the key's authorizations and sign/verify a fixed self-test digest. This distinguishes a local signing-key failure from a PC certificate failure.
- Log connection stage and a bounded cause-class chain, never arbitrary exception messages or clipboard text. Generic TLS failures no longer falsely claim a peer-pin mismatch; an actual pin mismatch still has specific advice.
- Replace the stale Android v0.1.0 startup string with BuildConfig.VERSION_NAME. Both platform builds now identify themselves as v0.5.1; Windows displays the version in its header.
- The Windows launch script now defaults to Release and refuses to overwrite or silently compete with an already running ClipSync. It does not terminate the user's process.

### Retest these steps first

1. On the phone, tap **Stop background connection**. Sideload **dist/ClipSync-debug-0.5.1.apk** through Telegram Saved Messages or the existing Wi-Fi APK server. Install over the existing app without deleting data.
2. On Windows, choose **Exit ClipSync** from the tray. Close TestClient too. Run this exact command (not an old Debug shortcut):

~~~powershell
& 'C:\Users\tanis\Desktop\ClipSync\scripts\run-windows.ps1'
~~~

   Alternatively launch the already-built Release executable listed under Artifacts. Expected: the Windows header reads **v0.5.1 · Text only**. If it does not, stop before trying to pair.
3. On the phone, verify **Advanced → Version 0.5.1**. On Windows choose **Pair new device**, then on the phone enter the same PC Wi-Fi address and tap **Pair with PC** (or **Pair again with this PC** if already paired).
4. Expected: the six-digit confirmation dialog appears on the phone and matches the Windows code. Confirm only if all six digits agree. Expected: both devices become Connected.
5. Copy a fresh short text on the PC; paste in a phone note without tapping the tile. If this succeeds, continue the original Phase 5 rapid-copy, Pause, screen-off and background-service tests below.
6. If pairing still fails, immediately use **Advanced → Diagnostics → Share logs** and send the newest PC log. Useful new evidence is the stage sequence, **Keystore TLS signing self-test passed: policy=v2**, and any **Receive connection ended: stage=... causes=...** entry. The app collects these automatically; no debugging setup is required.

### Follow-up verification

- Android debug APK and unit tests: passed. **38 Android JVM/core tests + 14 Windows tests = 52 passing tests.**
- Android lint: 0 errors / 8 existing or documented warnings. Windows Release solution build: 0 warnings / 0 errors.
- Two device-only Keystore tests added and their test APK compiled. **Not executed**: no emulator/test device is connected. They use unique test-only aliases and do not alter production identity/pin state.
- APK versionCode 6, versionName 0.5.1; APK v2 signature verified. SHA-256: 43f66fd5fb0a5492900ab153d47196e42ccac82dc98ee576a07027858d9c653a.
- Local verification output: dist/phase5-verification/0.5.1/. APK size: 15.70 MiB.
- No new library dependencies. No phone-send/discovery/image work. No trust-all fallback, TLS downgrade, uninstall requirement, or debugging requirement.
- No user process was stopped by the agent. Nothing staged, committed, or pushed.

Reproduction commands (same JAVA_HOME/ANDROID_HOME as the build section below):

~~~powershell
Set-Location 'C:\Users\tanis\Desktop\ClipSync\android'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :core:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug --console=plain
& 'C:\Users\tanis\Desktop\ClipSync\scripts\build-android.ps1'
dotnet build 'C:\Users\tanis\Desktop\ClipSync\windows\ClipSync.sln' -c Release
dotnet test 'C:\Users\tanis\Desktop\ClipSync\windows\ClipSync.sln' -c Release --no-build
~~~

**Design note:** Existing screen layout and palette are unchanged. Error copy now distinguishes local-key failures, generic TLS setup failures and real pin mismatches; Windows has a small spaced build-version label to make stale executables apparent.

**Technical references:** Android KeyGenParameterSpec.Builder.setDigests documentation (https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder?hl=en#setDigests(java.lang.String...)); Conscrypt CryptoUpcalls.rawSignDigestWithPrivateKey (https://github.com/google/conscrypt/blob/master/common/src/main/java/org/conscrypt/CryptoUpcalls.java).

---

## What was built

- Android Keystore EC P-256 identity for TLS 1.3 client authentication. No private-key export. Removed the Phase 1 trust-all ConnectionTestClient.
- Exact SHA-256 certificate pin validation for normal connections. Only an explicitly requested pairing attempt permits provisional trust. The phone independently derives the six-digit code from both TLS identities using the Phase 4 Windows algorithm; it does not blindly accept the server's code. Clipboard application stays gated until confirmation and READY.
- Peer fingerprint encrypted/authenticated with AES-256-GCM using an Android Keystore key, with backup/device-transfer exclusions. Pin corruption fails closed. Pairing cancellation or a rejected code does not replace existing trust.
- Existing Phase 2 service upgraded in place to a connectedDevice foreground service with a quiet low-priority notification, Wi-Fi lock while connecting/connected, persisted Pause, and a one-time battery-exemption request. Existing enabled/BOOT_COMPLETED behavior is retained.
- Manual IPv4 connection on Wi-Fi only, port 48653. No mobile-data routing, address scanning, discovery, or reconnect backoff.
- Automatic sensitive-flagged clipboard writes with the Kotlin core state machine and a bounded hash echo guard. Successful writes alone update the guard. Consecutive duplicates are suppressed; A → B → A remains valid. The existing manual reader recognizes our writes and remains local-only.
- Final Material 3 screen: Connected / Waiting / Paused / Error status card, Pause toggle, contextual connection action, six-digit pairing dialog, collapsed Advanced with IP and Diagnostics. Copy/Share/View logs remain available; Save to Downloads now uses scoped storage on Android 10+ and the system document picker on older devices.
- Two necessary Windows integration fixes: listen on LAN rather than loopback only, and allow a newly confirmed phone to replace the Phase 4 TestClient pin during an explicit pairing window. Unknown certificates are rejected during normal TLS handshakes; pairing is bounded and only one confirmed session is active. Existing CSP1 framing/bootstrap remains compatible.

**Scope boundary:** no phone clipboard sending, auto-detection, polling, discovery, new image pipeline, or helper-app setup. Phase 2's Quick Settings reader remains local-only. The service notification has no send action. Notification/tile sending remains Phase 6.

## Design note

The screen retains ClipSync's indigo (#4F46E5 / #818CF8), teal, and neutral surfaces, using the existing system light/dark theme and type scale. A large rounded status card is the only visual focal point; green, amber, violet, and red states pair color with an icon and plain-language text. Pause remains directly accessible, while technical controls live in a collapsed Advanced section. Pairing uses six evenly spaced digit cells with a combined screen-reader label and an explicit match confirmation. Status changes use a restrained color transition. Dark-mode filled-button foregrounds were corrected for contrast; native vector clipboard glyphs identify the launcher and quiet notification. Visual approval on the physical phone is still required.

## Verification performed

| Check | Result |
| --- | --- |
| Android debug APK build | Passed |
| Kotlin core unit tests | 16 passed (4 existing + 12 receive/frame tests) |
| Android app JVM tests | 22 passed (pairing, pinning, authenticated storage, bootstrap, IP validation, TLS key policy, privacy-safe diagnostics) |
| Windows core tests | 5 passed |
| Windows TLS integration tests | 9 passed |
| Windows Release solution build | Passed, 0 warnings / 0 errors |
| Android lint | Passed, 0 errors / 8 warnings |
| APK signature verification | Passed, APK Signature Scheme v2 |
| Manifest | com.clipsync.android, versionCode 6, versionName 0.5.1, minSdk 24, targetSdk 34 |
| Whitespace validation | git diff --check passed |
| Real phone / Wi-Fi / system overlay / visual testing | Not performed; human test required |

Total: **52 passing tests**. Windows tests use isolated identities and ephemeral listening ports; they neither alter your saved PC pairing nor interact with the real Windows clipboard. They cover EC client authentication over real TLS 1.3, code agreement, persisted reconnect, wrong-code rejection, replacement/revocation, expiration, framing/Unicode, and no clipboard delivery before confirmation.

Lint warnings are four existing dependency-update notices, two existing obsolete SDK checks/resources, the explicit battery-exemption request, and the deliberately custom certificate-pin trust manager. No dependency versions were upgraded and no new library dependency was added. The Windows test project reuses the existing test package versions.

The first Windows Debug build hit the running Phase 4 executable's file lock. The verified Windows build is therefore **Release**, and the existing app was left running. The malformed old .sln file was regenerated as a real solution and both solution formats now include the integration tests. The Gradle wrapper was fixed to handle a missing OS environment variable without reporting failure after a successful build.

## Artifacts

- APK: C:\Users\tanis\Desktop\ClipSync\dist\ClipSync-debug-0.5.1.apk (15.70 MiB)
- APK SHA-256: 43f66fd5fb0a5492900ab153d47196e42ccac82dc98ee576a07027858d9c653a
- Updated Windows executable: C:\Users\tanis\Desktop\ClipSync\windows\ClipSync.Windows\bin\Release\net10.0-windows10.0.19041.0\ClipSync.exe
- Local verification logs and summary: C:\Users\tanis\Desktop\ClipSync\dist\phase5-verification

The APK is a debug-signed sideload build, not the Phase 11 release package. Keep the Windows executable in its build directory alongside its DLL/config files.

## Exact build commands (PowerShell)

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:ANDROID_HOME = 'C:\Users\tanis\AppData\Local\Android\Sdk'
Set-Location 'C:\Users\tanis\Desktop\ClipSync\android'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :core:testDebugUnitTest :app:lintDebug --console=plain
& 'C:\Users\tanis\Desktop\ClipSync\scripts\build-android.ps1'
dotnet build 'C:\Users\tanis\Desktop\ClipSync\windows\ClipSync.sln' -c Release
dotnet test 'C:\Users\tanis\Desktop\ClipSync\windows\ClipSync.sln' -c Release --no-build
```

## Exact manual test steps — no USB or Developer options

1. **Replace the running Windows instance.** Exit ClipSync through its tray menu and close the Phase 4 TestClient. Launch the updated executable below. Keep the saved identity/pin files; do not delete them. Allow ClipSync on **Private networks** if Windows Firewall asks. Both devices must be on the same Wi-Fi or the phone must join the laptop's hotspot.

   ```powershell
   & 'C:\Users\tanis\Desktop\ClipSync\windows\ClipSync.Windows\bin\Release\net10.0-windows10.0.19041.0\ClipSync.exe'
   ```

   Expected: Windows starts in Waiting, ready for explicit pairing. The old loopback-only Phase 4 process must not still be running.

2. **Sideload the APK.** Send ClipSync-debug-0.5.1.apk through Telegram Saved Messages, then download/open it on the Motorola. Install as an update without uninstalling first. Alternatively run the existing server script and open the printed Wi-Fi URL in the phone browser:

   ```powershell
   & 'C:\Users\tanis\Desktop\ClipSync\scripts\serve-apk.ps1'
   ```

   Expected: app installs without a cable. Only the sideloading app's normal Install unknown apps permission may be needed. Stop the APK server with Ctrl+C after downloading. If HTTP serving is blocked by Windows URL/firewall permissions, use Telegram instead; no debugging setup is required.

3. **Inspect the initial screen.** Open ClipSync. Expected: a status-first screen, Pause control, and Advanced initially collapsed. Expand Advanced and confirm version 0.5.1. It should not present the Phase 1 connection-test form or claim phone → PC sending is active.

4. **Start pairing.** In Windows, choose Pair new device. On the phone, expand Advanced, enter the PC's Wi-Fi IPv4 address and tap Pair with PC. Find the address in Windows ipconfig under the active Wi-Fi adapter; for the standard Windows mobile hotspot use 192.168.137.1. Allow notifications and the one-time battery-optimization exemption when prompted. If setup prompts consume the two-minute pairing window, repeat Pair new device on Windows and retry on the phone.

5. **Check confirmation safety.** Both apps must show the same six digits. On a first pairing, cancel once: the phone must not become Connected or receive clipboard text. Start pairing again on both devices; confirm Codes match · Pair only when all six digits match. Expected: both show Connected. The phone now replaces the prior TestClient pairing. A mismatch is a failure to report, not something to bypass.

6. **Automatic receive.** Leave ClipSync and open a notes app or message draft on the phone. Copy ordinary test text on Windows; paste on the phone without tapping ClipSync, a notification action, or a tile. Expected: exact text arrives, including a separate test with a newline and non-ASCII characters. ClipSync itself shows no toast/snackbar/copy notification. Android may display its own redacted clipboard confirmation; the actual clipboard contents must not be exposed in its preview.

7. **Rapid copies and duplicates.** Copy five distinct short texts rapidly on the PC, then paste on the phone. Expected: the final text wins. Windows may coalesce rapid events, so the receive count need not rise by exactly five. Copy the same final text again: there must be no echo loop or runaway receive count.

8. **Pause.** Enable Pause receiving on the phone, copy new text on the PC, and paste on the phone. Expected: the old phone clipboard remains. Turn Pause off and make a fresh PC copy: it should arrive. Paused copies are not queued/replayed. Also check PC Pause/Resume once: paused Windows copies should not forward; resuming should show Connected when the phone remains attached.

9. **Screen off.** Keep the connection active, lock the phone for two minutes, then copy new text on the PC while the phone is still locked. Unlock and paste without reopening ClipSync. Expected: the new text is available. Record whether Android displays a redacted confirmation on waking.

10. **Close the UI, keep the service.** Swipe ClipSync out of Recents (do not Force stop it in Android settings). Expected: the quiet ongoing notification remains and another PC copy still pastes on the phone. Its status is low priority and silent; there is no phone-send action in this phase.

11. **Persistence and manual reconnect.** In Advanced choose Stop background connection: the service notification should disappear. Tap Reconnect (or Advanced → Connect): it should reconnect without another six-digit confirmation or automatic battery prompt. Exit/relaunch the updated Windows app and manually reconnect from the phone again. Expected: the saved pins still work. Automatic retry/discovery is explicitly deferred to Phase 7.

12. **Echo guard on the existing tile (optional).** If the Phase 2 Send to PC tile is already installed, receive PC text and tap that tile while the text remains on the phone clipboard. Expected: nothing is sent to Windows; Diagnostics records “Manual read ignored: our own received clipboard.” The tile still only reads locally in this phase. Copying on the phone alone must never trigger a notification or network send.

13. **Diagnostics and appearance.** Open Advanced → Diagnostics. Verify View recent logs, Copy logs, Share logs, and Save to Downloads. Share logs through Telegram; verify the saved text file in Downloads. Switch Android light/dark mode and increase font size: status, pairing digits, actions, and IP errors should remain readable. Waiting, Connected, Paused, and Error must be distinct by text/icon, not color alone. For Error, try an unused valid Wi-Fi IP and then restore the correct IP and reconnect.

14. **Retained reboot behavior (optional regression check).** With the service enabled and the PC running, reboot/unlock the phone. Expected: the service resumes and makes one connection attempt to the saved IP. Stop the service in ClipSync, then reboot again: it should stay stopped. Do not use Force stop as a proxy for closing the UI; Android deliberately treats it differently.

## Known limits / not done

- The previously discussed overlay expectation is resolved by your approval to continue: EXTRA_IS_SENSITIVE hides the content preview, not necessarily Android's visual confirmation. No claim of zero system UI is made. No architecture document was silently rewritten.
- Physical Motorola behavior, notifications, Wi-Fi lock/screen-off behavior, battery exemption, reboot, and actual light/dark rendering remain unverified. No emulator/AVD is configured here.
- Secure sync requires a platform TLS 1.3 provider (normally Android 10+). The APK retains minSdk 24 for the existing reader/UI, but Android 7–9 do not get an insecure TLS fallback. The target Motorola is Android 13.
- The existing Phase 3/4 **CSP1 protocol has a 1 MiB UTF-8 text payload cap**, not the larger limit in the aspirational architecture. This phase preserves the working Windows wire format rather than silently migrating it. Oversize PC clips are skipped and logged without their contents.
- No auto-discovery, periodic PING/PONG, retry/backoff or full connection-drop recovery loop was added; use manual Reconnect. One saved-IP attempt on enabled service/process/boot startup is retained.
- No phone clipboard network sends or images. The Phase 2 trigger remains manual and local-only until Phase 6; automatic copy detection was not reintroduced.
- Battery exemption is requested once. If you decline it, use Advanced → Battery optimization settings to grant it manually before the screen-off test.

## If something fails, send

1. Phase 5 and the exact step number, expected vs actual behavior, and whether using router Wi-Fi or the laptop hotspot.
2. Phone: Advanced → Diagnostics → Share logs (Copy logs/Save to Downloads are alternatives).
3. PC: the newest file under %APPDATA%\ClipSync\logs, accessible with Open log folder in the tray UI.
4. For a visual/pairing problem, a screenshot with unrelated private material hidden. For an install failure, its exact Android error message. Do not send private clipboard contents, key files, or debug keystores. No ADB or Developer options are necessary.

## Files and cleanup

- Android app: build metadata/manifest; new security, store, transport, runtime, clipboard-writer files; upgraded existing foreground service and manual-reader echo check; replacement main Activity/screen; color-role contrast fixes; vector glyphs and backup exclusions; pairing/security unit tests. Removed ConnectionTestClient.kt.
- Kotlin core: bounded receive/hash-guard adapter, bounded frame buffering, and receive/frame regression tests.
- Windows: LAN/authenticated pairing/session integration changes, isolated CertificateStore test directory support, pairing copy and Pause status correction, new real-TLS test project, and corrected .sln/.slnx solution membership.
- Tooling/docs: Gradle wrapper quoting fix; .gitignore TestResults coverage; this report and appended LOG.md completion entry.

Cleaned up: APKs, binaries, intermediates, Gradle cache, test reports, generated test identities and verification logs remain under ignored build/bin/obj/TestResults/dist paths. No private keys or clipboard contents were added to source control. No staging, commit, or push has occurred.

User approved Phase 5 closure; awaiting confirmation of the exact staging list required by RULES.md.
