using System.IO;
using System.Net;
using System.Net.Security;
using System.Net.Sockets;
using System.Security.Authentication;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using System.Threading.Channels;
using ClipSync.Core;
using ClipSync.Windows.Logging;
using ClipSync.Windows.Security;

namespace ClipSync.Windows.Transport;

public sealed class SyncServer : IDisposable
{
    public const int Port = 48653;
    private readonly TcpListener _listener;
    private readonly CancellationTokenSource _stop = new();
    private readonly CertificateStore _store;
    private readonly List<SslStream> _clients = [];
    private readonly object _gate = new();
    private readonly Channel<ClipMessage> _outgoing = Channel.CreateBounded<ClipMessage>(new BoundedChannelOptions(1) { SingleReader = true, FullMode = BoundedChannelFullMode.DropOldest });
    private readonly SemaphoreSlim _pairGate = new(1, 1);
    private readonly bool _advertise;
    private readonly Action<string> _info;
    private readonly Action<string> _warn;
    private readonly TimeSpan _pairingDuration;
    private readonly TimeSpan _heartbeatInterval;
    private DateTime _pairUntil;
    private long _pairGeneration;
    private readonly HashSet<SslStream> _modern = [];
    private readonly Dictionary<SslStream, long> _pong = [];
    private readonly ReconnectJournal _journal;
    private MdnsAdvertiser? _mdns;
    public bool ReplayOnConnect { get; set; } = true;
    private bool _paused;
    public bool Paused { get { lock (_gate) return _paused; } set { lock (_gate) { _paused = value; if(value) _journal.Clear(); } } }

    public event Action<string>? IncomingText;
    public Func<string, bool>? ClipboardSink { get; set; }
    public event Action<bool>? ConnectionChanged;
    public event Action<string>? PairingCodeAvailable;
    public string Status { get; private set; } = "Waiting";
    public string Fingerprint => _store.Fingerprint;
    public int ListeningPort => ((IPEndPoint)_listener.LocalEndpoint).Port;

