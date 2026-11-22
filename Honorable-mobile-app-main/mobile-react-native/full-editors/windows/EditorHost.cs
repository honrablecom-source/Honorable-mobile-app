using System;
using System.Collections.Concurrent;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;

internal static class EditorHost
{
    private const int Style = -16;
    private const int ExtendedStyle = -20;
    private const uint ChildStyle = 0x40000000;
    private const uint SystemFrame = 0x80CF0000;
    private const uint AppWindow = 0x00040000;
    private const uint NoActivate = 0x0010;
    private const uint FrameChanged = 0x0020;
    private const uint CloseMessage = 0x0010;

    [StructLayout(LayoutKind.Sequential)]
    private struct Rectangle { public int Left, Top, Right, Bottom; }

    private delegate bool WindowCallback(IntPtr window, IntPtr state);
    [DllImport("user32.dll")] private static extern bool EnumWindows(WindowCallback callback, IntPtr state);
    [DllImport("user32.dll")] private static extern bool IsWindow(IntPtr window);
    [DllImport("user32.dll")] private static extern bool GetWindowRect(IntPtr window, out Rectangle rectangle);
    [DllImport("user32.dll")] private static extern bool GetClientRect(IntPtr window, out Rectangle rectangle);
    [DllImport("user32.dll")] private static extern uint GetDpiForWindow(IntPtr window);
    [DllImport("user32.dll")] private static extern uint GetWindowThreadProcessId(IntPtr window, out int processId);
    [DllImport("user32.dll")] private static extern IntPtr GetParent(IntPtr window);
    [DllImport("user32.dll", SetLastError=true)] private static extern IntPtr SetParent(IntPtr child, IntPtr parent);
    [DllImport("user32.dll")] private static extern IntPtr GetWindowLongPtr(IntPtr window, int index);
    [DllImport("user32.dll")] private static extern IntPtr SetWindowLongPtr(IntPtr window, int index, IntPtr value);
    [DllImport("user32.dll")] private static extern bool ShowWindowAsync(IntPtr window, int how);
    [DllImport("user32.dll")] private static extern bool SetWindowPos(IntPtr window, IntPtr after, int x, int y, int width, int height, uint flags);
    [DllImport("user32.dll")] private static extern bool PostMessage(IntPtr window, uint message, IntPtr parameter, IntPtr data);

    private static int Main(string[] args)
    {
        if (args.Length < 3) return 2;
        Process editor = null;
        IntPtr child = IntPtr.Zero;
        bool attached = false;
        try
        {
            var parent = new IntPtr(long.Parse(args[0], NumberStyles.HexNumber, CultureInfo.InvariantCulture));
            int toolbar = int.Parse(args[1], CultureInfo.InvariantCulture);
            if (!IsWindow(parent) || toolbar < 0 || toolbar > 160) return 2;

            var startup = new ProcessStartInfo(args[2])
            {
                WorkingDirectory = Path.GetDirectoryName(args[2]),
                UseShellExecute = false,
                CreateNoWindow = true,
                WindowStyle = ProcessWindowStyle.Hidden
            };
            var arguments = new StringBuilder();
            for (int i = 3; i < args.Length; i++)
            {
                if (args[i].Contains("\"") || args[i].EndsWith("\\")) throw new InvalidOperationException("Invalid editor argument.");
                arguments.Append(i == 3 ? "" : " ").Append('"').Append(args[i]).Append('"');
            }
            startup.Arguments = arguments.ToString();
            editor = Process.Start(startup);
            if (editor == null) throw new InvalidOperationException("The editor could not start.");

            var deadline = Stopwatch.StartNew();
            while (!editor.HasExited && IsWindow(parent) && deadline.ElapsedMilliseconds < 18000)
            {
                child = FindEditorWindow(editor.Id);
                if (child != IntPtr.Zero) break;
                Thread.Sleep(25);
            }
            if (child == IntPtr.Zero) throw new InvalidOperationException("The editor did not create a window.");
            ShowWindowAsync(child, 0);
            var style = unchecked((uint)GetWindowLongPtr(child, Style).ToInt64());
            var expanded = unchecked((uint)GetWindowLongPtr(child, ExtendedStyle).ToInt64());
            SetWindowLongPtr(child, Style, new IntPtr((style & ~SystemFrame) | ChildStyle));
            SetWindowLongPtr(child, ExtendedStyle, new IntPtr(expanded & ~AppWindow));
            SetParent(child, parent);
            if (GetParent(child) != parent) throw new InvalidOperationException("Windows refused to dock the editor.");
            attached = true;
            var previous = new Rectangle();
            Align(child, parent, toolbar, ref previous, true);
            var commands = new ConcurrentQueue<string>();
            using (var commandReady = new AutoResetEvent(false))
            {
                new Thread(() => ReadCommands(commands, commandReady)) { IsBackground = true }.Start();
                Console.WriteLine("ready");
                RunEditor(editor, child, parent, toolbar, commands, commandReady, previous);
            }
            if (!editor.HasExited)
            {
                PostMessage(child, CloseMessage, IntPtr.Zero, IntPtr.Zero);
                if (!editor.WaitForExit(4000) && !IsWindow(parent)) editor.Kill();
            }
            return 0;
        }
        catch (Exception error)
        {
            Console.Error.WriteLine(error.Message);
            return 1;
        }
        finally
        {
            if (editor != null)
            {
                if (!attached && !editor.HasExited) editor.Kill();
                editor.Dispose();
            }
        }
    }

