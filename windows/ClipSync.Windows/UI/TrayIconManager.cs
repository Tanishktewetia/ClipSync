using System.Drawing;
using System.Windows.Forms;
using ClipSync.Windows.Logging;

namespace ClipSync.Windows.UI;

public sealed class TrayIconManager : IDisposable
{
    private readonly NotifyIcon _notifyIcon;
    private readonly StatusWindow _statusWindow;

    public TrayIconManager(StatusWindow statusWindow)
    {
        _statusWindow = statusWindow;
        _notifyIcon = new NotifyIcon
        {
            Text = "ClipSync — Waiting",
            Visible = true,
            Icon = CreateTrayIcon()
        };
        _notifyIcon.MouseUp += OnMouseUp;

        var menu = new ContextMenuStrip();
        menu.Items.Add("Show Status", null, (_, _) => ScheduleShowStatus());
        menu.Items.Add("Open Log Folder", null, OnOpenLogFolder);
        menu.Items.Add("Quit ClipSync (stop sharing)", null, OnExit);
        _notifyIcon.ContextMenuStrip = menu;
        FileLogger.Instance.Info("Tray icon created");
    }

    private void OnMouseUp(object? sender, MouseEventArgs e)
    {
        if (e.Button == MouseButtons.Left)
            ScheduleShowFromTray();
    }

    private void ScheduleShowFromTray()
    {
        _statusWindow.Dispatcher.BeginInvoke(
            new Action(() => _statusWindow.ShowPopover()),
            System.Windows.Threading.DispatcherPriority.ContextIdle);
    }

    private void ScheduleShowStatus()
    {
        _statusWindow.Dispatcher.BeginInvoke(
            new Action(() => _statusWindow.ShowPopover()),
            System.Windows.Threading.DispatcherPriority.ContextIdle);
    }

    private void OnOpenLogFolder(object? sender, EventArgs e)
    {
        try { System.Diagnostics.Process.Start("explorer.exe", FileLogger.Instance.LogDirectory); }
        catch (Exception ex) { FileLogger.Instance.Error("Failed to open log folder", ex); }
    }

    private void OnExit(object? sender, EventArgs e) => System.Windows.Application.Current.Shutdown();

    private static Icon CreateTrayIcon()
    {
        using var bitmap = new Bitmap(32, 32);
        using var graphics = Graphics.FromImage(bitmap);
        graphics.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
        using var background = new SolidBrush(ColorTranslator.FromHtml("#4F46E5"));
        graphics.FillEllipse(background, 1, 1, 30, 30);
        using var font = new Font("Segoe UI", 10, FontStyle.Bold);
        using var textBrush = new SolidBrush(Color.White);
        var format = new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center };
        graphics.DrawString("CS", font, textBrush, new RectangleF(0, 0, 32, 32), format);
        return Icon.FromHandle(bitmap.GetHicon());
    }

    public void Dispose()
    {
        _notifyIcon.Visible = false;
        _notifyIcon.Dispose();
        _statusWindow.CloseForExit();
    }
}
