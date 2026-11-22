using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Web.Script.Serialization;
using System.Windows.Forms;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;

[assembly: AssemblyTitle("Noble Studio")]
[assembly: AssemblyProduct("Noble Studio")]
[assembly: AssemblyDescription("Windows photo and video editing")]

internal sealed partial class NobleStudio : Form
{
    private static readonly string Package = AppDomain.CurrentDomain.BaseDirectory;
    private static readonly string Resources = Path.Combine(Package, "resources", "app");
    private const string Home = "/desktop/home.html";
    private const int DockHeaderHeight = 36;
    private const int DwmCaptionColor = 35;
    private const int DwmTextColor = 36;
    private readonly JavaScriptSerializer json = new JavaScriptSerializer { MaxJsonLength = 16 * 1024 * 1024 };
    private readonly string profile;
    private readonly string settingsProfile;
    private readonly string origin;
    private readonly string model;
    private readonly string debugPort;
    private readonly Label startup;
    private readonly Dictionary<string, WebView2> views = new Dictionary<string, WebView2>();
    private readonly Dictionary<string, Task<WebView2>> loading = new Dictionary<string, Task<WebView2>>();
    private readonly Dictionary<string, HostedEditor> editors = new Dictionary<string, HostedEditor>();
    private readonly Dictionary<string, WebView2> mcpRequests = new Dictionary<string, WebView2>();
    private Process service;
    private CoreWebView2Environment environment;
    private WebView2 active;
    private TaskCompletionSource<bool> photoCloseCheck;
    private bool photoCloseConfirmed;
    private string nativeEditor;
    private int workspaceVersion;
    private bool pickerOpen, closingEditors, closed;

    [DllImport("shell32.dll", CharSet = CharSet.Unicode)]
    private static extern int SetCurrentProcessExplicitAppUserModelID(string id);

    [DllImport("dwmapi.dll")]
    private static extern int DwmSetWindowAttribute(IntPtr window, int attribute, ref int value, int size);

    [STAThread]
    private static void Main(string[] args)
    {
        SetCurrentProcessExplicitAppUserModelID("Honorable.NobleStudio");
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        string profile = Option(args, "user-data-dir") ?? Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Noble Studio", "Profile");
        string key;
        using (var sha = SHA256.Create())
            key = Convert.ToBase64String(sha.ComputeHash(Encoding.UTF8.GetBytes(Path.GetFullPath(profile).ToUpperInvariant()))).Replace("/", "_");
        using (var instance = new Mutex(false, @"Local\Honorable.NobleStudio." + key))
        {
            try { if (!instance.WaitOne(0)) return; }
            catch (AbandonedMutexException) { }
            try { Application.Run(new NobleStudio(args, profile)); }
            finally { instance.ReleaseMutex(); }
        }
    }

