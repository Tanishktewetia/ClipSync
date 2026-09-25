using System.Net.Security;
using System.Net.Sockets;
using System.Security.Authentication;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using ClipSync.Core;
using ClipSync.Windows.Security;

Console.WriteLine("ClipSync.TestClient — type text to send, Ctrl+C to exit");
using var store = new CertificateStore("testclient-identity");
using var tcp = new TcpClient();
await tcp.ConnectAsync("127.0.0.1", 48653);
using var ssl = new SslStream(tcp.GetStream(), false, (_, _, _, _) => true);
await ssl.AuthenticateAsClientAsync(new SslClientAuthenticationOptions
{
    TargetHost = "ClipSync",
    ClientCertificates = new X509CertificateCollection { store.Certificate },
    EnabledSslProtocols = SslProtocols.Tls13,
    CertificateRevocationCheckMode = X509RevocationMode.NoCheck
});

async Task<string?> ReadLineAsync()
{
    var bytes = new List<byte>();
    var one = new byte[1];
    while (await ssl.ReadAsync(one) != 0)
    {
        if (one[0] == (byte)'\n') break;
        bytes.Add(one[0]);
    }
    return Encoding.UTF8.GetString(bytes.ToArray()).Trim('\r', '\ufeff', ' ', '\t');
}

Task WriteLineAsync(string value) => ssl.WriteAsync(Encoding.UTF8.GetBytes(value + "\n")).AsTask();

var hello = await ReadLineAsync();
if (hello?.StartsWith("PAIR|", StringComparison.Ordinal) == true)
{
    var parts = hello.Split('|');
    var code = parts.Length > 2 ? parts[2].Trim() : CertificateStore.PairCode(parts[1], store.Fingerprint);
    Console.WriteLine($"Pairing code for this client: {code}");
    Console.Write("Confirm pairing with ClipSync? [Y/n]: ");
    var answer = Console.ReadLine();
    if (!string.IsNullOrWhiteSpace(answer) && !answer.Equals("y", StringComparison.OrdinalIgnoreCase))
    {
        Console.WriteLine("Pairing cancelled.");
        return 1;
    }
    await WriteLineAsync("CONFIRM|" + code);
    hello = await ReadLineAsync();
}

if (hello != "READY")
{
    Console.WriteLine($"Connection rejected: {hello ?? "no response"}");
    return 2;
}

Console.WriteLine("Connected. Sending lines; incoming clipboard text will be printed.");
_ = Task.Run(async () =>
{
    var reader = new FrameReader();
    var buffer = new byte[8192];
    try
    {
        while (true)
        {
            var count = await ssl.ReadAsync(buffer);
            if (count == 0) break;
            foreach (var message in reader.Push(buffer[..count]))
                if (message.Type == MessageType.Text)
                    Console.WriteLine("[received] " + message.Text);
        }
    }
    catch (Exception ex) { Console.WriteLine("Receive stopped: " + ex.Message); }
});

while (true)
{
    var line = Console.ReadLine();
    if (line is null) break;
    await ssl.WriteAsync(FrameCodec.Encode(new ClipMessage(MessageType.Text, line)));
}

return 0;
