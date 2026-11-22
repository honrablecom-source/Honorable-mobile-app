using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using System.Windows.Forms;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;

internal sealed partial class NobleStudio
{
    private const int Megabyte = 1024 * 1024;
    private const string TransferPrefix = "/desktop-transfer/";
    private readonly Dictionary<string, Transfer> transfers = new Dictionary<string, Transfer>();

    private sealed class Transfer
    {
        public WebView2 View;
        public string Name, Path, Kind, Destination;
        public DateTime Expires = DateTime.UtcNow.AddMinutes(2);
    }

    private string IssueTransfer(Transfer transfer)
    {
        var expired = new List<string>();
        foreach (var item in transfers)
            if (item.Value.Expires < DateTime.UtcNow) expired.Add(item.Key);
        foreach (string key in expired) transfers.Remove(key);
        if (transfers.Count >= 32) throw new InvalidOperationException("Finish the current files before opening another batch.");
        string token = TransferPrefix + Guid.NewGuid().ToString("N");
        transfers.Add(token,transfer);
        return token;
    }

    private async Task Pick(string kind)
    {
        if (pickerOpen) return;
        pickerOpen = true;
        bool photo = kind == "image";
        try
        {
            using (var dialog = new OpenFileDialog {
                Title = photo ? "Open photos in Noble" : "Open a Noble video project",
                Filter = photo ? "Photos and projects|*.pcraft;*.psd;*.psb;*.ora;*.png;*.jpg;*.jpeg;*.webp;*.tif;*.tiff;*.bmp;*.gif;*.heic;*.heif|All files|*.*" : "Noble video project|*.fcproj",
                Multiselect = photo, CheckFileExists = true, RestoreDirectory = true
            })
            {
                if (dialog.ShowDialog(this) != DialogResult.OK) return;
                if (dialog.FileNames.Length > 12) throw new InvalidOperationException("Choose up to 12 photos per import.");
                string[] files = dialog.FileNames;
                var view = await ShowWorkspace(kind,PathFor(kind) + "?empty&webgl");
                long total = 0;
                foreach (string file in files)
                {
                    string name = Path.GetFileName(file);
                    try
                    {
                        long size = new FileInfo(file).Length;
                        if (size < 1 || size > 64L * Megabyte || total + size > 128L * Megabyte)
                            throw new InvalidOperationException("Choose a nonempty file under 64 MB and a batch under 128 MB.");
                        if (!photo && !string.Equals(Path.GetExtension(file),".fcproj",StringComparison.OrdinalIgnoreCase))
                            throw new InvalidOperationException("Choose a Noble video project (.fcproj).");
                        string url = IssueTransfer(new Transfer { View=view, Path=file, Name=name });
                        total += size;
                        Send(view,photo ? "photo" : "project",new {name,url});
                    }
                    catch (Exception error)
                    {
                        if (!photo) throw;
                        Send(view,"photo",new {name,error=error.Message});
                    }
                }
            }
        }
        finally { pickerOpen = false; }
    }

    private string PrepareUpload(WebView2 view,string workspace,object data)
    {
        var options = data as Dictionary<string,object>;
        string kind = Field(options,"kind") as string;
        string name = Field(options,"name") as string;
        if (string.IsNullOrWhiteSpace(name) || name.Length > 255) throw new InvalidOperationException("Choose a valid filename.");
        if (workspace == "image" && active == view && kind == "decode" && Regex.IsMatch(name,@"\.hei[cf]$",RegexOptions.IgnoreCase))
            return IssueTransfer(new Transfer { View=view, Kind=kind, Name=name });
        if (workspace == "video" && (kind == "video" || kind == "project"))
            return IssueTransfer(new Transfer { View=view, Kind=kind, Name=name });
        throw new InvalidOperationException("This file action is unavailable in the current workspace.");
    }

    private string ChooseSaveDestination(WebView2 view,string kind,object data)
    {
        if ((kind != "image" && kind != "video") || view != active || pickerOpen)
            throw new InvalidOperationException("Open the workspace before saving.");
        string name = Field(data as Dictionary<string,object>,"name") as string;
        if (string.IsNullOrWhiteSpace(name) || name.Length > 255 || name.IndexOfAny(Path.GetInvalidFileNameChars()) >= 0)
            throw new InvalidOperationException("Choose a valid filename.");
        pickerOpen = true;
        try
        {
            using (var dialog = new SaveFileDialog {
                Title = kind == "image" ? "Save from Noble Image Studio" : "Save from Noble Video Studio",
                FileName = name, OverwritePrompt = true, RestoreDirectory = true,
                DefaultExt = Path.GetExtension(name).TrimStart('.'),
                InitialDirectory = Environment.GetFolderPath(kind == "image" ? Environment.SpecialFolder.MyPictures : Environment.SpecialFolder.MyVideos)
            })
            {
                if (dialog.ShowDialog(this) != DialogResult.OK) return null;
                return IssueTransfer(new Transfer { View=view, Kind=kind + "-delivery", Destination=dialog.FileName });
            }
        }
        finally { pickerOpen = false; }
    }

