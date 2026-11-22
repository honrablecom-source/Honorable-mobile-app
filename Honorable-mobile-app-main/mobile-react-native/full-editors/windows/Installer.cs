using System;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Threading;
using System.Windows.Forms;

static class Installer
{
    [STAThread]
    static int Main(string[] args)
    {
        bool unattendedLaunch = args.Length == 1 && args[0] == "--install-and-launch";
        bool quiet = unattendedLaunch || (args.Length == 1 && args[0] == "--install");
        bool smoke = args.Length == 1 && args[0] == "--verify-payload";
        try
        {
            if (smoke)
            {
                using (var archive = OpenPayload())
                {
                    if (archive.GetEntry("NobleStudio.exe") == null)
                        throw new InvalidDataException("The Windows executable is missing.");
                }
                return 0;
            }

            using (var setup = new Mutex(false, @"Local\Honorable.NobleStudio.Setup"))
            {
                try
                {
                    if (!setup.WaitOne(0))
                        return quiet ? 1 : 0;
                }
                catch (AbandonedMutexException)
                {
                    // This invocation now owns the lock.
                }
                try
                {
                    return RunSetup(quiet, unattendedLaunch);
                }
                finally
                {
                    setup.ReleaseMutex();
                }
            }
        }
        catch (Exception error)
        {
            if (!smoke && !quiet)
                MessageBox.Show(error.Message, "Noble Studio Setup", MessageBoxButtons.OK, MessageBoxIcon.Error);
            return 1;
        }
    }

    static int RunSetup(bool quiet, bool unattendedLaunch)
    {
        string local = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        string root = Path.Combine(local, "Noble Studio");
        string destination = Path.Combine(root, PackageIdentity.Folder);
        string executable = Path.Combine(destination, "NobleStudio.exe");
        if (!quiet && MessageBox.Show("Install Noble Studio for your Windows account?", "Noble Studio Setup",
            MessageBoxButtons.OKCancel, MessageBoxIcon.Question, MessageBoxDefaultButton.Button2) != DialogResult.OK)
            return 0;

        if (!quiet && StudioIsRunning(root, executable, anotherVersion: true))
        {
            if (MessageBox.Show("Close the open Noble Studio window, then select OK to continue.",
                "Noble Studio Setup", MessageBoxButtons.OKCancel, MessageBoxIcon.Information, MessageBoxDefaultButton.Button2) != DialogResult.OK)
                return 0;
            if (StudioIsRunning(root, executable, anotherVersion: true))
                throw new InvalidOperationException("Noble Studio is still open. Close it and run Setup again.");
        }

        Directory.CreateDirectory(root);
        if (!File.Exists(executable))
            Install(root, destination);
        CreateShortcut(executable, destination);
        if ((quiet && !unattendedLaunch) || StudioIsRunning(root, executable, anotherVersion: false))
            return 0;
        if (!unattendedLaunch && MessageBox.Show("Noble Studio is installed. Open it now?", "Noble Studio Setup",
            MessageBoxButtons.OKCancel, MessageBoxIcon.Information, MessageBoxDefaultButton.Button2) != DialogResult.OK)
            return 0;

        var startup = new ProcessStartInfo
        {
            FileName = executable,
            WorkingDirectory = destination,
            UseShellExecute = false
        };
        using (var launched = Process.Start(startup))
        {
            if (launched == null)
                throw new InvalidOperationException("Noble Studio was installed but could not be opened.");
        }
        return 0;
    }

    static ZipArchive OpenPayload()
    {
        var stream = Assembly.GetExecutingAssembly().GetManifestResourceStream("NoblePayload");
        if (stream == null)
            throw new InvalidDataException("The installer payload is missing.");
        return new ZipArchive(stream, ZipArchiveMode.Read);
    }

    static void Install(string root, string destination)
    {
        string stage = Path.Combine(root, "setup-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(stage);
        try
        {
            using (var archive = OpenPayload())
                archive.ExtractToDirectory(stage);
            if (!File.Exists(Path.Combine(stage, "NobleStudio.exe")))
                throw new InvalidDataException("The Windows executable is missing.");
            Directory.Move(stage, destination);
        }
        finally
        {
            if (Directory.Exists(stage))
                Directory.Delete(stage, true);
        }
    }

    static bool StudioIsRunning(string root, string executable, bool anotherVersion)
    {
        string prefix = root + Path.DirectorySeparatorChar;
        foreach (var process in Process.GetProcessesByName("NobleStudio"))
        {
            using (process)
            {
                string location;
                try { location = process.MainModule.FileName; }
                catch (System.ComponentModel.Win32Exception) { continue; }
                catch (InvalidOperationException) { continue; }
                if (location.StartsWith(prefix, StringComparison.OrdinalIgnoreCase) &&
                    location.Equals(executable, StringComparison.OrdinalIgnoreCase) != anotherVersion)
                    return true;
            }
        }
        return false;
    }
    static void CreateShortcut(string executable, string workingDirectory)
    {
        string programs = Environment.GetFolderPath(Environment.SpecialFolder.Programs);
        var shellType = Type.GetTypeFromProgID("WScript.Shell", true);
        dynamic shell = Activator.CreateInstance(shellType);
        dynamic shortcut = shell.CreateShortcut(Path.Combine(programs, "Noble Studio.lnk"));
        shortcut.TargetPath = executable;
        shortcut.WorkingDirectory = workingDirectory;
        shortcut.Description = "Noble Studio — video and image editing";
        shortcut.IconLocation = Path.Combine(workingDirectory, "Noble.ico") + ",0";
        shortcut.Save();
    }
}
