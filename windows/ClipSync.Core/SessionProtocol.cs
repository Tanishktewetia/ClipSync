using System.Buffers.Binary;
using System.Text;
namespace ClipSync.Core;

// CSP1 extension negotiated by HELLO. Legacy TEXT sessions remain compatible.
public static class SessionProtocol
{
    public const int MaxText = FrameCodec.MaxPayloadLength - 266;
    public static ClipMessage Hello(ClipMessage? latest, bool replay) => new(MessageType.Hello,
        Lamport: latest?.Lamport ?? 0, DeviceId: $"cs7|{latest?.DeviceId ?? ""}|{(replay ? 1 : 0)}");
    public static string? Origin(ClipMessage m) { var p = m.DeviceId?.Split('|'); return p is { Length: 3 } && p[0] == "cs7" ? p[1] : null; }
    public static bool Replay(ClipMessage m) => m.DeviceId?.EndsWith("|1", StringComparison.Ordinal) == true;
    public static bool Newer(ClipMessage m, long clock, string origin) => m.Lamport > clock || (m.Lamport == clock && string.CompareOrdinal(m.DeviceId, origin) > 0);
    public static byte[] EncodeState(ClipMessage m)
    {
        var id = Encoding.UTF8.GetBytes(m.DeviceId ?? ""); var text = Encoding.UTF8.GetBytes(m.Text ?? "");
        if (m.Lamport <= 0 || m.Lamport >= long.MaxValue - 1 || id.Length is < 1 or > 256 || text.Length > MaxText) throw new InvalidDataException("Invalid state");
        var p = new byte[10 + id.Length + text.Length]; BinaryPrimitives.WriteInt64BigEndian(p, m.Lamport);
        BinaryPrimitives.WriteUInt16BigEndian(p.AsSpan(8), (ushort)id.Length); id.CopyTo(p, 10); text.CopyTo(p, 10 + id.Length); return p;
    }
    public static ClipMessage DecodeState(byte[] p)
    {
        if (p.Length < 11) throw new InvalidDataException("Short state");
        var clock = BinaryPrimitives.ReadInt64BigEndian(p); var size = BinaryPrimitives.ReadUInt16BigEndian(p.AsSpan(8));
        if (clock <= 0 || clock >= long.MaxValue - 1 || size is < 1 or > 256 || p.Length < 10 + size || p.Length - 10 - size > MaxText) throw new InvalidDataException("Invalid state");
        return new(MessageType.State, Text: Encoding.UTF8.GetString(p.AsSpan(10 + size)), Lamport: clock, DeviceId: Encoding.UTF8.GetString(p.AsSpan(10, size)));
    }
}
public sealed class ReconnectJournal(string deviceId)
{
    long _clock;
    public ClipMessage? Latest { get; private set; }
    public ClipMessage Local(string text)
    {
        if (Encoding.UTF8.GetByteCount(text) > SessionProtocol.MaxText || _clock >= long.MaxValue - 1) throw new InvalidDataException("Clipboard limit");
        return Latest = new(MessageType.State, Text: text, Lamport: ++_clock, DeviceId: deviceId);
    }
    public bool Accept(ClipMessage m)
    {
        if (m.Lamport <= 0 || m.Lamport >= long.MaxValue - 1) throw new InvalidDataException("Invalid clock");
        if (_clock >= long.MaxValue - 1) throw new InvalidDataException("Clock exhausted");
        _clock = Math.Max(_clock, m.Lamport) + 1;
        if (Latest is not null && !SessionProtocol.Newer(m, Latest.Lamport, Latest.DeviceId ?? "")) return false;
        Latest = m; return true;
    }
    public void Observe(long value) { if(value < 0 || value >= long.MaxValue - 1 || _clock >= long.MaxValue - 1) throw new InvalidDataException("Invalid clock"); _clock = Math.Max(_clock, value) + 1; }
    public void Clear() => Latest = null;
}
