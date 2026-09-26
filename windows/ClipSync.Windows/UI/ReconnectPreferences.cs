using Microsoft.Win32;
namespace ClipSync.Windows.UI;

/// <summary>User preference only. Never stores clipboard payloads.</summary>
internal static class ReconnectPreferences
{
    public static bool Enabled
    {
        get { try { using var key = Registry.CurrentUser.OpenSubKey(@"Software\ClipSync"); return (key?.GetValue("ReplayOnConnect") as int? ?? 1) != 0; } catch { return true; } }
        set { try { using var key = Registry.CurrentUser.CreateSubKey(@"Software\ClipSync"); key.SetValue("ReplayOnConnect", value ? 1 : 0, RegistryValueKind.DWord); } catch { Logging.FileLogger.Instance.Warn("Reconnect preference could not be saved"); } }
    }
}
