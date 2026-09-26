# ClipSync website

Static landing page, setup guide and real **Windows EXE + Android APK** downloads. No website runtime dependencies, external fonts, analytics, account forms or fake testimonials. Device/Quick Settings visuals are explicitly illustrative, not recordings of the native apps.

## Build and preview

From the repository root:

1. Build the Android 0.7.0 / versionCode 8 debug APK using scripts/build-android.ps1.
2. Run **scripts/build-windows-download.ps1** to publish the matching self-contained, single-file Windows x64 EXE. This requires the .NET 10 SDK and access to the standard .NET runtime packs if not already cached. No new application library is added. It does not sign or run the output.
3. Run **node site/build.cjs**. The packager requires both artifacts, checks Android version metadata, verifies the Windows version/runtime manifest and actual EXE hash, checks PE x64 format, and writes both downloads plus SHA256SUMS.txt and manifest.json.
4. Run **node site/serve.cjs** and open http://127.0.0.1:4177. Do not start a duplicate if the preview is already running. PORT can override the port.

All generated output is under ignored dist/. The Windows file is **ClipSync-0.7.0-win-x64.exe**; the Android file is **ClipSync-debug-0.7.0.apk**. Windows is an unsigned portable beta, not an installer, and includes .NET. Android remains debug-signed. The website describes these limits visibly.

For intentional no-USB sideloading on private Wi-Fi, run **node site/serve.cjs --lan** and visit http://<PC-WiFi-IP>:4177 from the phone. Stop the server after use. It serves only the generated site directory, not source, keys or logs. EXE/APK responses have attachment headers and correct MIME types.

## Motion: explain the actual product

The graphite/oyster/gold direction replaces the earlier blue-button design. Website graphics do not rebrand the native apps or alter their binaries.

Uses UI/UX Pro Max motion.csv choreography/scroll guidance and reduced-motion guidance with native Web Animations + IntersectionObserver. No GSAP dependency is added.

The **26-second loop** has three directly selectable chapters:

1. **PC → phone, automatic:** copy text in Windows, see the note move to Android, then paste without a Receive tap.
2. **Add the tile once:** swipe down twice, open Quick Settings Edit/pencil, find Send to PC, drag into active tiles and tap Done. Adding a shortcut is not itself a clipboard send.
3. **Phone → PC, one tap:** copy in Android, open the existing tile panel, tap Send to PC, then watch the note move back to Windows. Copying alone never sends.

The loop repeats while its graphic is visible. Pause/resume preserves the current time; hidden-tab/offscreen playback suspends. Reduced motion uses immediate chapter snapshots without autoplay, drag or transfer movement. Chapter buttons stay usable. No-JS users still get the tile instructions, product explanation, documentation and both downloads. Synthetic sample text only: no real clipboard API is accessed.

One desktop setup scene remains scroll-linked; small-screen layouts use normal document flow. Selected headings have short reveals, not perpetual unrelated animation.

## Browser verification

Use an existing Playwright development install, not a site dependency. Set PLAYWRIGHT_MODULE to its module path and optionally CHROME_PATH to an installed browser. Then run:

- **node site/verify-motion.cjs**: six desktop/dark/phone/small-phone/tablet/landscape scenarios; both transfer directions, tile edit/drag, deliberate tap, automatic loop rollover, pause, offscreen suspension, reduced-motion chapter selection and no-JS fallback. Captures scene screenshots under dist/phase7-verification/bidirectional/.
- **node site/verify-downloads.cjs**: real browser downloads of EXE and APK (never runs the EXE), SHA-256/manifest verification, MIME/attachment headers, PE x64 validation, internal links/anchors, light/dark responsive checks, FAQ, theme persistence and path-traversal checks. Results under dist/phase7-verification/downloads/.

SITE_URL overrides the localhost test target. Actual native app installation/pairing remains a human acceptance test.

## Publishing — not performed

Upload the contents of dist/site/ to an HTTPS static host that accepts both large binary files. Preserve the downloads paths and serve EXE as application/octet-stream and APK as application/vnd.android.package-archive. Include SHA256SUMS.txt. No public hosting, credentials, domain or auto-publishing workflow has been configured. A local preview is not deployment.

The user explicitly requested the EXE as part of this Phase 7 download-page follow-up. This focused beta packaging does not declare the later production release phase complete. Production signing, installation/update policy, clean-machine testing and public release remain separate work.
