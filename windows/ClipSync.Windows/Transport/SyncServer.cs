using System.IO;
using System.Net;
using System.Net.Security;
using System.Net.Sockets;
using System.Security.Authentication;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;
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
    private readonly SemaphoreSlim _pairGate = new(1, 1);
    private readonly bool _advertise;
    private readonly Action<string> _info;
    private readonly Action<string> _warn;
    private readonly TimeSpan _pairingDuration;
    private DateTime _pairUntil;
    private long _pairGeneration;

    public event Action<string>? IncomingText;
    public event Action<bool>? ConnectionChanged;
    public event Action<string>? PairingCodeAvailable;
    public string Status { get; private set; } = "Waiting";
    public string Fingerprint => _store.Fingerprint;
    public int ListeningPort => ((IPEndPoint)_listener.LocalEndpoint).Port;

    // Optional ephemeral port/isolated identity let integration tests avoid the user's app.
    public SyncServer(CertificateStore store, int port = Port, bool advertise = true, TimeSpan? pairingDuration = null, Action<string>? log = null)
    {
        _store = store;
        _info = log ?? (message => FileLogger.Instance.Info(message));
        _warn = log ?? (message => FileLogger.Instance.Warn(message));
        _listener = new TcpListener(IPAddress.Any, port);
        _advertise = advertise;
        _pairingDuration = pairingDuration ?? TimeSpan.FromMinutes(2);
    }
    public void Start()
    {
        _listener.Start();
        _ = AcceptLoop();
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
            try
            {
                tcp.NoDelay = true;
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
                var reader = new FrameReader();
                var buffer = new byte[8192];
                while (!_stop.IsCancellationRequested)
                {
                    var count = await ssl.ReadAsync(buffer, _stop.Token);
                    if (count == 0) break;
                    foreach (var message in reader.Push(buffer[..count]))
                        if (message.Type == MessageType.Text) IncomingText?.Invoke(message.Text ?? string.Empty);
                }
            }
            catch (Exception ex) when (ex is IOException or AuthenticationException or SocketException or OperationCanceledException or ObjectDisposedException or ArgumentException)
            {
                _warn("Client connection ended: " + ex.GetType().Name);
            }
            finally
            {
                bool removed; bool connected;
                lock (_gate)
                {
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
        if (Encoding.UTF8.GetByteCount(text) > FrameCodec.MaxPayloadLength)
        {
            _warn("Clipboard skipped: exceeds current CSP1 text limit"); return;
        }
        var frame = FrameCodec.Encode(new ClipMessage(MessageType.Text, text));
        bool removed = false; bool connected;
        lock (_gate)
        {
            foreach (var client in _clients.ToArray())
            {
                try { client.Write(frame); }
                catch { client.Dispose(); _clients.Remove(client); removed = true; }
            }
            connected = _clients.Count != 0;
            Status = connected ? "Connected" : "Waiting";
        }
        if (removed) ConnectionChanged?.Invoke(connected);
    }
    private void Advertise()
    {
        try
        {
            using var udp = new UdpClient();
            var payload = Encoding.UTF8.GetBytes("_clipsync._tcp|ClipSync|" + ListeningPort);
            udp.Send(payload, payload.Length, new IPEndPoint(IPAddress.Parse("224.0.0.251"), 5353));
        }
        catch (Exception ex) { _warn("mDNS advertisement unavailable: " + ex.GetType().Name); }
    }
    public void Dispose()
    {
        _stop.Cancel(); _listener.Stop();
        lock (_gate) { foreach (var client in _clients) client.Dispose(); _clients.Clear(); }
    }
}
