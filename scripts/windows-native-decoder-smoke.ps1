$ErrorActionPreference = "Stop"
$work = "C:\CinemarrNativeSmoke"
$media = $null
$evidenceRoot = $null

function Publish-File([string]$Path, [string]$Name) {
    Copy-Item -LiteralPath $Path -Destination (Join-Path $evidenceRoot $Name) -Force
}

function Publish-Text([string]$Text, [string]$Name) {
    $path = Join-Path $work $Name
    [IO.File]::WriteAllText($path, $Text, [Text.UTF8Encoding]::new($false))
    Publish-File $path $Name
}

function Resolve-VolumeRoot([string]$Label) {
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        Get-Disk | Where-Object IsOffline | Set-Disk -IsOffline $false -ErrorAction SilentlyContinue | Out-Null
        $volume = Get-Volume -FileSystemLabel $Label -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($null -ne $volume) {
            if ([string]::IsNullOrWhiteSpace($volume.DriveLetter)) {
                $partition = Get-Partition | Where-Object {
                    $candidate = $_ | Get-Volume -ErrorAction SilentlyContinue
                    $null -ne $candidate -and $candidate.FileSystemLabel -eq $Label
                } | Select-Object -First 1
                if ($null -ne $partition) {
                    $used = @(Get-Volume | Where-Object DriveLetter | ForEach-Object { $_.DriveLetter })
                    $letter = @('E','F','G','H','I','J','K','L','M','N','O','P','Q','R','S','T','U','V','W','X','Y','Z') |
                        Where-Object { $_ -notin $used } | Select-Object -First 1
                    if ($null -ne $letter) {
                        $partition | Set-Partition -NewDriveLetter $letter | Out-Null
                        $volume = Get-Volume -FileSystemLabel $Label | Select-Object -First 1
                    }
                }
            }
            if (-not [string]::IsNullOrWhiteSpace($volume.DriveLetter)) {
                return "$($volume.DriveLetter):\"
            }
        }
        Start-Sleep -Seconds 2
    }
    throw "$Label volume was not ready after 120 seconds"
}