    private void HandleResource(WebView2 view,CoreWebView2WebResourceRequestedEventArgs e)
    {
        Uri uri;
        if (IsLocal(e.Request.Uri,out uri))
        {
            if (uri.AbsolutePath.StartsWith(TransferPrefix,StringComparison.Ordinal))
                HandleTransfer(view,uri.AbsolutePath,e);
            return;
        }
        if (Uri.TryCreate(e.Request.Uri,UriKind.Absolute,out uri) && (uri.Scheme == "blob" || uri.Scheme == "data")) return;
        e.Response = Response(403,"text/plain",null);
    }

    private async void HandleTransfer(WebView2 view,string key,CoreWebView2WebResourceRequestedEventArgs e)
    {
        Transfer transfer;
        if (!transfers.TryGetValue(key,out transfer) || transfer.View != view || transfer.Expires < DateTime.UtcNow ||
            e.Request.Method != (transfer.Path == null ? "POST" : "GET"))
        {
            e.Response = Response(404,"text/plain",null);
            return;
        }
        transfers.Remove(key);
        // Disposing the WebView2 deferral completes the request.
        using (e.GetDeferral())
        {
            try
            {
                if (transfer.Path != null)
                {
                    var file = File.OpenRead(transfer.Path);
                    if (file.Length < 1 || file.Length > 64L * Megabyte)
                    {
                        file.Dispose();
                        throw new InvalidDataException("Choose a nonempty file under 64 MB.");
                    }
                    e.Response = Response(200,"application/octet-stream",file);
                }
                else
                {
                    int limit = (transfer.Kind == "decode" || transfer.Kind == "image-delivery" ? 64 :
                        transfer.Kind == "project" ? 32 : 100) * Megabyte;
                    byte[] bytes;
                    using (var input = e.Request.Content) bytes = ReadAtMost(input,limit);
                    if (transfer.Kind == "decode")
                    {
                        byte[] png = await Task.Run(() => DecodePhonePhoto(transfer.Name,bytes));
                        e.Response = Response(200,"image/png",new MemoryStream(png,false));
                    }
                    else
                    {
                        string saved = await Task.Run(() => transfer.Destination == null ?
                            SaveEditedMedia(transfer.Name,transfer.Kind,bytes) : DeliverFile(transfer.Destination,bytes));
                        e.Response = Response(200,"application/json",Utf8Stream(json.Serialize(saved)));
                    }
                }
            }
            catch (Exception error) { e.Response = Response(400,"text/plain; charset=utf-8",Utf8Stream(error.Message)); }
        }
    }

    private CoreWebView2WebResourceResponse Response(int status,string type,Stream content)
    {
        return environment.CreateWebResourceResponse(content,status,status == 200 ? "OK" : "File unavailable",
            "Content-Type: " + type + "\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n");
    }

    private static MemoryStream Utf8Stream(string text) { return new MemoryStream(Encoding.UTF8.GetBytes(text),false); }

    private static string DeliverFile(string destination,byte[] bytes)
    {
        string pending = Path.Combine(Path.GetDirectoryName(destination),".noble-" + Guid.NewGuid().ToString("N") + ".part");
        try
        {
            File.WriteAllBytes(pending,bytes);
            if (File.Exists(destination)) File.Replace(pending,destination,null);
            else File.Move(pending,destination);
            return Path.GetFileName(destination);
        }
        finally { if (File.Exists(pending)) File.Delete(pending); }
    }

    private void CopyExportedFile()
    {
        if (pickerOpen) return;
        pickerOpen = true;
        try
        {
            using (var picker = new OpenFileDialog {
                Title = "Choose an exported file to copy",
                Filter = "Exported media|*.mp4;*.webm;*.png;*.jpg;*.jpeg;*.webp;*.gif;*.wav",
                RestoreDirectory = true, CheckFileExists = true
            })
            {
                if (picker.ShowDialog(this) != DialogResult.OK) return;
                var files = new System.Collections.Specialized.StringCollection();
                files.Add(picker.FileName);
                Clipboard.SetFileDropList(files);
                MessageBox.Show(this,"Paste the file into an app or folder that accepts files.","File copied",
                    MessageBoxButtons.OK,MessageBoxIcon.Information);
            }
        }
        finally { pickerOpen = false; }
    }

    private static byte[] ReadAtMost(Stream input,int limit)
    {
        if (input == null) throw new InvalidDataException("The editor did not supply a file.");
        using (var output = new MemoryStream())
        {
            var chunk = new byte[32768];
            int length;
            while ((length = input.Read(chunk,0,chunk.Length)) != 0)
            {
                if (output.Length + length > limit) throw new InvalidDataException("This file exceeds " + limit / Megabyte + " MB.");
                output.Write(chunk,0,length);
            }
            if (output.Length < 16) throw new InvalidDataException("The editor returned an incomplete file.");
            return output.ToArray();
        }
    }

