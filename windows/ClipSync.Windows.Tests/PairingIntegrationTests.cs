using System.IO;
using System.Net;
using System.Net.Security;
using System.Net.Sockets;
using System.Security.Authentication;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using ClipSync.Core;
using ClipSync.Windows.Security;
using ClipSync.Windows.Transport;
using Xunit;

namespace ClipSync.Windows.Tests;

public sealed class PairingIntegrationTests
{
    [Fact] public void PairCodeMatchesAndroidVector() => Assert.Equal("830564", CertificateStore.PairCode(new string('A', 64), new string('B', 64)));

    [Fact] public async Task UnknownIdentityIsRejectedOutsideExplicitPairing()
    {
        using var f = new Fixture(); using var peer = Identity();
        await AssertRejected(f, peer);
        Assert.Null(f.Store.PinnedPeer);
    }
    [Fact] public async Task EcPhonePairsAndReconnectsWithNoNewConfirmation()
    {
        using var f = new Fixture(); using var peer = Identity();
        using (var first = await f.Pair(peer)) Assert.Equal(SslProtocols.Tls13, first.Ssl.SslProtocol);
        using var resumed = await f.Connect(peer);
        Assert.Equal("READY", await resumed.Line());
        Assert.Equal(Fingerprint(peer), f.Store.PinnedPeer);
    }
    [Fact] public async Task WrongCodePreservesExistingTrustAndExistingSession()
    {
        using var f = new Fixture(); using var first = Identity(); using var stranger = Identity();
        using var active = await f.Pair(first);
        f.Server.BeginPairing();
        using var candidate = await f.Connect(stranger);
        Assert.StartsWith("PAIR|", await candidate.Line());
        await candidate.Send("CONFIRM|not-a-code");
        Assert.Equal("PAIR_REJECTED", await candidate.Line());
        Assert.Equal(Fingerprint(first), f.Store.PinnedPeer);
        f.Server.Broadcast("fixture only");
        Assert.Equal("fixture only", await active.Text());
    }
    [Fact] public async Task ConfirmedReplacementRevokesOldClient()
    {
        using var f = new Fixture(); using var first = Identity(); using var replacement = Identity();
        using var active = await f.Pair(first);
        using var next = await f.Pair(replacement);
        Assert.Equal(Fingerprint(replacement), f.Store.PinnedPeer);
        await AssertRejected(f, first);
        f.Server.Broadcast("replacement fixture");
        Assert.Equal("replacement fixture", await next.Text());
    }
    [Fact] public async Task PairingWindowExpiresWithoutPersistingCandidate()
    {
        using var f = new Fixture(TimeSpan.FromMilliseconds(800)); using var peer = Identity();
        f.Server.BeginPairing();
        using var candidate = await f.Connect(peer);
        Assert.StartsWith("PAIR|", await candidate.Line());
        await Task.Delay(1000);
        var error = await Record.ExceptionAsync(async () => {
            await candidate.Send("CONFIRM|" + f.Server.PairCodeFor(Fingerprint(peer)));
            Assert.NotEqual("READY", await candidate.Line());
        });
        Assert.False(error is Xunit.Sdk.NotEqualException);
        Assert.Null(f.Store.PinnedPeer);
    }
    [Fact] public async Task FragmentedConfirmationAndUnicodeTextRemainCompatible()
    {
        using var f = new Fixture(); using var peer = Identity(); f.Server.BeginPairing();
        using var connection = await f.Connect(peer);
        var offer = (await connection.Line()).Split('|');
        foreach (var b in Encoding.ASCII.GetBytes("CONFIRM|" + offer[2] + "\n")) await connection.Ssl.WriteAsync(new[] { b });
        Assert.Equal("READY", await connection.Line());
        f.Server.Broadcast("fixture ☕\nहिन्दी");
        Assert.Equal("fixture ☕\nहिन्दी", await connection.Text());
    }
    [Fact] public async Task UnconfirmedClientNeverReceivesClipboardFrames()
    {
        using var f = new Fixture(); using var peer = Identity(); f.Server.BeginPairing();
        using var connection = await f.Connect(peer);
        Assert.StartsWith("PAIR|", await connection.Line());
        f.Server.Broadcast("must not be sent");
        using var timeout = new CancellationTokenSource(200);
        await Assert.ThrowsAnyAsync<OperationCanceledException>(async () => await connection.Ssl.ReadExactlyAsync(new byte[1], timeout.Token));
        Assert.Null(f.Store.PinnedPeer);
    }
    [Fact] public async Task PersistedPinSurvivesServerRestart()
    {
        var directory = Path.Combine(AppContext.BaseDirectory, "test-identities", Guid.NewGuid().ToString("N"));
        using var peer = Identity();
        using (var first = new Fixture(directory: directory)) using (await first.Pair(peer)) { }
        using var restarted = new Fixture(directory: directory);
        using var connection = await restarted.Connect(peer);
        Assert.Equal("READY", await connection.Line());
    }
    [Fact] public async Task TenPhoneCopiesAndPcRepliesShareTheSameTlsSession()
    {
        using var f = new Fixture(); using var identity = Identity(); using var peer = await f.Pair(identity);
        var received = System.Threading.Channels.Channel.CreateUnbounded<string>();
        f.Server.IncomingText += text => received.Writer.TryWrite(text);
        using var deadline = new CancellationTokenSource(5000);
        for (var i = 0; i < 10; i++) {
            var phone = "phone-fixture-" + i;
            await peer.Ssl.WriteAsync(FrameCodec.Encode(new ClipMessage(MessageType.Text, phone)), deadline.Token);
            Assert.Equal(phone, await received.Reader.ReadAsync(deadline.Token));
            var pc = "pc-fixture-" + i;
            f.Server.Broadcast(pc);
            Assert.Equal(pc, await peer.Text());
        }
    }
    [Fact] public async Task FragmentedAndCombinedPhoneFramesAreDeliveredOnceEach()
    {
        using var f = new Fixture(); using var identity = Identity(); using var peer = await f.Pair(identity);
        var received = System.Threading.Channels.Channel.CreateUnbounded<string>();
        f.Server.IncomingText += text => received.Writer.TryWrite(text);
        using var deadline = new CancellationTokenSource(5000);
        var frame = FrameCodec.Encode(new ClipMessage(MessageType.Text, "phone unicode ☕"));
        foreach (var b in frame) await peer.Ssl.WriteAsync(new[] { b }, deadline.Token);
        var two = FrameCodec.Encode(new ClipMessage(MessageType.Text, "second"));
        var three = FrameCodec.Encode(new ClipMessage(MessageType.Text, "third"));
        await peer.Ssl.WriteAsync(two.Concat(three).ToArray(), deadline.Token);
        Assert.Equal("phone unicode ☕", await received.Reader.ReadAsync(deadline.Token));
        Assert.Equal("second", await received.Reader.ReadAsync(deadline.Token));
        Assert.Equal("third", await received.Reader.ReadAsync(deadline.Token));
        Assert.False(received.Reader.TryRead(out _));
    }
    [Fact] public async Task NegotiatedSessionReplaysOnlyNewestAndAnswersPing()
    {
        using var f = new Fixture(); using var identity = Identity();
        using (await f.Pair(identity)) { }
        f.Server.Broadcast("older fixture"); f.Server.Broadcast("newest fixture"); await Task.Delay(100);
        using var peer = await f.Connect(identity); Assert.Equal("READY", await peer.Line());
        await peer.Ssl.WriteAsync(FrameCodec.Encode(SessionProtocol.Hello(null, true)));
        Assert.Equal(MessageType.Hello, (await peer.Frame()).Type);
        var newest = await peer.Frame(); Assert.Equal(MessageType.State, newest.Type); Assert.Equal("newest fixture", newest.Text);
        await peer.Ssl.WriteAsync(FrameCodec.Encode(new ClipMessage(MessageType.Ping)));
        Assert.Equal(MessageType.Pong, (await peer.Frame()).Type);
    }
    [Fact] public async Task ReconnectOptOutSkipsSnapshotButAcceptsLiveText()
    {
        using var f = new Fixture(); using var identity = Identity(); using (await f.Pair(identity)) { }
        f.Server.Broadcast("offline fixture"); await Task.Delay(100);
        using var peer = await f.Connect(identity); Assert.Equal("READY", await peer.Line());
        await peer.Ssl.WriteAsync(FrameCodec.Encode(SessionProtocol.Hello(null, false)));
        Assert.Equal(MessageType.Hello, (await peer.Frame()).Type);
        await peer.Ssl.WriteAsync(FrameCodec.Encode(new ClipMessage(MessageType.Ping)));
        Assert.Equal(MessageType.Pong, (await peer.Frame()).Type); // A replay would have appeared before PONG.
        f.Server.Broadcast("live fixture"); Assert.Equal("live fixture", (await peer.Frame()).Text);
    }
    [Fact] public async Task NewerPhoneVersionWinsAndDuplicateIsNotAppliedTwice()
    {
        using var f = new Fixture(); using var identity = Identity(); using var peer = await f.Pair(identity);
        var incoming = System.Threading.Channels.Channel.CreateUnbounded<string>(); f.Server.IncomingText += text => incoming.Writer.TryWrite(text);
        await peer.Ssl.WriteAsync(FrameCodec.Encode(SessionProtocol.Hello(null, true))); await peer.Frame();
        var update = new ClipMessage(MessageType.State, Text: "phone version fixture", Lamport: 20, DeviceId: "phone");
        await peer.Ssl.WriteAsync(FrameCodec.Encode(update)); await peer.Ssl.WriteAsync(FrameCodec.Encode(update));
        await peer.Ssl.WriteAsync(FrameCodec.Encode(new ClipMessage(MessageType.Ping))); Assert.Equal(MessageType.Pong, (await peer.Frame()).Type);
        Assert.True(incoming.Reader.TryRead(out var text)); Assert.Equal(update.Text, text); Assert.False(incoming.Reader.TryRead(out _));
        f.Server.Broadcast("next PC fixture"); Assert.Equal(23, (await peer.Frame()).Lamport);
    }
    [Fact] public async Task DisabledReplayStillObservesPeerClockForNextLiveCopy()
    {
        using var f = new Fixture(); using var identity = Identity(); using var peer = await f.Pair(identity);
        f.Server.ReplayOnConnect = false;
        var known = new ClipMessage(MessageType.State, Text: "not transmitted", Lamport: 100, DeviceId: "phone");
        await peer.Ssl.WriteAsync(FrameCodec.Encode(SessionProtocol.Hello(known, false)));
        Assert.False(SessionProtocol.Replay(await peer.Frame()));
        f.Server.Broadcast("fresh PC fixture"); Assert.Equal(102, (await peer.Frame()).Lamport);
    }
    [Fact] public async Task PausedServerDoesNotApplyVersionedPhoneText()
    {
        using var f = new Fixture(); using var identity = Identity(); using var peer = await f.Pair(identity);
        var called = false; f.Server.IncomingText += _ => called = true; f.Server.Paused = true;
        await peer.Ssl.WriteAsync(FrameCodec.Encode(SessionProtocol.Hello(null, true))); Assert.False(SessionProtocol.Replay(await peer.Frame()));
        await peer.Ssl.WriteAsync(FrameCodec.Encode(new ClipMessage(MessageType.State, Text: "paused fixture", Lamport: 1, DeviceId: "phone")));
        await peer.Ssl.WriteAsync(FrameCodec.Encode(new ClipMessage(MessageType.Ping))); Assert.Equal(MessageType.Pong, (await peer.Frame()).Type); Assert.False(called);
    }
    [Fact] public async Task TwoMissedHeartbeatsRetireHalfOpenSession()
    {
        using var f = new Fixture(heartbeat: TimeSpan.FromMilliseconds(100)); using var identity = Identity(); using var peer = await f.Pair(identity);
        await peer.Ssl.WriteAsync(FrameCodec.Encode(SessionProtocol.Hello(null, true))); Assert.Equal(MessageType.Hello, (await peer.Frame()).Type);
        Assert.Equal(MessageType.Ping, (await peer.Frame()).Type);
        // Deliberately withhold PONG. The server must close without waiting on TCP's long timeout.
        using var deadline = new CancellationTokenSource(3000);
        var buffer = new byte[4096]; int read;
        try { do { read = await peer.Ssl.ReadAsync(buffer, deadline.Token); } while (read != 0); }
        catch (IOException) { read = 0; }
        Assert.Equal(0, read);
    }
    [Fact] public async Task FailedClipboardSinkLeavesVersionReplayable()
    {
        using var f = new Fixture(); using var identity = Identity();
        var update = new ClipMessage(MessageType.State, Text: "retry fixture", Lamport: 5, DeviceId: "phone");
        f.Server.ClipboardSink = _ => false;
        using (var peer = await f.Pair(identity)) {
            await peer.Ssl.WriteAsync(FrameCodec.Encode(SessionProtocol.Hello(update, true))); await peer.Frame();
            await peer.Ssl.WriteAsync(FrameCodec.Encode(update));
            using var deadline = new CancellationTokenSource(3000);
            Assert.Equal(0, await peer.Ssl.ReadAsync(new byte[1], deadline.Token));
        }
        var applied = new TaskCompletionSource<string>(TaskCreationOptions.RunContinuationsAsynchronously);
        f.Server.ClipboardSink = text => { applied.TrySetResult(text); return true; };
        using var retry = await f.Connect(identity); Assert.Equal("READY", await retry.Line());
        await retry.Ssl.WriteAsync(FrameCodec.Encode(SessionProtocol.Hello(update, true)));
        Assert.Equal(0, (await retry.Frame()).Lamport);
        await retry.Ssl.WriteAsync(FrameCodec.Encode(update));
        Assert.Equal(update.Text, await applied.Task.WaitAsync(TimeSpan.FromSeconds(3)));
    }
    private static async Task AssertRejected(Fixture fixture, X509Certificate2 identity)
    {
        bool ready = false;
        try { using var peer = await fixture.Connect(identity); ready = await peer.Line() == "READY"; }
        catch (Exception e) when (e is IOException or AuthenticationException or SocketException or OperationCanceledException) { }
        Assert.False(ready);
    }
    private static string Fingerprint(X509Certificate2 cert) => Convert.ToHexString(SHA256.HashData(cert.RawData));
    private static X509Certificate2 Identity()
    {
        using var key = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        var request = new CertificateRequest("CN=ClipSync test phone", key, HashAlgorithmName.SHA256);
        using var cert = request.CreateSelfSigned(DateTimeOffset.UtcNow.AddMinutes(-1), DateTimeOffset.UtcNow.AddDays(1));
        return X509CertificateLoader.LoadPkcs12(cert.Export(X509ContentType.Pfx), null, X509KeyStorageFlags.UserKeySet);
    }
    private sealed class Fixture : IDisposable
    {
        public CertificateStore Store { get; }
        public SyncServer Server { get; }
        public Fixture(TimeSpan? duration = null, string? directory = null, TimeSpan? heartbeat = null)
        {
            directory ??= Path.Combine(AppContext.BaseDirectory, "test-identities", Guid.NewGuid().ToString("N"));
            Store = new CertificateStore(directory: directory);
            Server = new SyncServer(Store, 0, advertise: false, pairingDuration: duration, log: _ => { }, heartbeatInterval: heartbeat);
            Server.Start();
        }
        public async Task<Peer> Connect(X509Certificate2 certificate)
        {
            var peer = new Peer();
            try
            {
                using var timeout = new CancellationTokenSource(5000);
                await peer.Tcp.ConnectAsync(IPAddress.Loopback, Server.ListeningPort, timeout.Token);
                peer.Ssl = new SslStream(peer.Tcp.GetStream(), false, (_, remote, _, _) => remote != null &&
                    Convert.ToHexString(SHA256.HashData(remote.GetRawCertData())) == Store.Fingerprint);
                await peer.Ssl.AuthenticateAsClientAsync(new SslClientAuthenticationOptions {
                    TargetHost = "ClipSync", EnabledSslProtocols = SslProtocols.Tls13,
                    ClientCertificates = new X509CertificateCollection { certificate }, CertificateRevocationCheckMode = X509RevocationMode.NoCheck
                }, timeout.Token);
                return peer;
            }
            catch { peer.Dispose(); throw; }
        }
        public async Task<Peer> Pair(X509Certificate2 certificate)
        {
            Server.BeginPairing();
            var peer = await Connect(certificate);
            try {
                var offer = (await peer.Line()).Split('|');
                Assert.Equal("PAIR", offer[0]); Assert.Equal(Store.Fingerprint, offer[1]);
                Assert.Equal(CertificateStore.PairCode(Store.Fingerprint, Fingerprint(certificate)), offer[2]);
                await peer.Send("CONFIRM|" + offer[2]); Assert.Equal("READY", await peer.Line());
                return peer;
            } catch { peer.Dispose(); throw; }
        }
        public void Dispose() { Server.Dispose(); Store.Dispose(); }
    }
    private sealed class Peer : IDisposable
    {
        public TcpClient Tcp { get; } = new();
        public SslStream Ssl { get; set; } = null!;
        public Task Send(string line) => Ssl.WriteAsync(Encoding.ASCII.GetBytes(line + "\n")).AsTask();
        public async Task<string> Line()
        {
            using var timeout = new CancellationTokenSource(5000);
            var bytes = new List<byte>(); var one = new byte[1];
            while (await Ssl.ReadAsync(one, timeout.Token) > 0) {
                if (one[0] == 10) return Encoding.ASCII.GetString(bytes.ToArray());
                bytes.Add(one[0]); if (bytes.Count > 256) throw new InvalidDataException();
            }
            throw new EndOfStreamException();
        }
        public async Task<ClipMessage> Frame()
        {
            using var timeout = new CancellationTokenSource(5000);
            var header = new byte[9]; await Ssl.ReadExactlyAsync(header, timeout.Token);
            var length = System.Buffers.Binary.BinaryPrimitives.ReadInt32BigEndian(header.AsSpan(5));
            Assert.InRange(length, 0, FrameCodec.MaxPayloadLength);
            var payload = new byte[length]; await Ssl.ReadExactlyAsync(payload, timeout.Token);
            return FrameCodec.Decode(header.Concat(payload).ToArray());
        }
        public async Task<string> Text()
        {
            using var timeout = new CancellationTokenSource(5000);
            var reader = new FrameReader(); var buffer = new byte[8192];
            while (true) {
                var count = await Ssl.ReadAsync(buffer, timeout.Token);
                if (count == 0) throw new EndOfStreamException();
                foreach (var frame in reader.Push(buffer[..count])) if (frame.Type == MessageType.Text) return frame.Text!;
            }
        }
        public void Dispose() { Ssl?.Dispose(); Tcp.Dispose(); }
    }
}
