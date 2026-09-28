# ClipSync

<p align="center">
	<img src="site/mark.svg" width="88" height="88" alt="ClipSync mark">
</p>

<h3 align="center">Your clipboard, in sync. Your devices, still yours.</h3>

<p align="center">
	<img src="https://readme-typing-svg.demolab.com?font=JetBrains+Mono&weight=600&size=18&duration=2800&pause=750&color=138A82&center=true&vCenter=true&width=620&lines=Windows+%E2%86%94+Android;Automatic+PC+%E2%86%92+phone;One+deliberate+tap+to+send+back;Private+sync+over+your+local+network" alt="Windows and Android clipboard sync, over your local network">
</p>

<p align="center">
	<img src="https://img.shields.io/badge/Windows-desktop-30343B?logo=windows&logoColor=white" alt="Windows desktop">
	<img src="https://img.shields.io/badge/Android-10%2B-138A82?logo=android&logoColor=white" alt="Android 10 and newer">
	<img src="https://img.shields.io/badge/Transport-TLS%201.3-C18B42" alt="TLS 1.3 transport">
	<img src="https://img.shields.io/badge/Network-local%20only-30343B" alt="Local network only">
</p>

ClipSync keeps the clipboard moving between a Windows PC and an Android phone on the same Wi-Fi or PC hotspot. Pair the devices with a six-digit code, then use your clipboard without an account or a cloud history.

> **Testing build:** the Android APK is debug-signed and the Windows installer is unsigned. These are beta builds for manual testing, not production releases. Android secure sync requires a TLS 1.3-capable device (Android 10+ on the current implementation).

## The flow

| Windows to Android | Android to Windows |
| --- | --- |
| Copy on the PC. Text syncs automatically while connected; paste it on the unlocked phone. | Copy on the phone, then tap **Send clipboard now** in the notification or **Send to PC** in Quick Settings. |
| No phone-side tap for incoming PC text. | Copying alone never sends clipboard content. |

```mermaid
sequenceDiagram
		participant PC as Windows PC
		participant Phone as Android phone
		PC->>Phone: Paired TLS 1.3 connection
		Note over PC,Phone: PC clipboard is delivered automatically
		Phone->>PC: User taps Send clipboard now
		Note over PC,Phone: No account or cloud clipboard history
```

## What it does

- **Pairs explicitly:** compare and confirm a six-digit code before trusting a device.
- **Keeps devices separate:** encrypted paired-device storage, identity pinning, and a deliberate Connect action for each PC.
- **Finds the connection again:** local discovery, reconnect, and a selected-device-only recovery policy.
- **Keeps sending intentional:** Android never silently reads clipboard changes to send them to the PC.
- **Stays local:** no account, cloud clipboard history, USB, root, helper app, Developer options, or keyboard switching.

Clipboard sync is text-only. Images and automatic hotspot management are not included. Android may show its own clipboard privacy indicator; ClipSync cannot suppress operating-system UI.

## Get connected

1. Install the matching Windows and Android builds, then connect both devices to the same private Wi-Fi or the PC hotspot.
2. In the Windows tray app, choose **Pair new device**. In ClipSync on Android, choose **Find & pair my PC**.
3. Compare the six-digit codes. Confirm only when both screens show the same code.
4. Select the paired PC to connect. Copy on Windows and paste on the unlocked phone.
5. To send the other way, copy on Android and tap the notification action or Quick Settings tile.

The Android app includes an onboarding guide for optional permissions and adding the Quick Settings tile. The notification action is an alternative; adding the tile is optional.

## Build from source

**Tooling:** .NET 10 SDK, JDK 17, Android SDK Platform 34, and PowerShell. Node.js is only needed to build or preview the website.

```powershell
# Build the Windows app and Android beta APK
.\scripts\build-windows.ps1
.\scripts\build-android.ps1

# Build the compact Windows installer
.\scripts\build-windows-download.ps1

# Optional: build and preview the website
node site/build.cjs
node site/serve.cjs
```

The Android build creates a debug-signed APK. The compact Windows installer installs per-user and downloads the official .NET 10 Desktop Runtime only if it is missing. Build outputs are written under the locally ignored `dist/` directory. Quit an existing Windows tray instance before launching a replacement.

## Privacy and troubleshooting

Android's enabled foreground service supports background connection recovery. Android requires an explicit user action to read and send phone clipboard text. Incoming text queued while the phone is locked is held in memory; pending text can be lost if the service or process stops. Pause stops sync in both directions.

For diagnostics, use **Advanced → Diagnostics → Share logs / Save to Downloads** on Android or **Open Log Folder** from the Windows tray menu. When reporting an issue, include the app versions, network mode, test step, and expected versus actual result. Do not send private clipboard text.

The animated headline above is decorative and hosted by [readme-typing-svg](https://github.com/DenverCoder1/readme-typing-svg); it does not run code from this repository.

<p align="center">
	<a href="https://github.com/Tanishktewetia/ClipSync/issues">Report a problem</a>
	&nbsp;·&nbsp;
	<a href="site/index.html">Explore the product walkthrough</a>
</p>
