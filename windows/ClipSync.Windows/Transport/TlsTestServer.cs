using System.Net;
using System.IO;
using System.Net.Security;
using System.Net.Sockets;
using System.Security.Authentication;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using ClipSync.Windows.Logging;

namespace ClipSync.Windows.Transport;

/// <summary>Phase 1 throwaway TLS echo server. Replaced by the framed transport in Phase 3.</summary>
public sealed class TlsTestServer : IDisposable
{
    public const int Port = 48653;
    private readonly TcpListener _listener = new(IPAddress.Any, Port);
    private readonly CancellationTokenSource _stop = new();
    private X509Certificate2? _certificate;

    public void Start()
    {
        _certificate = LoadOrCreateCertificate();
        _listener.Start();
        _ = AcceptLoopAsync();
        FileLogger.Instance.Info($"TLS test server listening on port {Port} (TLS 1.3)");
    }

    private async Task AcceptLoopAsync()
    {
        try
        {
            while (!_stop.IsCancellationRequested)
                _ = HandleClientAsync(await _listener.AcceptTcpClientAsync(_stop.Token));
        }
        catch (OperationCanceledException) { }
        catch (Exception ex) { FileLogger.Instance.Error("TLS listener stopped unexpectedly", ex); }
    }

    private async Task HandleClientAsync(TcpClient client)
    {
        using (client)
        try
        {
            await using var stream = new SslStream(client.GetStream(), false);
            await stream.AuthenticateAsServerAsync(new SslServerAuthenticationOptions
            {
                ServerCertificate = _certificate,
                EnabledSslProtocols = SslProtocols.Tls13,
                ClientCertificateRequired = false,
                CertificateRevocationCheckMode = X509RevocationMode.NoCheck,
            });
            using var reader = new StreamReader(stream, Encoding.UTF8, leaveOpen: true);
            await using var writer = new StreamWriter(stream, new UTF8Encoding(false), leaveOpen: true) { AutoFlush = true };
            var text = await reader.ReadLineAsync(_stop.Token);
            if (text is null) return;
            FileLogger.Instance.Info($"TLS test received {text.Length} characters");
            await writer.WriteLineAsync($"pong: {text}");
        }
        catch (Exception ex) when (ex is IOException or AuthenticationException or SocketException)
        { FileLogger.Instance.Error("TLS test client failed; check Windows Firewall for port 48653", ex); }
    }

    private static X509Certificate2 LoadOrCreateCertificate()
    {
        var dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "ClipSync");
        Directory.CreateDirectory(dir);
        var path = Path.Combine(dir, "phase1-test-server.pfx");
        const string password = "clipsync-phase1-dev";
        if (File.Exists(path)) return X509CertificateLoader.LoadPkcs12FromFile(path, password, X509KeyStorageFlags.UserKeySet | X509KeyStorageFlags.PersistKeySet);
        using var rsa = RSA.Create(2048);
        var request = new CertificateRequest("CN=ClipSync Phase 1", rsa, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1);
        request.CertificateExtensions.Add(new X509BasicConstraintsExtension(false, false, 0, false));
        request.CertificateExtensions.Add(new X509KeyUsageExtension(X509KeyUsageFlags.DigitalSignature, false));
        using var cert = request.CreateSelfSigned(DateTimeOffset.UtcNow.AddMinutes(-5), DateTimeOffset.UtcNow.AddYears(2));
        File.WriteAllBytes(path, cert.Export(X509ContentType.Pfx, password));
        return X509CertificateLoader.LoadPkcs12FromFile(path, password, X509KeyStorageFlags.UserKeySet | X509KeyStorageFlags.PersistKeySet);
    }

    public void Dispose()
    {
        _stop.Cancel();
        _listener.Stop();
        _certificate?.Dispose();
        _stop.Dispose();
    }
}