    private static void ReadCommands(ConcurrentQueue<string> commands, AutoResetEvent commandReady)
    {
        try
        {
            string command;
            do
            {
                command = Console.ReadLine();
                commands.Enqueue(command ?? "disconnect");
                commandReady.Set();
            } while (command != null);
        }
        catch (ObjectDisposedException) { }
    }

    private static void RunEditor(Process editor, IntPtr child, IntPtr parent, int toolbar,
        ConcurrentQueue<string> commands, AutoResetEvent commandReady, Rectangle previous)
    {
        bool visible = false;
        while (!editor.HasExited && IsWindow(parent) && IsWindow(child))
        {
            string command;
            while (commands.TryDequeue(out command))
            {
                switch (command)
                {
                    case "disconnect": return;
                    case "close":
                        PostMessage(child, CloseMessage, IntPtr.Zero, IntPtr.Zero);
                        break;
                    case "show":
                        visible = true;
                        Align(child, parent, toolbar, ref previous, true);
                        ShowWindowAsync(child, 5);
                        break;
                    case "hide":
                        visible = false;
                        ShowWindowAsync(child, 0);
                        break;
                }
            }
            if (visible) Align(child, parent, toolbar, ref previous, false);
            commandReady.WaitOne(100);
        }
    }

    private static IntPtr FindEditorWindow(int processId)
    {
        IntPtr found = IntPtr.Zero;
        EnumWindows((window, unused) =>
        {
            int owner;
            GetWindowThreadProcessId(window, out owner);
            Rectangle bounds;
            if (owner == processId && GetWindowRect(window, out bounds) &&
                bounds.Right - bounds.Left >= 320 && bounds.Bottom - bounds.Top >= 240)
            {
                found = window;
                return false;
            }
            return true;
        }, IntPtr.Zero);
        return found;
    }

    private static void Align(IntPtr child, IntPtr parent, int toolbar, ref Rectangle previous, bool force)
    {
        Rectangle client;
        if (!IsWindow(child) || !GetClientRect(parent, out client)) return;
        var dpi = Math.Max(96u, GetDpiForWindow(parent));
        int y = (int)Math.Round(toolbar * dpi / 96.0);
        var bounds = new Rectangle { Top = y, Right = Math.Max(1, client.Right), Bottom = Math.Max(1, client.Bottom - y) };
        if (!force && bounds.Top == previous.Top && bounds.Right == previous.Right && bounds.Bottom == previous.Bottom) return;
        if (SetWindowPos(child, IntPtr.Zero, 0, bounds.Top, bounds.Right, bounds.Bottom, NoActivate | (force ? FrameChanged : 0)))
            previous = bounds;
    }
}
