using System.Reflection;
using Microsoft.Win32;
using ClipSync.Windows.Logging;

namespace ClipSync.Windows.UI;

public static class StartupManager
{
    private const string RunKeyPath = @"Software\Microsoft\Windows\CurrentVersion\Run";
    private const string ValueName = "ClipSync";

    public static bool IsEnabled
    {
        get
        {
            try
            {
                using var key = Registry.CurrentUser.OpenSubKey(RunKeyPath, writable: false);
                return key?.GetValue(ValueName) is string value && !string.IsNullOrWhiteSpace(value);
            }
            catch (Exception ex)
            {
                FileLogger.Instance.Warn("Could not read startup setting: " + ex.GetType().Name);
                return false;
            }
        }
    }

    public static string BuildCommand(string executablePath) => $"\"{executablePath}\" --startup";

    public static bool SetEnabled(bool enabled)
    {
        try
        {
            using var key = Registry.CurrentUser.CreateSubKey(RunKeyPath, writable: true);
            if (key is null) return false;
            if (enabled)
            {
                var executable = Environment.ProcessPath ?? Assembly.GetExecutingAssembly().Location;
                key.SetValue(ValueName, BuildCommand(executable), RegistryValueKind.String);
            }
            else
            {
                key.DeleteValue(ValueName, throwOnMissingValue: false);
            }
            FileLogger.Instance.Info($"Start with Windows {(enabled ? "enabled" : "disabled")}");
            return true;
        }
        catch (Exception ex)
        {
            FileLogger.Instance.Error("Could not update startup setting", ex);
            return false;
        }
    }
}
