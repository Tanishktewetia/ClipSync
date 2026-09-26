using System.Runtime.InteropServices;
namespace ClipSync.Windows.Transport;

/// <summary>Windows DNS-SD responder owns probing, interface changes, TTLs and goodbye records.</summary>
internal sealed class MdnsAdvertiser : IDisposable
{
    [UnmanagedFunctionPointer(CallingConvention.Winapi)] delegate void Completion(uint status, IntPtr context, IntPtr instance);
    [StructLayout(LayoutKind.Sequential)] struct Request
    {
        public uint Version, InterfaceIndex;
        public IntPtr Instance, Callback, Context, Credentials;
        public int Unicast;
    }
    [DllImport("dnsapi.dll", CharSet = CharSet.Unicode)] static extern IntPtr DnsServiceConstructInstance(string name, string host, IntPtr ipv4, IntPtr ipv6, ushort port, ushort priority, ushort weight, uint count, IntPtr keys, IntPtr values);
    [DllImport("dnsapi.dll")] static extern uint DnsServiceRegister(IntPtr request, IntPtr cancel);
    [DllImport("dnsapi.dll")] static extern uint DnsServiceDeRegister(IntPtr request, IntPtr cancel);
    [DllImport("dnsapi.dll")] static extern void DnsServiceFreeInstance(IntPtr instance);
    static readonly Completion Callback = Completed;
    readonly object _gate = new();
    readonly Action<string> _log;
    IntPtr _request, _instance;
    GCHandle _root;
    bool _pending, _registered, _disposed;
    const uint Pending = 9506;

    public MdnsAdvertiser(int port, Action<string> log)
    {
        _log = log;
        var host = System.Net.Dns.GetHostName().Split('.')[0];
        _instance = DnsServiceConstructInstance($"ClipSync-{Environment.ProcessId}._clipsync._tcp.local", host + ".local", IntPtr.Zero, IntPtr.Zero, checked((ushort)port), 0, 0, 0, IntPtr.Zero, IntPtr.Zero);
        if (_instance == IntPtr.Zero) throw new InvalidOperationException("DNS-SD instance allocation failed");
        _root = GCHandle.Alloc(this);
        _request = Marshal.AllocHGlobal(Marshal.SizeOf<Request>());
        Marshal.StructureToPtr(new Request { Version = 1, Instance = _instance, Callback = Marshal.GetFunctionPointerForDelegate(Callback), Context = GCHandle.ToIntPtr(_root) }, _request, false);
        lock (_gate)
        {
            _pending = true;
            var status = DnsServiceRegister(_request, IntPtr.Zero);
            if (status != Pending) { _pending = false; Cleanup(); _log($"DNS-SD registration unavailable: status={status}"); }
        }
    }
    static void Completed(uint status, IntPtr context, IntPtr instance)
    {
        // No exception may escape a native callback.
        try
        {
            if (GCHandle.FromIntPtr(context).Target is not MdnsAdvertiser self) return;
            lock (self._gate)
            {
                self._pending = false;
                if (self._registered) { self._registered = false; self.Cleanup(); }
                else if (status == 0)
                {
                    self._registered = true; self._log("DNS-SD _clipsync._tcp registration active");
                    if (self._disposed) self.Deregister();
                }
                else { self._log($"DNS-SD registration failed: status={status}"); self.Cleanup(); }
            }
        }
        catch { /* Registration is optional; saved/manual IP remains available. */ }
        finally { if (instance != IntPtr.Zero) DnsServiceFreeInstance(instance); }
    }
    void Deregister()
    {
        if (!_registered || _pending) return;
        _pending = true;
        var status = DnsServiceDeRegister(_request, IntPtr.Zero);
        if (status != Pending) { _pending = false; _registered = false; Cleanup(); }
    }
    void Cleanup()
    {
        if (_request != IntPtr.Zero) { Marshal.FreeHGlobal(_request); _request = IntPtr.Zero; }
        if (_instance != IntPtr.Zero) { DnsServiceFreeInstance(_instance); _instance = IntPtr.Zero; }
        if (_root.IsAllocated) _root.Free();
    }
    public void Dispose() { lock (_gate) { _disposed = true; if (!_pending) { if (_registered) Deregister(); else Cleanup(); } } }
}
