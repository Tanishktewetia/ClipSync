using System.Windows;
using Microsoft.Win32;

namespace ClipSync.Windows.Theme;

/// <summary>
/// Manages light/dark theme switching following the OS setting.
/// Listens to the Windows registry for theme changes via SystemEvents.
/// </summary>
public static class ThemeManager
{
    private static bool _initialized;

    public static void Initialize()
    {
        if (_initialized) return;
        _initialized = true;

        ApplyTheme(IsSystemDarkMode());
        SystemEvents.UserPreferenceChanged += OnUserPreferenceChanged;
    }

    public static void Shutdown()
    {
        SystemEvents.UserPreferenceChanged -= OnUserPreferenceChanged;
    }

    private static void OnUserPreferenceChanged(object sender, UserPreferenceChangedEventArgs e)
    {
        if (e.Category == UserPreferenceCategory.General)
        {
            System.Windows.Application.Current?.Dispatcher.Invoke(() =>
            {
                ApplyTheme(IsSystemDarkMode());
            });
        }
    }

    public static bool IsSystemDarkMode()
    {
        try
        {
            using var key = Registry.CurrentUser.OpenSubKey(
                @"Software\Microsoft\Windows\CurrentVersion\Themes\Personalize");
            var value = key?.GetValue("AppsUseLightTheme");
            return value is int intValue && intValue == 0;
        }
        catch
        {
            return false;
        }
    }

    public static void ApplyTheme(bool isDark)
    {
        var app = System.Windows.Application.Current;
        if (app == null) return;

        var themePath = isDark
            ? "Theme/DarkTheme.xaml"
            : "Theme/LightTheme.xaml";

        var themeDict = new ResourceDictionary
        {
            Source = new Uri(themePath, UriKind.Relative)
        };

        // Remove existing theme dictionaries (keep other merged dictionaries)
        var toRemove = app.Resources.MergedDictionaries
            .Where(d => d.Source?.OriginalString.Contains("Theme/LightTheme.xaml") == true || d.Source?.OriginalString.Contains("Theme/DarkTheme.xaml") == true)
            .ToList();

        foreach (var dict in toRemove)
        {
            app.Resources.MergedDictionaries.Remove(dict);
        }

        // Insert theme as the first merged dictionary
        app.Resources.MergedDictionaries.Insert(0, themeDict);

        Logging.FileLogger.Instance.Info($"Theme applied: {(isDark ? "Dark" : "Light")}");
    }
}

