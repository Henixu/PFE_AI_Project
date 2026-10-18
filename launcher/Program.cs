using System.Diagnostics;

var projectRoot = FindProjectRoot(AppContext.BaseDirectory) ?? FindProjectRoot(Directory.GetCurrentDirectory());
if (projectRoot is null)
{
    Console.Error.WriteLine("Could not find the project root containing frontend and backend.");
    Console.ReadLine();
    return;
}

Console.Title = "PFE Project Launcher";
Console.WriteLine("PFE Project Launcher");
Console.WriteLine($"Project: {projectRoot}");
Console.WriteLine();
Console.WriteLine("Starting frontend and backend in separate terminals...");

StartTerminal(
    "PFE Frontend",
    projectRoot,
    "Push-Location frontend; npm.cmd start");

StartTerminal(
    "PFE Backend",
    projectRoot,
    "Push-Location backend; if (-not (Get-Command java.exe -ErrorAction SilentlyContinue)) { Write-Host 'Java 21 is required. Install Java 21 and set JAVA_HOME, then run this launcher again.' -ForegroundColor Red } else { .\\mvnw.cmd spring-boot:run }");

Console.WriteLine();
Console.WriteLine("Frontend: http://localhost:4200");
Console.WriteLine("Backend:  http://localhost:8090");
Console.WriteLine();
Console.WriteLine("The child terminals stay open. Press Enter here to close this launcher.");
Console.ReadLine();

static void StartTerminal(string title, string workingDirectory, string command)
{
    var escapedDirectory = workingDirectory.Replace("'", "''");
    var script = $"$Host.UI.RawUI.WindowTitle='{title}'; Push-Location '{escapedDirectory}'; {command}; Write-Host ''; Write-Host 'Process stopped. This window remains open.' -ForegroundColor Yellow";
    var startInfo = new ProcessStartInfo
    {
        FileName = "powershell.exe",
        WorkingDirectory = workingDirectory,
        UseShellExecute = true
    };
    startInfo.ArgumentList.Add("-NoLogo");
    startInfo.ArgumentList.Add("-NoExit");
    startInfo.ArgumentList.Add("-ExecutionPolicy");
    startInfo.ArgumentList.Add("Bypass");
    startInfo.ArgumentList.Add("-Command");
    startInfo.ArgumentList.Add(script);

    var process = Process.Start(startInfo);

    if (process is null)
    {
        Console.WriteLine($"Could not start: {title}");
    }
}

static string? FindProjectRoot(string startDirectory)
{
    var directory = new DirectoryInfo(startDirectory);
    while (directory is not null)
    {
        if (Directory.Exists(Path.Combine(directory.FullName, "frontend")) &&
            Directory.Exists(Path.Combine(directory.FullName, "backend")))
        {
            return directory.FullName;
        }
        directory = directory.Parent;
    }
    return null;
}