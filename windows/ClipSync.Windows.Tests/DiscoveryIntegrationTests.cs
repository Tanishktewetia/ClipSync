using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using ClipSync.Windows.Security;
using ClipSync.Windows.Transport;
using Xunit;
namespace ClipSync.Windows.Tests;

public sealed class DiscoveryIntegrationTests
{
    // Native host-side checks only; Android NSD and physical Wi-Fi still need a phone test.
    [Fact] public async Task NativeDnsSdAdvertisesResolvableIpv4AndListeningPort()
    {
        var directory = Path.Combine(AppContext.BaseDirectory, "test-identities", Guid.NewGuid().ToString("N"));
        using var store = new CertificateStore(directory: directory);
        var outcome = new TaskCompletionSource<string>(TaskCreationOptions.RunContinuationsAsynchronously);
        using var server = new SyncServer(store, 0, advertise: true, log: line => {
            if (line.Contains("DNS-SD", StringComparison.Ordinal)) outcome.TrySetResult(line);
        });
        server.Start();
        Assert.Equal("DNS-SD _clipsync._tcp registration active", await outcome.Task.WaitAsync(TimeSpan.FromSeconds(15)));
        var endpoint = await NativeResolve.Start($"ClipSync-{Environment.ProcessId}._clipsync._tcp.local").WaitAsync(TimeSpan.FromSeconds(15));
        Assert.Equal(server.ListeningPort, endpoint.Port);
        Assert.Equal(AddressFamily.InterNetwork, endpoint.Address.AddressFamily);
        using var tcp = new TcpClient(); using var deadline = new CancellationTokenSource(3000);
        await tcp.ConnectAsync(endpoint, deadline.Token);
        Assert.True(tcp.Connected);
    }
    sealed class NativeResolve
    {
        [UnmanagedFunctionPointer(CallingConvention.Winapi)] delegate void Complete(uint status, IntPtr context, IntPtr instance);
        [StructLayout(LayoutKind.Sequential)] struct Request { public uint Version, InterfaceIndex; public IntPtr Name, Callback, Context; }
        [DllImport("dnsapi.dll")] static extern uint DnsServiceResolve(IntPtr request, IntPtr cancel);
        [DllImport("dnsapi.dll")] static extern void DnsServiceFreeInstance(IntPtr instance);
        static readonly Complete Callback = OnComplete;
        readonly TaskCompletionSource<IPEndPoint> _result = new(TaskCreationOptions.RunContinuationsAsynchronously);
        GCHandle _root; IntPtr _request, _name, _cancel;
        public static Task<IPEndPoint> Start(string name)
        {
            var self = new NativeResolve(); self._root = GCHandle.Alloc(self);
            self._name = Marshal.StringToHGlobalUni(name); self._request = Marshal.AllocHGlobal(Marshal.SizeOf<Request>());
            Marshal.StructureToPtr(new Request { Version = 1, Name = self._name, Callback = Marshal.GetFunctionPointerForDelegate(Callback), Context = GCHandle.ToIntPtr(self._root) }, self._request, false);
            self._cancel = Marshal.AllocHGlobal(IntPtr.Size); Marshal.WriteIntPtr(self._cancel, IntPtr.Zero);
            var status = DnsServiceResolve(self._request, self._cancel);
            if (status != 9506) { self._result.TrySetException(new IOException($"Resolve status {status}")); self.Free(); }
            return self._result.Task;
        }
        static void OnComplete(uint status, IntPtr context, IntPtr instance)
        {
            var self = (NativeResolve)GCHandle.FromIntPtr(context).Target!;
            try {
                if (status != 0 || instance == IntPtr.Zero) throw new IOException($"Resolve callback status {status}");
                var ipv4 = Marshal.ReadIntPtr(instance, IntPtr.Size * 2);
                if (ipv4 == IntPtr.Zero) throw new IOException("DNS-SD has no IPv4 address");
                var bytes = new byte[4]; Marshal.Copy(ipv4, bytes, 0, 4);
                var port = (ushort)Marshal.ReadInt16(instance, IntPtr.Size * 4);
                self._result.TrySetResult(new IPEndPoint(new IPAddress(bytes), port));
            } catch (Exception e) { self._result.TrySetException(e); }
            finally { if (instance != IntPtr.Zero) DnsServiceFreeInstance(instance); self.Free(); }
        }
        void Free() { Marshal.FreeHGlobal(_request); Marshal.FreeHGlobal(_name); Marshal.FreeHGlobal(_cancel); _root.Free(); }
    }
}
