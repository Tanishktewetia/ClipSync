using System.IO;
using System.Globalization;

namespace ClipSync.Windows.Logging;

/// <summary>
/// Simple file logger with daily rotation. Writes to %APPDATA%\ClipSync\logs\.
/// Never logs clipboard content — only type, size, and truncated hashes.
/// </summary>
public sealed class FileLogger : IDisposable
{
    private static readonly Lazy<FileLogger> _instance = new(() => new FileLogger());
    public static FileLogger Instance => _instance.Value;

    private readonly string _logDir;
    private StreamWriter? _writer;
    private string _currentDate = "";
    private readonly object _lock = new();

    private FileLogger()
    {
        _logDir = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            "ClipSync", "logs");
        Directory.CreateDirectory(_logDir);
    }

    public string LogDirectory => _logDir;

    public void Log(string level, string message)
    {
        lock (_lock)
        {
            EnsureWriter();
            var timestamp = DateTime.Now.ToString("HH:mm:ss.fff", CultureInfo.InvariantCulture);
            _writer!.WriteLine($"[{timestamp}] [{level}] {message}");
            _writer.Flush();
        }
    }

    public void Info(string message) => Log("INFO", message);
    public void Warn(string message) => Log("WARN", message);
    public void Error(string message) => Log("ERROR", message);
    public void Error(string message, Exception ex) => Log("ERROR", $"{message}: {ex}");

    private void EnsureWriter()
    {
        var today = DateTime.Now.ToString("yyyyMMdd", CultureInfo.InvariantCulture);
        if (today == _currentDate && _writer != null) return;

        _writer?.Dispose();
        _currentDate = today;
        var path = Path.Combine(_logDir, $"clipsync-{today}.log");
        _writer = new StreamWriter(path, append: true) { AutoFlush = true };

        // Clean up logs older than 7 days
        CleanOldLogs();
    }

    private void CleanOldLogs()
    {
        try
        {
            var cutoff = DateTime.Now.AddDays(-7);
            foreach (var file in Directory.GetFiles(_logDir, "clipsync-*.log"))
            {
                if (File.GetLastWriteTime(file) < cutoff)
                {
                    File.Delete(file);
                }
            }
        }
        catch
        {
            // Best effort
        }
    }

    public void Dispose()
    {
        lock (_lock)
        {
            _writer?.Dispose();
            _writer = null;
        }
    }
}