    // Optional ephemeral port/isolated identity let integration tests avoid the user's app.
    public SyncServer(CertificateStore store, int port = Port, bool advertise = true, TimeSpan? pairingDuration = null, Action<string>? log = null, TimeSpan? heartbeatInterval = null)
    {
        _store = store;
        _journal = new ReconnectJournal(store.Fingerprint);
        _info = log ?? (message => FileLogger.Instance.Info(message));
        _warn = log ?? (message => FileLogger.Instance.Warn(message));
        _listener = new TcpListener(IPAddress.Any, port);
        _advertise = advertise;
        _pairingDuration = pairingDuration ?? TimeSpan.FromMinutes(2);
        _heartbeatInterval = heartbeatInterval ?? TimeSpan.FromSeconds(15);
        if (_heartbeatInterval <= TimeSpan.Zero) throw new ArgumentOutOfRangeException(nameof(heartbeatInterval));
    }
    public void Start()
    {
        _listener.Start();
        _ = Task.Run(AcceptLoop);
        _ = Task.Run(BroadcastLoop);
        if (_advertise) Advertise();
        _info($"mTLS sync server listening on Wi-Fi/LAN port {ListeningPort}");
    }
    public void BeginPairing()
    {
        lock (_gate) { _pairUntil = DateTime.UtcNow.Add(_pairingDuration); _pairGeneration++; }
        _info("Explicit pairing window opened");
    }
    public string PairCodeFor(string peerFingerprint) => CertificateStore.PairCode(_store.Fingerprint, peerFingerprint);
    private bool ValidatePeer(X509Certificate? certificate)
    {
        if (certificate is null) return false;
        var fp = Convert.ToHexString(SHA256.HashData(certificate.GetRawCertData()));
        lock (_gate)
            return string.Equals(_store.PinnedPeer, fp, StringComparison.OrdinalIgnoreCase) || DateTime.UtcNow < _pairUntil;
    }
    private async Task AcceptLoop()
    {
        try
        {
            while (!_stop.IsCancellationRequested)
                _ = Handle(await _listener.AcceptTcpClientAsync(_stop.Token));
        }
        catch (OperationCanceledException) { }
        catch (SocketException) when (_stop.IsCancellationRequested) { }
        catch (Exception ex) { _warn("Listener stopped: " + ex.GetType().Name); }
    }
    private async Task Handle(TcpClient tcp)
    {
        using (tcp)
        using (var ssl = new SslStream(tcp.GetStream(), false, (_, certificate, _, _) => ValidatePeer(certificate)))
        {
            using var lifetime = CancellationTokenSource.CreateLinkedTokenSource(_stop.Token);
            try
            {
                tcp.NoDelay = true;
                tcp.SendTimeout = 3000;
                ssl.WriteTimeout = 3000;
                using (var handshake = CancellationTokenSource.CreateLinkedTokenSource(_stop.Token))
                {
                    handshake.CancelAfter(TimeSpan.FromSeconds(10));
                    await ssl.AuthenticateAsServerAsync(new SslServerAuthenticationOptions
                    {
                        ServerCertificate = _store.Certificate,
                        ClientCertificateRequired = true,
                        EnabledSslProtocols = SslProtocols.Tls13,
                        CertificateRevocationCheckMode = X509RevocationMode.NoCheck
                    }, handshake.Token);
                }
                var peer = Convert.ToHexString(SHA256.HashData(ssl.RemoteCertificate!.GetRawCertData()));
                bool needsPairing;
                lock (_gate) needsPairing = DateTime.UtcNow < _pairUntil || !string.Equals(_store.PinnedPeer, peer, StringComparison.OrdinalIgnoreCase);
                if (needsPairing)
                {
                    if (!await _pairGate.WaitAsync(0, _stop.Token)) { await WriteLineAsync(ssl, "PAIR_REJECTED", _stop.Token); return; }
                    try
                    {
                        DateTime deadline; long generation;
                        lock (_gate) { deadline = _pairUntil; generation = _pairGeneration; }
                        var remaining = deadline - DateTime.UtcNow;
                        if (remaining <= TimeSpan.Zero) return;
                        using var pairing = CancellationTokenSource.CreateLinkedTokenSource(_stop.Token);
                        pairing.CancelAfter(remaining);
                        var code = PairCodeFor(peer);
                        PairingCodeAvailable?.Invoke(code);
                        await WriteLineAsync(ssl, $"PAIR|{_store.Fingerprint}|{code}", pairing.Token);
                        var confirmation = await ReadLineAsync(ssl, pairing.Token);
                        if (!string.Equals(confirmation, "CONFIRM|" + code, StringComparison.Ordinal))
                        {
                            await WriteLineAsync(ssl, "PAIR_REJECTED", pairing.Token); return;
                        }
                        lock (_gate)
                        {
                            if (generation != _pairGeneration || DateTime.UtcNow >= _pairUntil) return;
                            if (!string.Equals(_store.PinnedPeer, peer, StringComparison.OrdinalIgnoreCase)) _journal.Clear();
                            _store.Pin(peer); // Old trust survives a rejected/cancelled pairing.
                            _pairUntil = DateTime.MinValue;
                            foreach (var old in _clients) old.Dispose();
                            _clients.Clear();
                        }
                        _info("Peer fingerprint pinned after confirmed pairing");
                    }
                    finally { _pairGate.Release(); }
                }
                lock (_gate)
                {
                    // Another confirmed pairing could have replaced trust since TLS completed.
                    if (!string.Equals(_store.PinnedPeer, peer, StringComparison.OrdinalIgnoreCase)) return;
                    foreach (var old in _clients) old.Dispose();
                    _clients.Clear();
                    var ready = Encoding.ASCII.GetBytes("READY\n");
                    ssl.Write(ready);
                    _clients.Add(ssl);
                    Status = "Connected";
                }
                ConnectionChanged?.Invoke(true);
                _ = Heartbeat(ssl, lifetime.Token);
                var reader = new FrameReader();
                var buffer = new byte[8192];
                while (!_stop.IsCancellationRequested)
                {
                    var count = await ssl.ReadAsync(buffer, _stop.Token);
                    if (count == 0) break;
                    foreach (var message in reader.Push(buffer[..count]))
                    {
                        string? incoming = null;
                        ClipMessage? accepted = null;
                        lock (_gate)
                        {
                            if (message.Type == MessageType.Hello && SessionProtocol.Origin(message) is { } origin && _modern.Add(ssl))
                            {
                                _journal.Observe(message.Lamport);
                                _pong[ssl] = Environment.TickCount64;
                                ssl.Write(FrameCodec.Encode(SessionProtocol.Hello(_journal.Latest, ReplayOnConnect && !_paused)));
                                if (!_paused && SessionProtocol.Replay(message) && _journal.Latest is { } newest && SessionProtocol.Newer(newest, message.Lamport, origin)) ssl.Write(FrameCodec.Encode(newest));
                            }
                            else if (message.Type == MessageType.Ping && _modern.Contains(ssl)) ssl.Write(FrameCodec.Encode(new ClipMessage(MessageType.Pong)));
                            else if (message.Type == MessageType.Pong) _pong[ssl] = Environment.TickCount64;
                            else if (!_paused && message.Type == MessageType.State && _modern.Contains(ssl) && _journal.Accept(message)) { accepted = message; incoming = message.Text ?? ""; }
                            else if (!_paused && message.Type == MessageType.Text) { incoming = message.Text ?? ""; accepted = _journal.Local(incoming); }
                        }
                        if (incoming is not null) {
                            if (ClipboardSink?.Invoke(incoming) == false) {
                                lock (_gate) { if (_journal.Latest == accepted) _journal.Clear(); }
                                throw new IOException("Clipboard apply failed; reconnect for latest replay");
                            }
                            IncomingText?.Invoke(incoming);
                        }
                    }
                }
            }
            catch (Exception ex) when (ex is IOException or AuthenticationException or SocketException or OperationCanceledException or ObjectDisposedException or ArgumentException)
            {
                _warn("Client connection ended: " + ex.GetType().Name);
            }
            finally
            {
                lifetime.Cancel();
                bool removed; bool connected;
                lock (_gate)
                {
                    _modern.Remove(ssl); _pong.Remove(ssl);
                    removed = _clients.Remove(ssl); connected = _clients.Count != 0;
                    Status = connected ? "Connected" : "Waiting";
                }
                if (removed) ConnectionChanged?.Invoke(connected);
            }
        }
    }
    private static async Task<string> ReadLineAsync(Stream stream, CancellationToken cancellation)
    {
        var bytes = new List<byte>(); var one = new byte[1];
        while (await stream.ReadAsync(one, cancellation) != 0)
        {
            if (one[0] == (byte)'\n') return Encoding.ASCII.GetString(bytes.ToArray()).TrimEnd('\r');
            if (bytes.Count >= 256 || (one[0] < 32 && one[0] != 13) || one[0] > 126) throw new InvalidDataException("Invalid pairing response");
            bytes.Add(one[0]);
        }
        throw new EndOfStreamException();
    }
    private static Task WriteLineAsync(Stream stream, string value, CancellationToken cancellation) =>
        stream.WriteAsync(Encoding.ASCII.GetBytes(value + "\n"), cancellation).AsTask();
    public void Broadcast(string text)
    {
        if (Encoding.UTF8.GetByteCount(text) > SessionProtocol.MaxText)
        {
            _warn("Clipboard skipped: exceeds current CSP1 text limit"); return;
        }
        lock (_gate) { if (!_paused) _outgoing.Writer.TryWrite(_journal.Local(text)); }
    }
    private async Task BroadcastLoop()
    {
        try {
            await foreach (var text in _outgoing.Reader.ReadAllAsync(_stop.Token)) BroadcastNow(text);
        } catch (OperationCanceledException) { }
    }
    private void BroadcastNow(ClipMessage text)
    {

        bool removed = false; bool connected;
        lock (_gate)
        {
            if (_paused || _journal.Latest != text) return;
            foreach (var client in _clients.ToArray())
            {
                try { client.Write(FrameCodec.Encode(_modern.Contains(client) ? text : new ClipMessage(MessageType.Text, text.Text))); }
                catch { client.Dispose(); _clients.Remove(client); removed = true; }
            }
            connected = _clients.Count != 0;
            Status = connected ? "Connected" : "Waiting";
        }
        if (removed) ConnectionChanged?.Invoke(connected);
    }
    private async Task Heartbeat(SslStream ssl, CancellationToken stop)
    {
        try {
            while (!stop.IsCancellationRequested) {
                await Task.Delay(_heartbeatInterval, stop);
                lock (_gate) {
                    if (!_clients.Contains(ssl)) return;
                    if (!_modern.Contains(ssl)) continue;
                    if (Environment.TickCount64 - _pong.GetValueOrDefault(ssl) >= _heartbeatInterval.TotalMilliseconds * 2) { ssl.Dispose(); return; }
                    ssl.Write(FrameCodec.Encode(new ClipMessage(MessageType.Ping)));
                }
            }
        } catch (OperationCanceledException) { }
        catch (Exception ex) when (ex is IOException or ObjectDisposedException) { ssl.Dispose(); }
    }
    private void Advertise()
    {
        try { _mdns = new MdnsAdvertiser(ListeningPort, _info); }
        catch (Exception ex) { _warn("DNS-SD unavailable: " + ex.GetType().Name); }
    }
    public void Dispose()
    {
        _mdns?.Dispose();
        _outgoing.Writer.TryComplete(); _stop.Cancel(); _listener.Stop();
        lock (_gate) { foreach (var client in _clients) client.Dispose(); _clients.Clear(); }
    }
}
