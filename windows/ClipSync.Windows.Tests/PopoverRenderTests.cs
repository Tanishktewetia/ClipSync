using System.IO;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using ClipSync.Windows.UI;
using Xunit;

namespace ClipSync.Windows.Tests;

public sealed class PopoverRenderTests
{
    [Fact] public void ExistingPanelRendersAllStatesInBothThemesWithoutStartingSync()
    {
        Exception? failure = null;
        var thread = new Thread(() => {
            try {
                var app = new Application { ShutdownMode = ShutdownMode.OnExplicitShutdown };
                var styles = new ResourceDictionary { Source = new Uri("/ClipSync;component/Theme/Styles.xaml", UriKind.Relative) };
                foreach (var theme in new[] { "Light", "Dark" }) {
                    app.Resources.MergedDictionaries.Clear();
                    app.Resources.MergedDictionaries.Add(new ResourceDictionary { Source = new Uri($"/ClipSync;component/Theme/{theme}Theme.xaml", UriKind.Relative) });
                    app.Resources.MergedDictionaries.Add(styles);
                    foreach (var state in new[] { "Connected", "Waiting", "Paused", "Error" }) {
                        var window = new StatusWindow(() => { }, () => false);
                        window.SetStatus(state, state == "Connected" ? "Secure phone connected. Clipboard sync is active." : "A readable status message with room for connection guidance.");
                        window.SetPairCode("123456");
                        var panel = (FrameworkElement)window.Content;
                        panel.Measure(new Size(400, double.PositiveInfinity));
                        var measured = panel.DesiredSize;
                        Assert.True(double.IsFinite(measured.Height));
                        Assert.InRange(measured.Height, 400, 760);
                        panel.Arrange(new Rect(0, 0, 400, measured.Height)); panel.UpdateLayout();
                        var bitmap = new RenderTargetBitmap(400, (int)Math.Ceiling(measured.Height), 96, 96, PixelFormats.Pbgra32);
                        bitmap.Render(panel);
                        var output = Environment.GetEnvironmentVariable("CLIPSYNC_RENDER_DIR") ?? Path.Combine(AppContext.BaseDirectory, "TestResults", "visual");
                        Directory.CreateDirectory(output);
                        var encoder = new PngBitmapEncoder(); encoder.Frames.Add(BitmapFrame.Create(bitmap));
                        using var file = File.Create(Path.Combine(output, $"popover-{theme.ToLowerInvariant()}-{state.ToLowerInvariant()}.png"));
                        encoder.Save(file);
                        window.CloseForExit();
                    }
                }
                app.Shutdown();
            } catch (Exception e) { failure = e; }
        });
        thread.SetApartmentState(ApartmentState.STA); thread.Start();
        Assert.True(thread.Join(TimeSpan.FromSeconds(20)), "Offscreen WPF render timed out");
        Assert.Null(failure);
    }
}
