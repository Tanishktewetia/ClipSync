using ClipSync.Core;
using Xunit;
namespace ClipSync.Core.Tests;
public sealed class SessionProtocolTests
{
    [Fact] public void WireVectorMatchesAndroid() => Assert.Equal("43535031060000000D00000000000000070001616869", Convert.ToHexString(FrameCodec.Encode(new(MessageType.State, Text: "hi", Lamport: 7, DeviceId: "a"))));
    [Fact] public void UnicodeStateSurvivesFragmentation()
    {
        var original = new ClipMessage(MessageType.State, Text: "fixture ☕ हिन्दी", Lamport: 42, DeviceId: "phone"); var reader = new FrameReader();
        var messages = FrameCodec.Encode(original).SelectMany(b => reader.Push([b])).ToArray(); Assert.Equal([original], messages);
    }
    [Fact] public void EqualClocksUseOrdinalOrigin()
    {
        var a = new ReconnectJournal("a"); var b = new ReconnectJournal("b"); var first = a.Local("first"); var second = b.Local("second");
        Assert.True(a.Accept(second)); Assert.False(b.Accept(first)); var third = a.Local("third"); Assert.Equal(3, third.Lamport); Assert.True(b.Accept(third));
    }
    [Fact] public void ClearDropsPayloadNotClock() { var a = new ReconnectJournal("a"); a.Local("fixture"); a.Clear(); Assert.Null(a.Latest); Assert.Equal(2, a.Local("after pause").Lamport); }
    [Fact] public void HelloDoesNotContainPayload() { var a = new ReconnectJournal("a"); var hello = SessionProtocol.Hello(a.Local("private fixture"), false); Assert.Null(hello.Text); Assert.Equal("a", SessionProtocol.Origin(hello)); Assert.False(SessionProtocol.Replay(hello)); }
    [Theory] [InlineData("4353503101FFFFFFFF")] [InlineData("435350310200000000")] [InlineData("435350310600000000")]
    public void MalformedFramesRejected(string hex) => Assert.Throws<InvalidDataException>(() => new FrameReader().Push(Convert.FromHexString(hex)).ToArray());
    [Fact] public void OversizeStateRejected() => Assert.Throws<InvalidDataException>(() => new ReconnectJournal("a").Local(new string('x', FrameCodec.MaxPayloadLength)));
}
