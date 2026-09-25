using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using System.Windows.Interop;
using System.Windows.Threading;
using ClipSync.Windows.Logging;

namespace ClipSync.Windows.Clipboard;

/// <summary>STA clipboard access. Store only a hash for echo/dedup; retry locked writes too.</summary>
public sealed class ClipboardWatcher : IDisposable
{
    private const int WmClipboardUpdate = 0x031D;
    private readonly HwndSource _source;
    private readonly DispatcherTimer _debounce;
    private bool _writing;
    private string? _lastHash;
    public event Action<string>? TextChanged;
    public ClipboardWatcher()
    {
        _source = new HwndSource(new HwndSourceParameters("ClipSyncClipboardWatcher") { ParentWindow = new IntPtr(-3), Width = 0, Height = 0, WindowStyle = 0 });
        _source.AddHook(WndProc);
        if (!AddClipboardFormatListener(_source.Handle)) throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error());
        _debounce = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(125) };
        _debounce.Tick += (_, _) => { _debounce.Stop(); ReadAndRaise(); };
    }
    private IntPtr WndProc(IntPtr hwnd, int message, IntPtr w, IntPtr l, ref bool handled)
    {
        if (message == WmClipboardUpdate && !_writing) { _debounce.Stop(); _debounce.Start(); handled = true; }
        return IntPtr.Zero;
    }
    private static string Hash(string text) => Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(text)));
    private void ReadAndRaise()
    {
        for (var attempt = 0; attempt < 5; attempt++) {
            try {
                if (!System.Windows.Clipboard.ContainsText()) { _lastHash = null; return; }
                var text = System.Windows.Clipboard.GetText();
                var hash = Hash(text);
                if (hash == _lastHash) return;
                _lastHash = hash;
                TextChanged?.Invoke(text);
                return;
            } catch (COMException) { Thread.Sleep(10 * (attempt + 1)); }
        }
        FileLogger.Instance.Warn("Clipboard read remained locked after retries");
    }
    public bool WriteText(string text)
    {
        _writing = true;
        try {
            for (var attempt = 0; attempt < 5; attempt++) {
                try {
                    if (text.Length == 0) System.Windows.Clipboard.Clear();
                    else System.Windows.Clipboard.SetText(text);
                    _lastHash = Hash(text); // Mark only after a successful write.
                    return true;
                } catch (COMException) { Thread.Sleep(10 * (attempt + 1)); }
            }
            FileLogger.Instance.Warn("Clipboard write remained locked after retries");
            return false;
        } finally { _writing = false; }
    }
    [DllImport("user32.dll", SetLastError = true)] private static extern bool AddClipboardFormatListener(IntPtr hwnd);
    [DllImport("user32.dll", SetLastError = true)] private static extern bool RemoveClipboardFormatListener(IntPtr hwnd);
    public void Dispose() { _debounce.Stop(); RemoveClipboardFormatListener(_source.Handle); _source.RemoveHook(WndProc); _source.Dispose(); }
}
