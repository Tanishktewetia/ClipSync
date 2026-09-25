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
    private readonly TcpListener _listener = new(IPAddress.Loopback, Port);
    private readonly CancellationTokenSource _stop = new();
    private readonly CertificateStore _store;
    private readonly List<SslStream> _clients = [];
    private readonly object _gate = new();
    private DateTime _pairUntil;

    public event Action<string>? IncomingText;
    public event Action<bool>? ConnectionChanged;
    public event Action<string>? PairingCodeAvailable;
    public string Status { get; private set; } = "Waiting";
    public string Fingerprint => _store.Fingerprint;

    public SyncServer(CertificateStore store) => _store = store;

    public void Start()
    {
        _listener.Start();
        _ = AcceptLoop();
        Advertise();
        FileLogger.Instance.Info($"mTLS sync server listening on {Port}");
    }

    public void BeginPairing()
    {
        _pairUntil = DateTime.UtcNow.AddMinutes(2);
        Status = "Waiting";
        FileLogger.Instance.Info("Pairing window opened for 2 minutes");
    }

    public string PairCodeFor(string peerFingerprint) => CertificateStore.PairCode(_store.Fingerprint, peerFingerprint);

    private async Task AcceptLoop()
    {
        try
        {
            while (!_stop.IsCancellationRequested)
            {
                var client = await _listener.AcceptTcpClientAsync(_stop.Token);
                _ = Handle(client);
            }
        }
        catch (OperationCanceledException) { }
        catch (Exception ex) { FileLogger.Instance.Error("Listener stopped", ex); }
    }

    private async Task Handle(TcpClient tcp)
    {
        using (tcp)
        {
            SslStream? ssl = null;
            try
            {
                ssl = new SslStream(tcp.GetStream(), leaveInnerStreamOpen: false, (_, certificate, _, _) => certificate is not null);
                await ssl.AuthenticateAsServerAsync(new SslServerAuthenticationOptions
                {
                    ServerCertificate = _store.Certificate,
                    ClientCertificateRequired = true,
                    EnabledSslProtocols = SslProtocols.Tls13,
                    CertificateRevocationCheckMode = X509RevocationMode.NoCheck
                });

                using var peerCertificate = new X509Certificate2(ssl.RemoteCertificate!);
                var peerFingerprint = Convert.ToHexString(SHA256.HashData(peerCertificate.RawData));

                if (_store.PinnedPeer is not null && !peerFingerprint.Equals(_store.PinnedPeer, StringComparison.OrdinalIgnoreCase))
                {
                    FileLogger.Instance.Warn("Rejected client with non-pinned certificate");
                    await WriteLineAsync(ssl, "PAIR_REJECTED");
                    return;
                }

                if (_store.PinnedPeer is null)
                {
                    if (DateTime.UtcNow > _pairUntil)
                    {
                        await WriteLineAsync(ssl, "PAIR_REQUIRED");
                        return;
                    }

                    var pairCode = PairCodeFor(peerFingerprint);
                    PairingCodeAvailable?.Invoke(pairCode);
                    await WriteLineAsync(ssl, $"PAIR|{_store.Fingerprint}|{pairCode}");
                    var confirmation = await ReadLineAsync(ssl);
                    var confirmedCode = confirmation is not null && confirmation.StartsWith("CONFIRM|", StringComparison.Ordinal)
                        ? confirmation[8..].Trim()
                        : string.Empty;

                    if (!string.Equals(confirmedCode, pairCode, StringComparison.Ordinal))
                    {
                        FileLogger.Instance.Warn($"Pairing rejected: received confirmation with {confirmedCode.Length} characters");
                        await WriteLineAsync(ssl, "PAIR_REJECTED");
                        return;
                    }

                    _store.Pin(peerFingerprint);
                    FileLogger.Instance.Info("Peer fingerprint pinned after pairing");
                }

                await WriteLineAsync(ssl, "READY");
                lock (_gate) _clients.Add(ssl);
                ConnectionChanged?.Invoke(true);

                var reader = new FrameReader();
                var buffer = new byte[8192];
                while (!_stop.IsCancellationRequested)
                {
                    var count = await ssl.ReadAsync(buffer, _stop.Token);
                    if (count == 0) break;
                    foreach (var message in reader.Push(buffer[..count]))
                    {
                        if (message.Type == MessageType.Text)
                            IncomingText?.Invoke(message.Text ?? string.Empty);
                    }
                }
            }
            catch (Exception ex) when (ex is IOException or AuthenticationException or SocketException)
            {
                FileLogger.Instance.Warn($"Client disconnected: {ex.GetType().Name}");
            }
            finally
            {
                if (ssl is not null)
                    lock (_gate) _clients.Remove(ssl);
                ConnectionChanged?.Invoke(false);
            }
        }
    }

    private static async Task<string?> ReadLineAsync(Stream stream)
    {
        var bytes = new List<byte>();
        var one = new byte[1];
        while (await stream.ReadAsync(one) != 0)
        {
            if (one[0] == (byte)'\n') break;
            bytes.Add(one[0]);
        }
        return Encoding.UTF8.GetString(bytes.ToArray()).Trim('\r', '\ufeff', ' ', '\t');
    }

    private static Task WriteLineAsync(Stream stream, string value)
    {
        var bytes = Encoding.UTF8.GetBytes(value + "\n");
        return stream.WriteAsync(bytes).AsTask();
    }

    public void Broadcast(string text)
    {
        var frame = FrameCodec.Encode(new ClipMessage(MessageType.Text, text));
        lock (_gate)
        {
            foreach (var client in _clients.ToArray())
            {
                try { client.Write(frame, 0, frame.Length); }
                catch { _clients.Remove(client); }
            }
        }
    }

    private void Advertise()
    {
        try
        {
            using var udp = new UdpClient();
            var payload = Encoding.UTF8.GetBytes("_clipsync._tcp|ClipSync|" + Port);
            udp.Send(payload, payload.Length, new IPEndPoint(IPAddress.Parse("224.0.0.251"), 5353));
        }
        catch (Exception ex) { FileLogger.Instance.Warn("mDNS advertisement unavailable: " + ex.Message); }
    }

    public void Dispose()
    {
        _stop.Cancel();
        _listener.Stop();
        lock (_gate)
        {
            foreach (var client in _clients) client.Dispose();
            _clients.Clear();
        }
        _stop.Dispose();
    }
}
