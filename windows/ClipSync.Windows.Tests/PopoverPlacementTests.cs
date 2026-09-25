using ClipSync.Windows.UI;
using Xunit;

namespace ClipSync.Windows.Tests;
public sealed class PopoverPlacementTests
{
    [Fact] public void FirstOpenWithAutoHeightNeverProducesNaN()
    {
        var p = PopoverPlacement.BottomRight(new(0, 0, 1920, 1040), 400, double.NaN);
        Assert.True(double.IsFinite(p.Top)); Assert.Equal(468, p.Top); Assert.Equal(1508, p.Left);
    }
    [Fact] public void MeasuredFirstAndSubsequentOpenUseSamePosition()
    {
        var work = new PopoverPlacement.Bounds(0, 0, 1366, 728);
        Assert.Equal(PopoverPlacement.BottomRight(work, 400, 600), PopoverPlacement.BottomRight(work, 400, 600));
        Assert.Equal(116, PopoverPlacement.BottomRight(work, 400, 600).Top);
    }
    [Theory]
    [InlineData(-1920, 0, 1920, 1040, 400, 560)]
    [InlineData(0, -1080, 1920, 1040, 600, 840)]
    [InlineData(1920, 0, 2560, 1400, 800, 1120)]
    [InlineData(0, 0, 320, 480, 400, 1000)]
    public void PlacementStaysInsideSelectedMonitorWorkArea(double x, double y, double w, double h, double pw, double ph)
    {
        var p = PopoverPlacement.BottomRight(new(x,y,w,h),pw,ph);
        Assert.True(p.Left >= x && p.Top >= y);
        Assert.True(p.Left + p.Width <= x + w && p.Top + p.Height <= y + h);
    }
    [Fact] public void InvalidWorkAreaRejected() => Assert.Throws<ArgumentOutOfRangeException>(() => PopoverPlacement.BottomRight(new(0,0,double.NaN,0),400,560));
    [Fact] public void ShowInvalidatesOlderFadeOutCompletion()
    {
        var v = new PopoverVisibility(); v.Show(); var old = v.Hide(); v.Show();
        Assert.False(v.CanFinishHide(old)); Assert.True(v.VisibleRequested);
    }
    [Fact] public void LatestHideCanFinishButOlderHideCannot()
    {
        var v = new PopoverVisibility(); var old = v.Hide(); var latest = v.Hide();
        Assert.False(v.CanFinishHide(old)); Assert.True(v.CanFinishHide(latest));
    }
}
