namespace ClipSync.Windows.UI;

/// <summary>Pixel-space geometry; never subtract Window.Height (NaN under SizeToContent).</summary>
public static class PopoverPlacement
{
    public readonly record struct Bounds(double Left, double Top, double Width, double Height);
    public static Bounds BottomRight(Bounds work, double width, double height, double gap = 12)
    {
        if (!double.IsFinite(work.Left) || !double.IsFinite(work.Top) || !double.IsFinite(work.Width) || !double.IsFinite(work.Height) || work.Width <= 0 || work.Height <= 0)
            throw new ArgumentOutOfRangeException(nameof(work));
        gap = double.IsFinite(gap) ? Math.Clamp(gap, 0, Math.Min(work.Width, work.Height) / 4) : 12;
        var w = Math.Clamp(double.IsFinite(width) && width > 0 ? width : 400, 1, Math.Max(1, work.Width - 2 * gap));
        var h = Math.Clamp(double.IsFinite(height) && height > 0 ? height : 560, 1, Math.Max(1, work.Height - 2 * gap));
        return new Bounds(work.Left + work.Width - w - gap, work.Top + work.Height - h - gap, w, h);
    }
}

/// <summary>Invalidate an old fade callback when another show/hide request arrives.</summary>
public sealed class PopoverVisibility
{
    private long _revision;
    public bool VisibleRequested { get; private set; }
    public long Show() { VisibleRequested = true; return ++_revision; }
    public long Hide() { VisibleRequested = false; return ++_revision; }
    public bool CanFinishHide(long revision) => !VisibleRequested && revision == _revision;
}
