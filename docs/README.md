# ClipSync

Wi-Fi clipboard sync between Windows 11 and Android 13. No cloud, no USB, no taps.

## Quick Start

### Windows

```powershell
# Build
.\scripts\build-windows.ps1

# Run
.\scripts\run-windows.ps1
```

The app lives in the system tray. Right-click the tray icon or click to open the status popover.

### Android

```powershell
# Build (outputs APK to dist/)
.\scripts\build-android.ps1

# Serve APK over local network for sideloading
.\scripts\serve-apk.ps1
```

#### Sideloading

1. Run `.\scripts\build-android.ps1` to produce `dist/ClipSync-debug-<version>.apk`
2. **Option A — Telegram:** Send the APK to Saved Messages, open on phone, install.
3. **Option B — Local HTTP:** Run `.\scripts\serve-apk.ps1`, note the URL, open it in the phone's browser (phone and PC must be on the same network).
4. On first install, allow "Install unknown apps" for the app you're installing from (Telegram, Chrome, Files).

### Wireless ADB (optional, for live logs)

```powershell
.\scripts\adb-wireless.ps1
```

Follow the prompts. Requires Developer Options → Wireless debugging enabled on the phone.

## Logs

- **Windows:** `%APPDATA%\ClipSync\logs\clipsync-YYYYMMDD.log` — or use the "Open log folder" option in the tray menu.
- **Android:** In-app Diagnostics section → Copy logs / Share logs / Save to Downloads.

## Architecture

See [architecture.md](architecture.md) for the full design document.
