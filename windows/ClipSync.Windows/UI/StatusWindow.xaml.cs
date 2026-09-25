using System.Diagnostics;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Animation; using System.Windows.Threading; using MediaBrush = System.Windows.Media.Brush;
using ClipSync.Windows.Logging;

namespace ClipSync.Windows.UI;

public partial class StatusWindow : Window
{
    private readonly Action _pair;
    private readonly Func<bool> _pause;
    private bool _suppressDeactivation;
    private DispatcherTimer? _deactivationGuard;

    public StatusWindow(Action pair, Func<bool> pause)
    {
        _pair = pair;
        _pause = pause;
        InitializeComponent();
        ShowActivated = true;
    }

    public void SetStatus(string state, string detail)
    {
        StatusText.Text = state;
        StatusDetail.Text = detail;
        var (icon, brush) = state switch
        {
            "Connected" => ("●", "ConnectedBrush"),
            "Paused" => ("Ⅱ", "PausedBrush"),
            "Error" => ("!", "ErrorBrush"),
            _ => ("◌", "WaitingBrush")
        };
        StatusIcon.Text = icon;
        StatusIcon.Foreground = (MediaBrush)FindResource(brush);
        PauseButton.Content = state == "Paused" ? "Resume syncing" : "Pause syncing";
    }

    public void SetPairCode(string code) => PairCodeText.Text = "Your code: " + code;

    public void TogglePopover()
    {
        if (IsVisible)
        {
            HidePopover();
            return;
        }
        ShowPopover();
    }

    public void ShowPopover()
    {
        if (IsVisible)
        {
            Activate();
            return;
        }

        _suppressDeactivation = true;
        _deactivationGuard?.Stop();
        PositionNearTray();
        Opacity = 0;
        Show();
        Activate();
        Focus();
        BeginAnimation(OpacityProperty, new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(150)));

        _deactivationGuard = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(800) };
        _deactivationGuard.Tick += ClearDeactivationGuard;
        _deactivationGuard.Start();
    }

    private void ClearDeactivationGuard(object? sender, EventArgs e)
    {
        _deactivationGuard?.Stop();
        _deactivationGuard = null;
        _suppressDeactivation = false;
    }

    public void HidePopover()
    {
        if (!IsVisible) return;
        _deactivationGuard?.Stop();
        _deactivationGuard = null;
        _suppressDeactivation = false;
        var fadeOut = new DoubleAnimation(1, 0, TimeSpan.FromMilliseconds(100));
        fadeOut.Completed += (_, _) => Hide();
        BeginAnimation(OpacityProperty, fadeOut);
    }

    private void PositionNearTray()
    {
        var workArea = SystemParameters.WorkArea;
        Left = workArea.Right - Width - 12;
        Top = workArea.Bottom - Height - 12;
    }

    private void Window_Deactivated(object sender, EventArgs e)
    {
        // WinForms NotifyIcon menus briefly deactivate WPF windows while opening.
        // Do not turn that focus transition into a lost first click.
        if (_suppressDeactivation) return;
        HidePopover();
    }

    private void Pair_Click(object sender, RoutedEventArgs e) => _pair();
    private void Pause_Click(object sender, RoutedEventArgs e) => _pause();

    private void OpenLogFolder_Click(object sender, RoutedEventArgs e)
    {
        try { Process.Start("explorer.exe", FileLogger.Instance.LogDirectory); }
        catch (Exception ex) { FileLogger.Instance.Error("Failed to open log folder", ex); }
    }

    private void Exit_Click(object sender, RoutedEventArgs e) => System.Windows.Application.Current.Shutdown();
}