try {
    $media = Resolve-VolumeRoot "CINEMARR"
    $evidenceRoot = Resolve-VolumeRoot "CINEVIDENCE"
    New-Item -ItemType Directory -Path $work -Force | Out-Null
    Copy-Item (Join-Path $media "bundle") (Join-Path $work "bundle") -Recurse -Force
    Expand-Archive -Path (Join-Path $media "jre.zip") -DestinationPath (Join-Path $work "java") -Force
    $java = Get-ChildItem (Join-Path $work "java") -Filter java.exe -Recurse |
        Where-Object { $_.FullName -like "*\bin\java.exe" } | Select-Object -First 1
    if ($null -eq $java) { throw "Windows x64 Java runtime was not found" }

    $bundle = Join-Path $work "bundle"
    $classpath = @(
        (Join-Path $bundle "classes"),
        (Join-Path $bundle "lib\core-1.0.0.jar"),
        (Join-Path $bundle "lib\javacpp-1.5.14.jar"),
        (Join-Path $bundle "lib\javacpp-1.5.14-windows-x86_64.jar"),
        (Join-Path $bundle "lib\ffmpeg-8.1.2-1.5.14.jar"),
        (Join-Path $bundle "lib\ffmpeg-8.1.2-1.5.14-windows-x86_64.jar")
    ) -join ';'
    $output = Join-Path $work "evidence"
    New-Item -ItemType Directory -Path $output -Force | Out-Null
    $console = Join-Path $output "console.txt"
    $arguments = @(
        '-Xmx3g', '-cp', $classpath, 'stonytark.cinemarr.client.VideoDecoderBenchmark',
        '--backend', 'software', '--expected-effective', 'software', '--output', $output,
        '--warmups', '0', '--runs', '1', '--seconds', '0.6', '--require-hardware', 'false',
        '--expect-fallback', 'false', '--fail-on-acceptance', 'true', '--gpu', 'none',
        '--driver', 'none', '--classifier', 'windows-x86_64',
        '--minecraft-profile', 'standalone-native-smoke',
        '--fixture-dir', (Join-Path $bundle 'fixtures'), '--resolutions', '144p,480p,1080p'
    )
    $stdout = Join-Path $output "benchmark.stdout.txt"
    $stderr = Join-Path $output "benchmark.stderr.txt"
    $process = Start-Process -FilePath $java.FullName -ArgumentList $arguments -Wait -PassThru -NoNewWindow `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    Get-Content $stdout, $stderr -ErrorAction SilentlyContinue |
        Set-Content -LiteralPath $console -Encoding UTF8
    if ($process.ExitCode -ne 0) { throw "Decoder benchmark exited $($process.ExitCode)" }

    $jsonPath = Join-Path $output "decoder-benchmark.json"
    $csvPath = Join-Path $output "decoder-benchmark.csv"
    $result = Get-Content $jsonPath -Raw | ConvertFrom-Json
    if ($result.schema -ne 3 -or $result.os -notmatch '^Windows' -or
        $result.arch -notmatch '^(amd64|x86_64)$' -or
        $result.ffmpegClassifier -ne 'windows-x86_64' -or
        $result.requestedBackend -ne 'software' -or
        $result.expectedEffectiveBackend -ne 'software' -or
        @($result.rows).Count -ne 3 -or
        @($result.rows | Where-Object { -not $_.accepted -or $_.effectiveBackend -ne 'software' }).Count -ne 0) {
        throw "Windows benchmark metadata or acceptance rows were invalid"
    }

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $nativeJar = Join-Path $bundle "lib\ffmpeg-8.1.2-1.5.14-windows-x86_64.jar"
    $nativeDll = Join-Path $work "avcodec-62.dll"
    $zip = [IO.Compression.ZipFile]::OpenRead($nativeJar)
    try {
        $entry = $zip.GetEntry("org/bytedeco/ffmpeg/windows-x86_64/avcodec-62.dll")
        if ($null -eq $entry) { throw "Windows avcodec DLL is absent from its classifier" }
        $source = $entry.Open(); $target = [IO.File]::Create($nativeDll)
        try { $source.CopyTo($target) } finally { $target.Dispose(); $source.Dispose() }
    } finally { $zip.Dispose() }
    $stream = [IO.File]::OpenRead($nativeDll); $reader = [IO.BinaryReader]::new($stream)
    try {
        $stream.Position = 0x3c; $peOffset = $reader.ReadInt32()
        $stream.Position = $peOffset + 4; $machine = $reader.ReadUInt16()
    } finally { $reader.Dispose(); $stream.Dispose() }
    if ($machine -ne 0x8664) { throw "Classifier DLL is not PE32+ x86-64" }

    $versionStdout = Join-Path $work "java-version.stdout.txt"
    $versionStderr = Join-Path $work "java-version.stderr.txt"
    $versionProcess = Start-Process -FilePath $java.FullName -ArgumentList '-version' -Wait -PassThru -NoNewWindow `
        -RedirectStandardOutput $versionStdout -RedirectStandardError $versionStderr
    if ($versionProcess.ExitCode -ne 0) { throw "Java version probe exited $($versionProcess.ExitCode)" }
    $javaVersion = (Get-Content $versionStdout, $versionStderr -ErrorAction SilentlyContinue | Out-String).Trim()
    $os = Get-CimInstance Win32_OperatingSystem
    $system = @(
        "processorArchitecture=$env:PROCESSOR_ARCHITECTURE",
        "os=$($os.Caption)",
        "version=$($os.Version)",
        "build=$($os.BuildNumber)",
        "peMachine=0x$('{0:X4}' -f $machine)",
        "java=$javaVersion"
    ) -join "`r`n"
    [IO.File]::WriteAllText((Join-Path $output "system.txt"), $system + "`r`n", [Text.UTF8Encoding]::new($false))

    Publish-File $jsonPath "decoder-benchmark.json"
    Publish-File $csvPath "decoder-benchmark.csv"
    Publish-File $console "console.txt"
    Publish-File (Join-Path $output "system.txt") "system.txt"
    Publish-Text "Windows x86-64 native decoder smoke passed.`r`n" "passed.txt"
} catch {
    New-Item -ItemType Directory -Path $work -Force | Out-Null
    $failure = $_.Exception.ToString()
    if ($null -ne $evidenceRoot) {
        try { Publish-Text ($failure + "`r`n") "failed.txt" } catch { }
    }
} finally {
    shutdown.exe /s /t 10 /f
}