    private NobleStudio(string[] args, string profilePath)
    {
        profile = profilePath;
        settingsProfile = Option(args,"user-data-dir") == null ? Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),"Noble Studio") : profile;
        origin = "http://127.0.0.1:" + Port(Option(args, "studio-port"), 4184);
        debugPort = Option(args, "remote-debugging-port");
        model = Option(args, "model") ?? Environment.GetEnvironmentVariable("NOBLE_EDIT_MODEL") ?? "qwen3:4b";
        Text = "Noble Studio";
        Icon = new Icon(Path.Combine(Package, "Noble.ico"));
        BackColor = Color.FromArgb(40, 40, 40);
        ForeColor = Color.WhiteSmoke;
        AutoScaleMode = AutoScaleMode.Dpi;
        MinimumSize = new Size(780, 520);
        Size = new Size(1360, 900);
        StartPosition = FormStartPosition.CenterScreen;
        startup = new Label {
            Text = "Starting Noble Studio…", Dock = DockStyle.Fill, TextAlign = ContentAlignment.MiddleCenter,
            Font = new Font("Segoe UI", 13f)
        };
        Controls.Add(startup);
        Shown += async delegate { await Initialize(); };
    }

    protected override void OnHandleCreated(EventArgs e)
    {
        base.OnHandleCreated(e);
        if (SystemInformation.HighContrast) return;
        int caption = ColorTranslator.ToWin32(Color.FromArgb(53, 53, 53));
        int text = ColorTranslator.ToWin32(Color.WhiteSmoke);
        DwmSetWindowAttribute(Handle, DwmCaptionColor, ref caption, sizeof(int));
        DwmSetWindowAttribute(Handle, DwmTextColor, ref text, sizeof(int));
    }

    private static string Option(string[] args, string name)
    {
        string prefix = "--" + name + "=";
        foreach (string arg in args)
            if (arg.StartsWith(prefix, StringComparison.Ordinal)) return arg.Substring(prefix.Length);
        return null;
    }

    private static int Port(string text, int fallback)
    {
        int port;
        return int.TryParse(text, out port) && port >= 1024 && port <= 65535 ? port : fallback;
    }

    private async Task Initialize()
    {
        try
        {
            Directory.CreateDirectory(profile);
            Directory.CreateDirectory(settingsProfile);
            var options = new CoreWebView2EnvironmentOptions();
            if (Port(debugPort, 0) > 0) options.AdditionalBrowserArguments = "--remote-debugging-port=" + Port(debugPort, 0);
            environment = await CoreWebView2Environment.CreateAsync(null, Path.Combine(profile, "WebView2"), options);
            await StartService();
            if (!closed) await ShowWorkspace("home", Home);
        }
        catch (WebView2RuntimeNotFoundException)
        {
            startup.Text = "Install the Microsoft Edge WebView2 Runtime, then reopen Noble Studio.";
        }
        catch (Exception error)
        {
            if (!closed) startup.Text = "Noble Studio could not start." + Environment.NewLine + error.Message;
        }
    }

    private async Task StartService()
    {
        var ready = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
        var info = HiddenProcess(Path.Combine(Package, "resources", "runtime", "node.exe"),
            Quote(Path.Combine(Resources, "studio-service.mjs")) + " --port " + new Uri(origin).Port);
        info.EnvironmentVariables.Remove("NODE_OPTIONS");
        info.EnvironmentVariables.Remove("NODE_PATH");
        info.EnvironmentVariables["NOBLE_EDIT_MODEL"] = model;
        info.EnvironmentVariables["NOBLE_WINDOWS_DESKTOP"] = "1";
        service = Process.Start(info);
        string error = null;
        service.ErrorDataReceived += (_, e) => { if (!string.IsNullOrWhiteSpace(e.Data)) error = e.Data; };
        service.OutputDataReceived += (_, e) =>
        {
            if (e.Data == null) return;
            if (e.Data.StartsWith("Noble browser review: " + origin + "/", StringComparison.Ordinal)) ready.TrySetResult(true);
            else if (e.Data.StartsWith("{", StringComparison.Ordinal))
            {
                var line = e.Data;
                OnUi(() => DispatchMcp(line));
            }
        };
        service.EnableRaisingEvents = true;
        service.Exited += delegate
        {
            if (!ready.TrySetException(new InvalidOperationException(error ?? "The editing service stopped. Check if the local port is in use.")))
                OnUi(() => ShowError("The local editing service stopped. Close and reopen Noble Studio."));
        };
        service.BeginOutputReadLine();
        service.BeginErrorReadLine();
        if (await Task.WhenAny(ready.Task, Task.Delay(30000)) != ready.Task)
            throw new InvalidOperationException("The local editing service did not start within 30 seconds.");
        await ready.Task;
    }

    private static ProcessStartInfo HiddenProcess(string executable, string arguments)
    {
        return new ProcessStartInfo(executable, arguments) {
            UseShellExecute = false, CreateNoWindow = true, WorkingDirectory = Path.GetDirectoryName(executable),
            RedirectStandardInput = true, RedirectStandardOutput = true, RedirectStandardError = true
        };
    }

    private static string Quote(string text)
    {
        if (text.IndexOf('"') >= 0 || text.EndsWith("\\", StringComparison.Ordinal))
            throw new ArgumentException("Invalid Windows process argument.");
        return "\"" + text + "\"";
    }

    private void OnUi(Action action)
    {
        if (closed || !IsHandleCreated || IsDisposed) return;
        try { BeginInvoke(action); }
        catch (InvalidOperationException) { }
    }

    private async Task<WebView2> CreateWorkspace(string kind, string path)
    {
        var view = new WebView2 { Dock = DockStyle.Fill, DefaultBackgroundColor = BackColor, Visible = false };
        Controls.Add(view);
        try
        {
            await view.EnsureCoreWebView2Async(environment);
            var core = view.CoreWebView2;
            core.Settings.AreDevToolsEnabled = Port(debugPort, 0) > 0;
            core.Settings.AreDefaultContextMenusEnabled = false;
            core.Settings.AreHostObjectsAllowed = false;
            core.Settings.IsStatusBarEnabled = false;
            core.Settings.AreBrowserAcceleratorKeysEnabled = false;
            core.NewWindowRequested += (_, e) => e.Handled = true;
            core.PermissionRequested += (_, e) => e.State = CoreWebView2PermissionState.Deny;
            core.NavigationStarting += (_, e) =>
            {
                Uri uri;
                if (IsLocal(e.Uri, out uri) && uri.AbsolutePath == PathFor(kind)) return;
                e.Cancel = true;
                string requested = e.Uri;
                OnUi(async () => await Route(view, requested));
            };
            core.FrameNavigationStarting += (_, e) => e.Cancel = true;
            core.AddWebResourceRequestedFilter("*", CoreWebView2WebResourceContext.All, CoreWebView2WebResourceRequestSourceKinds.All);
            core.WebResourceRequested += (_, e) => HandleResource(view, e);
            core.WebMessageReceived += (_, e) =>
            {
                Uri source;
                if (IsLocal(e.Source, out source) && source.AbsolutePath == PathFor(kind))
                {
                    string message = e.WebMessageAsJson;
                    OnUi(async () => await FromRenderer(view,kind,message));
                }
            };
            core.DownloadStarting += (_, e) => e.Cancel = true;
            await core.AddScriptToExecuteOnDocumentCreatedAsync(File.ReadAllText(Path.Combine(Resources, "native-bridge.js")));
            var navigated = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
            EventHandler<CoreWebView2NavigationCompletedEventArgs> finish =
                (_, e) => navigated.TrySetResult(e.IsSuccess);
            core.NavigationCompleted += finish;
            core.Navigate(origin + path);
            if (await Task.WhenAny(navigated.Task, Task.Delay(30000)) != navigated.Task || !await navigated.Task)
                throw new InvalidOperationException("Noble could not load the " + kind + " workspace.");
            core.NavigationCompleted -= finish;
            views.Add(kind, view);
            return view;
        }
        catch
        {
            view.Dispose();
            throw;
        }
    }

    private static string PathFor(string kind) { return kind == "home" ? Home : "/" + kind + "/"; }

    private bool IsLocal(string text, out Uri uri)
    {
        return Uri.TryCreate(text, UriKind.Absolute, out uri) &&
            uri.GetLeftPart(UriPartial.Authority) == origin && string.IsNullOrEmpty(uri.UserInfo);
    }

    private async Task<WebView2> ShowWorkspace(string kind, string path)
    {
        int version = ++workspaceVersion;
        HideEditor();
        WebView2 view;
        bool existing = views.TryGetValue(kind,out view);
        if (!existing)
        {
            Task<WebView2> task;
            if (!loading.TryGetValue(kind,out task))
            {
                task = CreateWorkspace(kind,path);
                loading.Add(kind,task);
            }
            try { view = await task; }
            finally { loading.Remove(kind); }
        }
        if (closed || version != workspaceVersion) return view;
        if (active != null && active != view)
        {
            Send(active,"stop",null);
            active.CoreWebView2.IsMuted = true;
            active.Visible = false;
        }
        active = view;
        active.CoreWebView2.IsMuted = false;
        active.Visible = true;
        active.BringToFront();
        active.Focus();
        startup.Visible = false;
        Text = kind == "home" ? "Noble Studio" : "Noble Studio — " + (kind == "image" ? "Image Studio" : "Video");
        string entry = System.Web.HttpUtility.ParseQueryString(new Uri(origin + path).Query).Get("entry");
        if (existing && kind == "video" && (entry == "agent" || entry == "story" || entry == "demo" || entry == "new")) Send(view,"entry",entry);
        if (kind == "image" && entry == "new") Send(view,"photo-entry",entry);
        return view;
    }

    private async Task Route(WebView2 sender,string href)
    {
        Uri uri;
        if (sender != active || closed || href == null || href.Length > 256 || !IsLocal(href,out uri)) return;
        try
        {
            string path = uri.AbsolutePath;
            closingEditors = false;
            if (path == Home) await ShowWorkspace("home", Home);
            else if (sender == views["home"])
            {
                if (path == "/desktop/copy-export") CopyExportedFile();
                else if (path == "/desktop/native/video") await ShowVideoEditor();
                else if (path == "/image/" || path == "/desktop/native/image" || path == "/video/")
                {
                    string kind = path == "/video/" ? "video" : "image";
                    if (System.Web.HttpUtility.ParseQueryString(uri.Query).Get("entry") == "open") await Pick(kind);
                    else await ShowWorkspace(kind,PathFor(kind) + (path == "/desktop/native/image" ? "?empty&webgl&" + uri.Query.TrimStart('?') : uri.Query));
                }
            }
        }
        catch (Exception error) { if (!closingEditors) ShowError(error.Message); }
    }

    private async Task FromRenderer(WebView2 view,string kind,string text)
    {
        Dictionary<string,object> message;
        try { message = json.Deserialize<Dictionary<string,object>>(text); }
        catch (Exception) { return; }
        if (message == null) return;
        object id = Field(message,"id");
        object data = Field(message,"data");
        string action = Field(message,"action") as string;
        try
        {
            switch (action)
            {
                case "list-recent":
                    if (kind=="home") Send(view,"reply",RecentFiles().ConvertAll(x=>new {id=x.Id,name=Path.GetFileName(x.Path),thumbnail=x.Thumbnail,opened=x.OpenedUtc}),id);
                    return;
                case "open-recent":
                    if (kind!="home" || view!=active || nativeEditor!=null) throw new InvalidOperationException("Return to Projects first.");
                    var item = RecentFiles().Find(x=>x.Id==(data as string));
                    if (item==null || !File.Exists(item.Path)) throw new FileNotFoundException("This file moved or was deleted. Choose Open to locate it.");
                    bool videoProject = Path.GetExtension(item.Path).Equals(".fcproj",StringComparison.OrdinalIgnoreCase);
                    await OpenSelected(videoProject ? "video" : "image",new[]{item.Path});
                    Send(view,"reply",true,id);
                    return;
                case "navigate": await Route(view,data as string); return;
                case "pick-photos":
                    if (kind != "image" || view != active) throw new InvalidOperationException("Open Image Studio first.");
                    await Pick("image");
                    Send(view,"reply",true,id);
                    return;
                case "photo-import-result":
                    if (kind == "image") FinishPhotoTransfer(view,data as Dictionary<string,object>);
                    return;
                case "photo-close-state":
                    if (kind == "image" && photoCloseCheck != null) photoCloseCheck.TrySetResult(Equals(data,true));
                    return;
                case "mcp-result": CompleteMcp(view,id as string,data as Dictionary<string,object>); return;
                case "prepare-upload": Send(view,"reply",PrepareUpload(view,kind,data),id); return;
                case "save-dialog": Send(view,"reply",ChooseSaveDestination(view,kind,data),id); return;
                case "photo-preferences":
                    if (kind != "image") throw new InvalidOperationException("Open Image Studio to change photo preferences.");
                    Send(view,"reply",PhotoPreferences(data),id);
                    return;
                case "show-error":
                    if (view == active && kind == "video") ShowError(data as string);
                    return;
            }
        }
        catch (Exception error)
        {
            if (!closed && id != null) Send(view,"reply",null,id,error.Message);
        }
    }

    private static object Field(Dictionary<string,object> data,string name)
    {
        object result;
        return data != null && data.TryGetValue(name,out result) ? result : null;
    }

    private void Send(WebView2 view,string type,object data,object id = null,string error = null)
    {
        if (!closed && !view.IsDisposed) view.CoreWebView2.PostWebMessageAsJson(json.Serialize(new { @event=type,data,id,error }));
    }

    private void DispatchMcp(string line)
    {
        var request = json.Deserialize<Dictionary<string,object>>(line);
        string type = Field(request,"type") as string;
        if (type != "video-edit-request" && type != "photo-review-request") return;
        bool video = type == "video-edit-request";
        object id = Field(request,"id");
        string key = (video ? "video:" : "photo:") + id;
        WebView2 view;
        if (views.TryGetValue(video ? "video" : "image",out view) &&
            (view == active || video && (Field(request,"tool") as string) == "video_export_status"))
        {
            mcpRequests.Clear();
            mcpRequests[key] = view;
            Send(view,"mcp",request,key);
        }
        else WriteService(new { type=video ? "video-edit-result" : "photo-review-result",id,
            error=video ? "Open Noble’s AI video workspace first." : "Open a photo in Noble’s AI photo workspace first." });
    }

    private void CompleteMcp(WebView2 view,string key,Dictionary<string,object> result)
    {
        WebView2 pending;
        if (key == null || !mcpRequests.TryGetValue(key,out pending) || view != pending) return;
        mcpRequests.Remove(key);
        bool video = key.StartsWith("video:",StringComparison.Ordinal);
        int id;
        if (!int.TryParse(key.Substring(6),out id)) return;
        string error = Field(result,"error") as string;
        if (!video && active != view) error = "Return to Noble Image Studio to review the photo.";
        WriteService(new {type=video ? "video-edit-result" : "photo-review-result",id,
            result=Field(result,"result"),error});
    }

    private void WriteService(object response)
    {
        if (service == null || service.HasExited) return;
        try { service.StandardInput.WriteLine(json.Serialize(response)); }
        catch (IOException) { }
    }

    private void ShowError(string error)
    {
        if (!closed) MessageBox.Show(this,error,"Noble Studio",MessageBoxButtons.OK,MessageBoxIcon.Warning);
    }

    protected override void OnFormClosing(FormClosingEventArgs e)
    {
        WebView2 imageView;
        if (editors.Count > 0 && !closed)
        {
            e.Cancel = true;
            closingEditors = true;
            CloseEditors();
        }
        else if (!closed && !photoCloseConfirmed && views.TryGetValue("image",out imageView))
        {
            e.Cancel = true;
            if (photoCloseCheck == null) CheckPhotoBeforeClose(imageView);
        }
        else
        {
            closed = true;
            if (service != null)
            {
                if (!service.HasExited)
                {
                    service.StandardInput.Close();
                    if (!service.WaitForExit(1500)) service.Kill();
                }
                service.Dispose();
            }
        }
        base.OnFormClosing(e);
    }

    private async void CheckPhotoBeforeClose(WebView2 view)
    {
        var check = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
        photoCloseCheck = check;
        bool dirty = true;
        try
        {
            Send(view,"check-photo-close",null);
            if (await Task.WhenAny(check.Task,Task.Delay(2500)) == check.Task) dirty = await check.Task;
            if (dirty && MessageBox.Show(this,"Close Noble and discard unsaved photo edits?","Noble Image Studio",
                MessageBoxButtons.OKCancel,MessageBoxIcon.Question,MessageBoxDefaultButton.Button2) != DialogResult.OK) return;
            photoCloseConfirmed = true;
            Close();
        }
        finally { photoCloseCheck = null; }
    }

    private sealed class HostedEditor
    {
        public Process Process;
        public TaskCompletionSource<bool> Ready = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
    }

    private string NativePath(string kind,string field)
    {
        var releases = json.Deserialize<Dictionary<string,Dictionary<string,string>>>(File.ReadAllText(Path.Combine(Resources,"native-studios.json")));
        return Path.Combine(Package,"resources","native",kind,releases[kind]["directory"],releases[kind][field]);
    }

    private async Task ShowVideoEditor()
    {
        const string kind = "video";
        string binary = NativePath(kind,"executable");
        string host = Path.Combine(Resources,"NobleEditorHost.exe");
        if (!File.Exists(binary) || !File.Exists(host)) throw new InvalidOperationException("The full desktop editing tools are missing. Reinstall Noble Studio.");
        workspaceVersion++;
        HideEditor();
        nativeEditor = kind;
        Send(views["home"],"desktop-editor",kind);
        Text = "Noble Studio — Full video tools";
        HostedEditor editor;
        if (!editors.TryGetValue(kind,out editor))
        {
            editor = new HostedEditor();
            string data = Path.Combine(settingsProfile,"desktop-editor-" + kind);
            string args = Handle.ToInt64().ToString("x") + " " + DockHeaderHeight + " " + Quote(binary);
            args += " --empty --data-dir " + Quote(data);
            editor.Process = Process.Start(HiddenProcess(host,args));
            editors.Add(kind,editor);
            string error = null;
            var launched = editor;
            editor.Process.ErrorDataReceived += (_,e) => { if (!string.IsNullOrWhiteSpace(e.Data)) error = e.Data; };
            editor.Process.OutputDataReceived += (_,e) =>
            {
                if (e.Data == "ready") launched.Ready.TrySetResult(true);
            };
            editor.Process.EnableRaisingEvents = true;
            editor.Process.Exited += delegate
            {
                launched.Ready.TrySetException(new InvalidOperationException(error ?? "The full desktop editor stopped."));
                OnUi(() =>
                {
                    editors.Remove(kind);
                    launched.Process.Dispose();
                    if (kind != nativeEditor) return;
                    nativeEditor = null;
                    if (closingEditors) CloseEditors();
                    else
                    {
                        Send(views["home"],"desktop-editor",null);
                        Text = "Noble Studio";
                    }
                });
            };
            editor.Process.BeginOutputReadLine();
            editor.Process.BeginErrorReadLine();
        }
        await editor.Ready.Task;
        if (!closed && nativeEditor == kind) EditorCommand(editor,"show");
    }

    private sealed class RecentFile
    {
        public string Id { get; set; }
        public string Path { get; set; }
        public string Thumbnail { get; set; }
        public string OpenedUtc { get; set; }
    }
    private List<RecentFile> recentFiles;
    private List<RecentFile> RecentFiles()
    {
        if (recentFiles != null) return recentFiles;
        recentFiles = new List<RecentFile>();
        try {
            var saved = json.Deserialize<List<RecentFile>>(File.ReadAllText(Path.Combine(profile,"recent-files.json")));
            if (saved != null) foreach (var item in saved)
                if (item != null && !string.IsNullOrEmpty(item.Path) && !string.IsNullOrEmpty(item.Id) && recentFiles.Count < 16)
                    recentFiles.Add(item);
        } catch (IOException) {} catch (UnauthorizedAccessException) {} catch (ArgumentException) {} catch (InvalidOperationException) {}
        return recentFiles;
    }
    private void RememberFile(string file)
    {
        var files = RecentFiles();
        var item = files.Find(x => string.Equals(x.Path,file,StringComparison.OrdinalIgnoreCase));
        if (item != null) files.Remove(item);
        else item = new RecentFile {Id=Guid.NewGuid().ToString("N"),Path=file};
        try {
            string ext = Path.GetExtension(file).ToLowerInvariant();
            if ((ext==".jpg" || ext==".jpeg" || ext==".png") && new FileInfo(file).Length<=8*1024*1024)
                using (var source = Image.FromFile(file)) {
                    if ((long)source.Width*source.Height<=24000000) {
                        double scale = Math.Min(160.0/source.Width,100.0/source.Height);
                        using (var thumb = new Bitmap(source,Math.Max(1,(int)(source.Width*scale)),Math.Max(1,(int)(source.Height*scale))))
                        using (var stream = new MemoryStream()) {
                            thumb.Save(stream,System.Drawing.Imaging.ImageFormat.Jpeg);
                            item.Thumbnail = "data:image/jpeg;base64,"+Convert.ToBase64String(stream.ToArray());
                        }
                    }
                }
        } catch (ArgumentException) {} catch (IOException) {} catch (OutOfMemoryException) {} catch (ExternalException) {} catch (UnauthorizedAccessException) {}
        item.OpenedUtc = DateTime.UtcNow.ToString("o");
        files.Insert(0,item);
        if (files.Count>16) files.RemoveAt(16);
        try {
            string dest=Path.Combine(profile,"recent-files.json"), temp=dest+".tmp";
            File.WriteAllText(temp,json.Serialize(files));
            if (File.Exists(dest)) File.Replace(temp,dest,null); else File.Move(temp,dest);
        } catch (IOException) {} catch (UnauthorizedAccessException) {}
        Send(views["home"],"recent-updated",null);
    }
    private void HideEditor()
    {
        if (nativeEditor == null) return;
        HostedEditor editor;
        if (editors.TryGetValue(nativeEditor,out editor)) EditorCommand(editor,"hide");
        nativeEditor = null;
        Send(views["home"],"desktop-editor",null);
    }

    private static void EditorCommand(HostedEditor editor,string command)
    {
        try { if (!editor.Process.HasExited) editor.Process.StandardInput.WriteLine(command); }
        catch (IOException) { }
        catch (InvalidOperationException) { }
    }

    private async void CloseEditors()
    {
        if (editors.Count == 0) { Close(); return; }
        string kind = nativeEditor;
        if (kind == null) foreach (string editor in editors.Keys) { kind = editor; break; }
        try
        {
            if (active != views["home"]) await ShowWorkspace("home", Home);
            await ShowVideoEditor();
            if (closingEditors && nativeEditor == kind) EditorCommand(editors[kind],"close");
        }
        catch (Exception error) { if (!closed && !closingEditors) ShowError(error.Message); }
    }

    protected override bool ProcessCmdKey(ref Message msg,Keys key)
    {
        if (key == (Keys.Control | Keys.Shift | Keys.H) && views.ContainsKey("home"))
        {
            closingEditors = false;
            OnUi(async () => await ShowWorkspace("home",Home));
            return true;
        }
        return base.ProcessCmdKey(ref msg,key);
    }
}
