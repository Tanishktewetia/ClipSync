# ClipSync documentation

Start with the [project README](../README.md), [setup guide](../site/docs.html), and Android's offline Guide.

PC → phone is automatic. Phone → PC requires an explicit notification or Quick Settings tile tap. No USB or Developer options are needed.

- [Phase 5](phase-5-report.md)
- [Phase 6](phase-6-report.md)
- [Phase 7: discovery, reconnect, Android guide and website](phase-7-report.md)
- [Phase 7 protocol extension](phase-7-protocol.md)
- [Website preview and publishing instructions](../site/README.md)

Use scripts/build-windows.ps1, scripts/run-windows.ps1 and scripts/build-android.ps1 from the repository root. Current projects use .NET 10, JDK 17 and SDK 34. Sideload using Telegram or scripts/serve-apk.ps1; no developer-mode or ADB setup is part of normal installation/testing.

Android logs: Advanced → Diagnostics → Copy / Share / Save. Windows logs: Open Log Folder in the tray. Include phase/step, versions and network mode; do not include confidential clipboard contents.
