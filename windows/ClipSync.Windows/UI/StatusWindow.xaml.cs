using System.ComponentModel;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Interop;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Threading;
using ClipSync.Windows.Logging;
using MediaBrush = System.Windows.Media.Brush;
using Forms = System.Windows.Forms;

namespace ClipSync.Windows.UI;

public partial class StatusWindow : Window
{
    private readonly Action _pair;
    private readonly Func<bool> _pause;
    private readonly PopoverVisibility _visibility = new();
    private bool _suppressDeactivation;
    private bool _closingForExit;
    private bool _positioning;
    private Forms.Screen? _anchorScreen;
    private readonly DispatcherTimer _deactivationGuard;

    public StatusWindow(Action pair, Func<bool> pause)
    {
        _pair = pair; _pause = pause;
        InitializeComponent();
        BuildLabel.Text = $"v{typeof(StatusWindow).Assembly.GetName().Version?.ToString(3)} · Text only";
        WindowStartupLocation = WindowStartupLocation.Manual;
        ShowActivated = true;
        _deactivationGuard = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(250) };
        _deactivationGuard.Tick += (_, _) => { _deactivationGuard.Stop(); _suppressDeactivation = false; };
        SizeChanged += (_, _) => { if (IsVisible && _visibility.VisibleRequested && !_positioning) PositionNearTray(); };
    }
    public void SetStatus(string state, string detail)
    {
        StatusText.Text = state; StatusDetail.Text = detail;
        var (icon, brush) = state switch {
            "Connected" => ("●", "ConnectedBrush"), "Paused" => ("Ⅱ", "PausedBrush"),
            "Error" => ("!", "ErrorBrush"), _ => ("◌", "WaitingBrush")
        };
        StatusIcon.Text = icon; StatusIcon.Foreground = (MediaBrush)FindResource(brush);
        PauseButton.Content = state == "Paused" ? "Resume syncing" : "Pause syncing";
    }
    public void SetPairCode(string code) => PairCodeText.Text = code.All(char.IsDigit) && code.Length == 6 ? "Your code: " + code : code;
    public void TogglePopover() { if (_visibility.VisibleRequested) HidePopover(); else ShowPopover(); }

    public void ShowPopover()
    {
        _visibility.Show();
        BeginAnimation(OpacityProperty, null); // Cancels any fade-out and its outdated completion.
        _deactivationGuard.Stop(); _suppressDeactivation = true;
        _anchorScreen = Forms.Screen.FromPoint(Forms.Cursor.Position);
        _positioning = true;
        try {
            Opacity = 0;
            _ = new WindowInteropHelper(this).EnsureHandle();
            ConstrainToScreen();
            if (!IsVisible) Show();
            UpdateLayout(); // SizeToContent has resolved ActualHeight on the FIRST opening.
        } finally { _positioning = false; }
        PositionNearTray();
        Activate(); Focus();
        if (SystemParameters.ClientAreaAnimation)
            BeginAnimation(OpacityProperty, new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(120)));
        else Opacity = 1;
        _deactivationGuard.Start();
    }
    public void HidePopover()
    {
        var revision = _visibility.Hide();
        _deactivationGuard.Stop(); _suppressDeactivation = false;
        if (!IsVisible) return;
        BeginAnimation(OpacityProperty, null);
        if (!SystemParameters.ClientAreaAnimation) { Hide(); return; }
        var fade = new DoubleAnimation(Opacity, 0, TimeSpan.FromMilliseconds(90));
        fade.Completed += (_, _) => { if (_visibility.CanFinishHide(revision)) Hide(); };
        BeginAnimation(OpacityProperty, fade);
    }
    private void ConstrainToScreen()
    {
        var area = (_anchorScreen ?? Forms.Screen.PrimaryScreen!).WorkingArea;
        var scale = PresentationSource.FromVisual(this)?.CompositionTarget?.TransformToDevice ?? Matrix.Identity;
        MaxHeight = Math.Max(200, (area.Height - 24) / scale.M22);
        Width = Math.Max(240, Math.Min(400, (area.Width - 24) / scale.M11));
    }
    private void PositionNearTray()
    {
        if (_positioning) return;
        _positioning = true;
        try {
            var handle = new WindowInteropHelper(this).Handle;
            if (handle == IntPtr.Zero) return;
            // Repeat once after crossing a DPI boundary so layout/device scaling agrees.
            for (var pass = 0; pass < 2; pass++) {
                ConstrainToScreen(); UpdateLayout();
                var area = (_anchorScreen ?? Forms.Screen.PrimaryScreen!).WorkingArea;
                var transform = PresentationSource.FromVisual(this)?.CompositionTarget?.TransformToDevice ?? Matrix.Identity;
                var target = PopoverPlacement.BottomRight(new(area.Left, area.Top, area.Width, area.Height), ActualWidth * transform.M11, ActualHeight * transform.M22);
                SetWindowPos(handle, IntPtr.Zero, (int)Math.Round(target.Left), (int)Math.Round(target.Top), 0, 0, 0x0001 | 0x0004 | 0x0010);
            }
        } finally { _positioning = false; }
    }
    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool SetWindowPos(IntPtr hwnd, IntPtr after, int x, int y, int width, int height, uint flags);

    protected override void OnClosing(CancelEventArgs e)
    {
        if (!_closingForExit) { e.Cancel = true; HidePopover(); }
        base.OnClosing(e);
    }
    public void CloseForExit() { _closingForExit = true; _deactivationGuard.Stop(); Close(); }
    private void Window_Deactivated(object sender, EventArgs e) { if (!_suppressDeactivation) HidePopover(); }
    private void Hide_Click(object sender, RoutedEventArgs e) => HidePopover();
    private void Pair_Click(object sender, RoutedEventArgs e) => _pair();
    private void Pause_Click(object sender, RoutedEventArgs e) => _pause();
    private void OpenLogFolder_Click(object sender, RoutedEventArgs e)
    {
        try { Process.Start("explorer.exe", FileLogger.Instance.LogDirectory); }
        catch (Exception ex) { FileLogger.Instance.Error("Failed to open log folder", ex); }
    }
    private void Exit_Click(object sender, RoutedEventArgs e) => System.Windows.Application.Current.Shutdown();
}
