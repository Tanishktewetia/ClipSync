using System.Runtime.InteropServices;
using System.Text;
using System.Windows;
using System.Windows.Interop; using System.Windows.Threading;
using ClipSync.Windows.Logging;
namespace ClipSync.Windows.Clipboard;
public sealed class ClipboardWatcher : IDisposable
{
    private const int WM_CLIPBOARDUPDATE=0x031D; private const int WM_CLOSE=0x0010;
    private readonly HwndSource _source; private readonly DispatcherTimer _debounce; private bool _writing; private string? _last;
    public event Action<string>? TextChanged;
    public ClipboardWatcher(){ var p=new HwndSourceParameters("ClipSyncClipboardWatcher"){Width=0,Height=0,WindowStyle=0x800000}; _source=new HwndSource(p); _source.AddHook(WndProc); AddClipboardFormatListener(_source.Handle); _debounce=new DispatcherTimer{Interval=TimeSpan.FromMilliseconds(125)}; _debounce.Tick+=(_,_)=>{_debounce.Stop(); ReadAndRaise();}; }
    private IntPtr WndProc(IntPtr h,int msg,IntPtr w,IntPtr l,ref bool handled){ if(msg==WM_CLIPBOARDUPDATE&&!_writing){_debounce.Stop();_debounce.Start();handled=true;} return IntPtr.Zero; }
    private void ReadAndRaise(){ for(var i=0;i<5;i++){try{ if(!System.Windows.Clipboard.ContainsText()) return; var t=System.Windows.Clipboard.GetText(); if(string.IsNullOrEmpty(t)||t==_last)return;_last=t;TextChanged?.Invoke(t);return;}catch(COMException){Thread.Sleep(25);}} FileLogger.Instance.Warn("Clipboard remained locked after retries"); }
    public void WriteText(string text){try{_writing=true;System.Windows.Clipboard.SetText(text);_last=text;}catch(Exception ex){FileLogger.Instance.Error("Clipboard write failed",ex);}finally{_writing=false;}}
    [DllImport("user32.dll")] static extern bool AddClipboardFormatListener(IntPtr hwnd); [DllImport("user32.dll")] static extern bool RemoveClipboardFormatListener(IntPtr hwnd);
    public void Dispose(){_debounce.Stop();RemoveClipboardFormatListener(_source.Handle);_source.RemoveHook(WndProc);_source.Dispose();}
}
