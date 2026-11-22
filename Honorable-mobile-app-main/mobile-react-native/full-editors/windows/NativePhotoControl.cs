using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using System.Web.Script.Serialization;

internal sealed class NativePhotoControl
{
    private readonly TaskCompletionSource<int> port = new TaskCompletionSource<int>(TaskCreationOptions.RunContinuationsAsynchronously);
    private readonly string root;
    private sealed class EditorRejectedFile : Exception
    {
        public EditorRejectedFile(string message) : base(message) { }
    }
    public string Token { get; private set; }
    public string ReadRoot { get; private set; }
    public string WriteRoot { get; private set; }

    public NativePhotoControl(string profile)
    {
        Token = RandomHex();
        root = Path.Combine(profile,"native-photo-control-" + Guid.NewGuid().ToString("N"));
        ReadRoot = Path.Combine(root,"selected");
        WriteRoot = Path.Combine(root,"output");
        Directory.CreateDirectory(ReadRoot);
        Directory.CreateDirectory(WriteRoot);
    }

    public void AcceptPort(string line)
    {
        const string prefix = "photo-control:";
        int value;
        if (line != null && line.StartsWith(prefix,StringComparison.Ordinal) &&
            int.TryParse(line.Substring(prefix.Length),out value) && value > 0 && value <= 65535)
            port.TrySetResult(value);
    }

    public void Stop()
    {
        port.TrySetCanceled();
        RemoveEmpty(ReadRoot);
        RemoveEmpty(WriteRoot);
        RemoveEmpty(root);
    }

    public Task OpenFiles(string[] files) { return Edit(files,false); }
    public Task NewProject() { return Edit(null,true); }

    private async Task Edit(string[] files,bool create)
    {
        if (await Task.WhenAny(port.Task,Task.Delay(6000)) != port.Task || port.Task.IsCanceled)
            throw new InvalidOperationException(create ? "Choose File > New in Image Studio." : "Choose File > Open in Image Studio.");
        int number = await port.Task;
        try { await Task.Run(() => SendEdit(number,files,create)); }
        catch (IOException)
        {
            throw new InvalidOperationException(create ? "Choose File > New in Image Studio." : "Choose File > Open in Image Studio.");
        }
        catch (SocketException)
        {
            throw new InvalidOperationException(create ? "Choose File > New in Image Studio." : "Choose File > Open in Image Studio.");
        }
        catch (AggregateException error)
        {
            if (!(error.GetBaseException() is SocketException)) throw;
            throw new InvalidOperationException(create ? "Choose File > New in Image Studio." : "Choose File > Open in Image Studio.");
        }
    }

    private void SendEdit(int number,string[] files,bool create)
    {
        var json = new JavaScriptSerializer { MaxJsonLength = 65536 };
        using (var client = new TcpClient())
        {
            if (!client.ConnectAsync(IPAddress.Loopback,number).Wait(2000)) throw new IOException("Connection timed out.");
            using (var stream = client.GetStream())
            using (var reader = new StreamReader(stream,Encoding.UTF8))
            using (var writer = new StreamWriter(stream,new UTF8Encoding(false)) { NewLine = "\n", AutoFlush = true })
            {
                stream.ReadTimeout = stream.WriteTimeout = 3500;
                string clientNonce = RandomHex();
                var challenge = Send(json,reader,writer,1,"auth.challenge",new {clientNonce});
                string serverNonce = ReadHex(challenge,"serverNonce");
                string expected = Proof("server",clientNonce,serverNonce);
                if (!EqualsProof(expected,ReadHex(challenge,"serverProof")))
                    throw new IOException("The editor could not authenticate.");
                Send(json,reader,writer,2,"auth",new {proof=Proof("client",clientNonce,serverNonce)});
                stream.ReadTimeout = 60000;
                if (create)
                {
                    Send(json,reader,writer,3,"engine.execute",new {command="file.new",@params=new {width=1920,height=1080,background="white"}});
                    return;
                }
                var failed = new List<string>();
                for (int i=0; i<files.Length; i++)
                {
                    string name = Path.GetFileName(files[i]);
                    string folder = Guid.NewGuid().ToString("N");
                    string path = Path.Combine(ReadRoot,folder);
                    string copy = Path.Combine(path,name);
                    try
                    {
                        Directory.CreateDirectory(path);
                        try { File.Copy(files[i],copy); }
                        catch (IOException) { throw new EditorRejectedFile("Windows could not copy the selected file."); }
                        Send(json,reader,writer,3+i,"app.open",new {path=folder + "/" + name});
                    }
                    catch (EditorRejectedFile error)
                    {
                        failed.Add(name + " — " + error.Message);
                    }
                    catch (UnauthorizedAccessException)
                    {
                        failed.Add(name + " — Windows could not read the file.");
                    }
                    finally
                    {
                        try { if (File.Exists(copy)) File.Delete(copy); }
                        catch (IOException) { }
                        catch (UnauthorizedAccessException) { }
                        RemoveEmpty(path);
                    }
                }
                if (failed.Count > 0)
                    throw new InvalidOperationException("Image Studio could not open these files.\n\n" + string.Join("\n",failed));
            }
        }
    }

    private static Dictionary<string,object> Send(JavaScriptSerializer json,StreamReader reader,StreamWriter writer,
        int id,string method,object parameters)
    {
        writer.WriteLine(json.Serialize(new {id,method,@params=parameters}));
        var response = new StringBuilder();
        int ch;
        while ((ch=reader.Read()) >= 0 && ch != '\n' && response.Length < json.MaxJsonLength) response.Append((char)ch);
        if (ch != '\n') throw new IOException("The editor returned an incomplete response.");
        var value = json.Deserialize<Dictionary<string,object>>(response.ToString());
        object received, success, result, error;
        if (value == null || !value.TryGetValue("id",out received) || !Equals(received,id) || !value.TryGetValue("ok",out success))
            throw new IOException("The editor did not accept the command.");
        if (!Equals(success,true))
        {
            string message = value.TryGetValue("error",out error) ? error as string : "The editor did not accept the command.";
            if (method == "app.open") throw new EditorRejectedFile(message);
            throw new IOException(message);
        }
        return value.TryGetValue("result",out result) ? result as Dictionary<string,object> : null;
    }

    private static string ReadHex(Dictionary<string,object> result,string key)
    {
        object value;
        if (result == null || !result.TryGetValue(key,out value) || !(value is string) ||
            !Regex.IsMatch((string)value,@"\A[0-9a-f]{64}\z"))
            throw new IOException("The editor authentication response was invalid.");
        return (string)value;
    }

    private string Proof(string side,string clientNonce,string serverNonce)
    {
        using (var hmac = new HMACSHA256(Encoding.ASCII.GetBytes(Token)))
            return Hex(hmac.ComputeHash(Encoding.ASCII.GetBytes(
                "photocraft-control-v1 " + side + "\n" + clientNonce + "\n" + serverNonce)));
    }

    private static bool EqualsProof(string a,string b)
    {
        int delta = 0;
        for (int i = 0; i < a.Length; i++) delta |= a[i] ^ b[i];
        return delta == 0;
    }

    private static string RandomHex()
    {
        var bytes = new byte[32];
        using (var random = RandomNumberGenerator.Create()) random.GetBytes(bytes);
        return Hex(bytes);
    }

    private static string Hex(byte[] bytes) { return BitConverter.ToString(bytes).Replace("-","").ToLowerInvariant(); }

    private static void RemoveEmpty(string path)
    {
        try { Directory.Delete(path); }
        catch (IOException) { }
        catch (UnauthorizedAccessException) { }
    }
}
