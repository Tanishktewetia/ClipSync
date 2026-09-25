using System.Threading;
using System.Windows;
using ClipSync.Windows.UI;
using ClipSync.Windows.Logging;
using ClipSync.Windows.Theme;
using ClipSync.Windows.Transport;

namespace ClipSync.Windows;

/// <summary>
/// Application entry point. Runs as a tray app with no main window.
/// Single instance enforced via a named mutex.
/// </summary>
public partial class App : System.Windows.Application
{
    private Mutex? _mutex;
    private TrayIconManager? _trayIcon;
    private TlsTestServer? _testServer;

    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        // Single instance check
        _mutex = new Mutex(true, "ClipSync-SingleInstance-48653", out bool createdNew);
        if (!createdNew)
        {
            System.Windows.MessageBox.Show("ClipSync is already running.", "ClipSync",
                MessageBoxButton.OK, MessageBoxImage.Information);
            Shutdown();
            return;
        }

        FileLogger.Instance.Info($"ClipSync v{ClipSync.Core.ClipSyncInfo.Version} starting");

        // Initialize theme (follows OS dark/light mode)
        ThemeManager.Initialize();

        // Create tray icon — the app lives here
        _trayIcon = new TrayIconManager();
        _testServer = new TlsTestServer();
        _testServer.Start();

        FileLogger.Instance.Info("ClipSync started successfully");
    }

    protected override void OnExit(ExitEventArgs e)
    {
        FileLogger.Instance.Info("ClipSync shutting down");
        ThemeManager.Shutdown();
        _testServer?.Dispose();
        _trayIcon?.Dispose();
        _mutex?.ReleaseMutex();
        _mutex?.Dispose();
        FileLogger.Instance.Dispose();
        base.OnExit(e);
    }
}


