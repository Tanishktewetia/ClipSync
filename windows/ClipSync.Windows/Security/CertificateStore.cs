using System.IO;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;
namespace ClipSync.Windows.Security;
public sealed class CertificateStore : IDisposable
{
    private readonly string _dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "ClipSync");
    public X509Certificate2 Certificate { get; }
    public string Fingerprint => Convert.ToHexString(SHA256.HashData(Certificate.RawData));
    public string PinnedPeerPath => Path.Combine(_dir, "pinned-peer.txt");
    public string? PinnedPeer => File.Exists(PinnedPeerPath) ? File.ReadAllText(PinnedPeerPath).Trim() : null;
    public CertificateStore(string profile = "identity", string? directory = null){ if(directory is not null) _dir = directory; Directory.CreateDirectory(_dir); var p=Path.Combine(_dir,profile+".pfx"); const string pw="clipsync-dpapi"; if(File.Exists(p)){ var raw=ProtectedData.Unprotect(File.ReadAllBytes(p),null,DataProtectionScope.CurrentUser); Certificate=X509CertificateLoader.LoadPkcs12(raw,pw,X509KeyStorageFlags.UserKeySet|X509KeyStorageFlags.PersistKeySet); } else { using var rsa=RSA.Create(2048); var req=new CertificateRequest("CN=ClipSync",rsa,HashAlgorithmName.SHA256,RSASignaturePadding.Pkcs1); req.CertificateExtensions.Add(new X509BasicConstraintsExtension(false,false,0,false)); req.CertificateExtensions.Add(new X509KeyUsageExtension(X509KeyUsageFlags.DigitalSignature|X509KeyUsageFlags.KeyEncipherment,false)); using var c=req.CreateSelfSigned(DateTimeOffset.UtcNow.AddMinutes(-5),DateTimeOffset.UtcNow.AddYears(5)); File.WriteAllBytes(p,ProtectedData.Protect(c.Export(X509ContentType.Pfx,pw),null,DataProtectionScope.CurrentUser)); Certificate=X509CertificateLoader.LoadPkcs12(ProtectedData.Unprotect(File.ReadAllBytes(p),null,DataProtectionScope.CurrentUser),pw,X509KeyStorageFlags.UserKeySet|X509KeyStorageFlags.PersistKeySet); }}
    public void Pin(string fp)=>File.WriteAllText(PinnedPeerPath,fp);
    public static string PairCode(string a,string b)=> (BitConverter.ToUInt32(SHA256.HashData(Encoding.UTF8.GetBytes(string.CompareOrdinal(a,b)<0?a+b:b+a)),0)%1000000).ToString("D6");
    public void Dispose()=>Certificate.Dispose();
}