    private byte[] DecodePhonePhoto(string name,byte[] bytes)
    {
        string binary = NativePath("image","cli");
        if (!File.Exists(binary)) throw new InvalidOperationException("Reinstall Noble Studio to restore the HEIC decoder.");
        string scratch = Path.Combine(Path.GetTempPath(),"noble-photo-import-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(scratch);
        string original = Path.Combine(scratch,"source" + Path.GetExtension(name).ToLowerInvariant());
        string output = Path.Combine(scratch,"decoded.png");
        try
        {
            File.WriteAllBytes(original,bytes);
            using (var decoder = Process.Start(HiddenProcess(binary,"convert " + Quote(original) + " " + Quote(output) + " --no-gpu")))
            {
                decoder.OutputDataReceived += delegate { };
                decoder.ErrorDataReceived += delegate { };
                decoder.BeginOutputReadLine();
                decoder.BeginErrorReadLine();
                if (!decoder.WaitForExit(45000))
                {
                    decoder.Kill();
                    decoder.WaitForExit(1500);
                    throw new InvalidOperationException("This HEIC or HEIF photo took too long to decode.");
                }
                if (decoder.ExitCode != 0 || !File.Exists(output))
                    throw new InvalidOperationException("This HEIC or HEIF photo could not be decoded. Try Noble’s full photo workspace.");
            }
            using (var file = File.OpenRead(output)) return ReadAtMost(file,64 * Megabyte);
        }
        finally
        {
            if (File.Exists(original)) File.Delete(original);
            if (File.Exists(output)) File.Delete(output);
            Directory.Delete(scratch);
        }
    }

    private static string SaveEditedMedia(string name,string kind,byte[] bytes)
    {
        bool project = kind == "project";
        if (project)
        {
            var serializer = new System.Web.Script.Serialization.JavaScriptSerializer { MaxJsonLength = 32 * Megabyte };
            var data = serializer.Deserialize<Dictionary<string,object>>(Encoding.UTF8.GetString(bytes));
            if ((Field(data,"format") as string) != "filmcraft.project" ||
                Field(Field(data,"project") as Dictionary<string,object>,"items") == null)
                throw new InvalidDataException("The editor returned an invalid project.");
        }
        else if (Encoding.ASCII.GetString(bytes,4,4) != "ftyp") throw new InvalidDataException("The editor returned an invalid MP4.");
        string prefix = project ? "Project" : "Export";
        string root = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.MyVideos),project ? "Noble Projects" : "Noble Exports");
        string folder = Path.Combine(root,prefix + "-" + Guid.NewGuid().ToString("N").Substring(0,12));
        Directory.CreateDirectory(folder);
        string safe = Regex.Replace(name,@"[^\p{L}\p{N} _-]"," ").Trim();
        if (safe.Length > 80) safe = safe.Substring(0,80);
        if (safe.Length == 0) safe = project ? "Project" : "Video";
        string output = Path.Combine(folder,"Noble - " + safe + (project ? ".fcproj" : ".mp4"));
        string pending = Path.Combine(folder,"save.part");
        try
        {
            File.WriteAllBytes(pending,bytes);
            File.Move(pending,output);
            return output;
        }
        catch
        {
            if (File.Exists(pending)) File.Delete(pending);
            Directory.Delete(folder);
            throw;
        }
    }

    private object PhotoPreferences(object data)
    {
        string path = Path.Combine(settingsProfile,"photo-workspace.json");
        if (data == null)
        {
            try { return File.Exists(path) && new FileInfo(path).Length <= 2048 ? json.DeserializeObject(File.ReadAllText(path)) : null; }
            catch (ArgumentException) { return null; }
            catch (InvalidOperationException) { return null; }
        }
        var fields = data as Dictionary<string,object>;
        var dock = Field(fields,"dock") as string;
        var density = Field(fields,"density") as string;
        var theme = Field(fields,"theme") as string;
        var width = Field(fields,"width") as int?;
        var canvasOnly = Field(fields,"canvasOnly") as bool?;
        if ((dock != "left" && dock != "right") ||
            (density != "compact" && density != "comfortable" && density != "large") ||
            (theme != "warm" && theme != "neutral") || width == null || width < 240 || width > 440 || canvasOnly == null)
            throw new InvalidOperationException("Choose valid photo workspace settings.");
        string pending = path + ".tmp";
        File.WriteAllText(pending,json.Serialize(new {dock,width,density,theme,canvasOnly}));
        if (File.Exists(path)) File.Replace(pending,path,null);
        else File.Move(pending,path);
        return true;
    }

}
