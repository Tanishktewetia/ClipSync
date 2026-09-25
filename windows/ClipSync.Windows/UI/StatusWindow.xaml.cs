using System.Diagnostics;
using System.Windows;
using System.Windows.Media.Animation;
using ClipSync.Windows.Logging;

namespace ClipSync.Windows.UI;

/// <summary>
/// Popover-style status window. Anchored near the tray area,
/// auto-hides on deactivation. Shows connection state prominently.
/// </summary>
public partial class StatusWindow : Window
{
    public StatusWindow()
    {
        InitializeComponent();
    }

    /// <summary>
    /// Positions the window near the system tray and shows it with a fade-in.
    /// </summary>
    public void ShowPopover()
    {
        PositionNearTray();
        Show();
        Activate();

        // Subtle fade-in
        var fadeIn = new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(150));
        BeginAnimation(OpacityProperty, fadeIn);
    }

    /// <summary>
    /// Hides the popover with a fade-out.
    /// </summary>
    public void HidePopover()
    {
        var fadeOut = new DoubleAnimation(1, 0, TimeSpan.FromMilliseconds(100));
        fadeOut.Completed += (_, _) => Hide();
        BeginAnimation(OpacityProperty, fadeOut);
    }

    private void PositionNearTray()
    {
        // Position above the taskbar, right-aligned
        var workArea = SystemParameters.WorkArea;
        Left = workArea.Right - ActualWidth - 12;
        if (Left < workArea.Left) Left = workArea.Right - Width - 12;
        Top = workArea.Bottom - Height - 12;
        if (Top < workArea.Top) Top = workArea.Bottom - 400;
    }

    private void Window_Deactivated(object sender, EventArgs e)
    {
        // Auto-hide when clicking elsewhere
        HidePopover();
    }

    private void OpenLogFolder_Click(object sender, RoutedEventArgs e)
    {
        var logDir = FileLogger.Instance.LogDirectory;
        try
        {
            Process.Start("explorer.exe", logDir);
        }
        catch (Exception ex)
        {
            FileLogger.Instance.Error("Failed to open log folder", ex);
        }
    }

    private void Exit_Click(object sender, RoutedEventArgs e)
    {
        FileLogger.Instance.Info("User requested exit via popover");
        System.Windows.Application.Current.Shutdown();
    }
}
