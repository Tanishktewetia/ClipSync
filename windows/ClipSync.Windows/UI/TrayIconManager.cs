using System.Drawing;
using System.Windows;
using System.Windows.Forms;
using ClipSync.Windows.Logging;

namespace ClipSync.Windows.UI;

/// <summary>
/// Manages the system tray icon and context menu.
/// The app lives in the tray — the popover is for quick glances.
/// </summary>
public sealed class TrayIconManager : IDisposable
{
    private readonly NotifyIcon _notifyIcon;
    private readonly StatusWindow _statusWindow;

    public TrayIconManager()
    {
        _statusWindow = new StatusWindow();

        _notifyIcon = new NotifyIcon
        {
            Text = "ClipSync — Waiting",
            Visible = true,
            Icon = CreateTrayIcon()
        };

        _notifyIcon.Click += OnTrayClick;
        _notifyIcon.DoubleClick += OnTrayClick;

        var contextMenu = new ContextMenuStrip();
        contextMenu.Items.Add("Show Status", null, (_, _) => TogglePopover());
        contextMenu.Items.Add(new ToolStripSeparator());
        contextMenu.Items.Add("Open Log Folder", null, OnOpenLogFolder);
        contextMenu.Items.Add(new ToolStripSeparator());
        contextMenu.Items.Add("Exit", null, OnExit);
        _notifyIcon.ContextMenuStrip = contextMenu;

        FileLogger.Instance.Info("Tray icon created");
    }

    private void OnTrayClick(object? sender, EventArgs e)
    {
        if (e is MouseEventArgs me && me.Button == MouseButtons.Right) return;
        TogglePopover();
    }

    private void TogglePopover()
    {
        if (_statusWindow.IsVisible)
        {
            _statusWindow.HidePopover();
        }
        else
        {
            _statusWindow.ShowPopover();
        }
    }

    private void OnOpenLogFolder(object? sender, EventArgs e)
    {
        var logDir = FileLogger.Instance.LogDirectory;
        try
        {
            System.Diagnostics.Process.Start("explorer.exe", logDir);
        }
        catch (Exception ex)
        {
            FileLogger.Instance.Error("Failed to open log folder", ex);
        }
    }

    private void OnExit(object? sender, EventArgs e)
    {
        FileLogger.Instance.Info("User requested exit via tray");
        _notifyIcon.Visible = false;
        System.Windows.Application.Current?.Shutdown();
    }

    /// <summary>
    /// Creates a simple programmatic tray icon (colored circle with "CS" text).
    /// No external icon file needed for Phase 0.
    /// </summary>
    private static Icon CreateTrayIcon()
    {
        using var bmp = new Bitmap(32, 32);
        using var g = Graphics.FromImage(bmp);
        g.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
        g.TextRenderingHint = System.Drawing.Text.TextRenderingHint.AntiAlias;

        // Indigo circle
        using var bgBrush = new SolidBrush(ColorTranslator.FromHtml("#4F46E5"));
        g.FillEllipse(bgBrush, 1, 1, 30, 30);

        // White "CS" text
        using var font = new Font("Segoe UI", 11f, System.Drawing.FontStyle.Bold);
        using var textBrush = new SolidBrush(System.Drawing.Color.White);
        var sf = new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center };
        g.DrawString("CS", font, textBrush, new RectangleF(0, 0, 32, 32), sf);

        return Icon.FromHandle(bmp.GetHicon());
    }

    public void Dispose()
    {
        _notifyIcon.Visible = false;
        _notifyIcon.Dispose();
        _statusWindow.Close();
    }
}
