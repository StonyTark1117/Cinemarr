param(
    [string]$MediaRoot = "",
    [string]$Server = "192.168.1.107",
    [string]$Receiver = "http://192.168.1.15:18080",
    [switch]$QueueOnly
)

$ErrorActionPreference = "Stop"
$work = "C:\CinemarrClientGate"
$javaBase = "C:\cj"
$source = Join-Path $work "source"
$results = Join-Path $work "results"
$summary = Join-Path $results "summary.txt"

if ($QueueOnly) {
    if ([string]::IsNullOrWhiteSpace($MediaRoot)) { $MediaRoot = $PSScriptRoot }
    $scriptPath = Join-Path $MediaRoot "run-client.ps1"
    New-Item -ItemType Directory -Path $work -Force | Out-Null
    $queuedScript = Join-Path $work "run-client.ps1"
    Copy-Item $scriptPath $queuedScript -Force
    $command = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$queuedScript`""
    $runOncePath = "HKCU:\Software\Microsoft\Windows\CurrentVersion\RunOnce"
    New-Item -Path $runOncePath -Force | Out-Null
    New-ItemProperty -Path $runOncePath -Name "CinemarrClientGate" -Value $command -PropertyType String -Force | Out-Null
    shutdown.exe /s /t 15 /f
    exit 0
}

function Publish-File([string]$Path, [string]$Name) {
    if (-not (Test-Path $Path)) { return }
    & curl.exe --fail --retry 5 --retry-delay 3 -X PUT --data-binary "@$Path" "$Receiver/$Name"
    if ($LASTEXITCODE -ne 0) { throw "Unable to publish $Name" }
}

function Publish-Checkpoint([string]$Message) {
    Add-Content -Path $summary -Value "$Message $(Get-Date -Format o)"
    try { Publish-File $summary "windows-client-progress.txt" } catch { }
}

function Wait-Port([int]$Port) {
    $deadline = (Get-Date).AddMinutes(30)
    while ((Get-Date) -lt $deadline) {
        if (Test-NetConnection -ComputerName $Server -Port $Port -InformationLevel Quiet -WarningAction SilentlyContinue) {
            return
        }
        Start-Sleep -Seconds 3
    }
    throw "Acceptance server $Server`:$Port did not become ready"
}

function Quote-Cmd([string]$Value) {
    return '"' + $Value.Replace('"', '""') + '"'
}

function Get-CompleteJavaHome([string]$Root) {
    $executable = Get-ChildItem $Root -Filter java.exe -File -Recurse -ErrorAction SilentlyContinue |
        Where-Object { $_.Directory.Name -eq "bin" } | Select-Object -First 1
    if ($null -eq $executable) { return "" }
    $javaHomePath = $executable.Directory.Parent.FullName
    if (-not (Test-Path (Join-Path $javaHomePath "release"))) { return "" }
    if (-not (Test-Path (Join-Path $javaHomePath "lib\modules")) -and
        -not (Test-Path (Join-Path $javaHomePath "jre\lib\rt.jar"))) { return "" }
    return $javaHomePath
}

function Find-JavaHome([string]$Root, [string]$Label) {
    $javaHomePath = Get-CompleteJavaHome $Root
    if ([string]::IsNullOrWhiteSpace($javaHomePath)) { throw "$Label Java runtime was not found" }
    return $javaHomePath
}

function Run-Profile([string]$Name, [string]$RelativeDirectory, [int]$Port) {
    $project = Join-Path $source $RelativeDirectory
    $game = Join-Path $work ("game-" + $Name)
    $config = Join-Path $game "config"
    $stdout = Join-Path $results ("$Name-console.txt")
    $stderr = Join-Path $results ("$Name-stderr.txt")
    New-Item -ItemType Directory -Path $config -Force | Out-Null
    $configLines = @(
        "enabled = true",
        "volume = 1.0",
        'videoDecoderBackend = "auto"',
        'videoDecoderDevice = ""'
    )
    [IO.File]::WriteAllLines((Join-Path $config "cinemarr-client.toml"), $configLines,
        [Text.UTF8Encoding]::new($false))

    Publish-Checkpoint "$Name waiting-for-server"
    Wait-Port $Port
    Publish-Checkpoint "$Name server-ready"
    $arguments = @(
        "runClient", "--no-daemon", "--no-configuration-cache", "--max-workers=1", "--console=plain",
        "-Dorg.gradle.java.installations.paths=$script:java8Home,$script:java17Home,$script:java21Home,$script:java26Home",
        "-PcinemarrAcceptanceUsername=CinemarrVideoA",
        "-PcinemarrAcceptanceServer=$Server`:$Port",
        "-PcinemarrAcceptanceGameDir=$game"
    )
    $command = "call " + (Quote-Cmd (Join-Path $project "gradlew.bat")) + " " +
        (($arguments | ForEach-Object { Quote-Cmd $_ }) -join " ")
    $javaHome = $script:java26Home
    $oldJavaHome = $env:JAVA_HOME
    $oldJavaOptions = $env:JAVA_TOOL_OPTIONS
    $oldOpenAlDrivers = $env:ALSOFT_DRIVERS
    $oldPath = $env:PATH
    try {
        $env:JAVA_HOME = $javaHome
        $env:JAVA_TOOL_OPTIONS = "-Dcinemarr.acceptance.enabled=true -Dcinemarr.acceptance.videoProbe=true -Dcinemarr.acceptance.videoLeader=true -Dcinemarr.video.gpuVendor=nvidia"
        $env:ALSOFT_DRIVERS = "null"
        $env:PATH = "$javaHome\bin;$oldPath"
        $process = Start-Process -FilePath "cmd.exe" -ArgumentList "/d", "/c", $command -WorkingDirectory $project -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
    } finally {
        $env:JAVA_HOME = $oldJavaHome
        $env:JAVA_TOOL_OPTIONS = $oldJavaOptions
        $env:ALSOFT_DRIVERS = $oldOpenAlDrivers
        $env:PATH = $oldPath
    }
    $deadline = (Get-Date).AddMinutes(45)
    $ready = $false
    try {
        while ((Get-Date) -lt $deadline -and -not $process.HasExited) {
            $combined = ""
            if (Test-Path $stdout) { $combined += Get-Content $stdout -Raw }
            if (Test-Path $stderr) { $combined += Get-Content $stderr -Raw }
            if ($combined -match "Acceptance video ready:") { $ready = $true; break }
            if ($combined -match "Cinemarr rejected video segment|Acceptance audio state: ERROR|Failed to open OpenAL device") {
                throw "$Name reported a playback failure"
            }
            Start-Sleep -Seconds 2
            $process.Refresh()
        }
        if (-not $ready) { throw "$Name did not reach Acceptance video ready" }
        Start-Sleep -Seconds 8
    } finally {
        if (-not $process.HasExited) {
            & taskkill.exe /PID $process.Id /T /F | Out-Null
            $process.WaitForExit(30000) | Out-Null
        }
    }

    $combined = (Get-Content $stdout -Raw) + (Get-Content $stderr -Raw)
    # deviceType describes the sanitized selector supplied through
    # videoDecoderDevice, not the selected backend. This gate intentionally
    # leaves that selector empty, so a native D3D11VA selection must report the
    # default device.
    if ($combined -notmatch "Cinemarr (legacy )?video decoder requested=auto effective=d3d11va deviceType=default") {
        throw "$Name did not select the expected D3D11VA decoder"
    }
    if ($combined -notmatch "Acceptance decoder metrics: requested=auto effective=d3d11va .*fallbackCount=0 recoveries=0 videoDrops=0 audioUnderruns=0") {
        throw "$Name did not record a clean D3D11VA acceptance interval"
    }
    $screenshot = Join-Path $game "screenshots\cinemarr-video-acceptance.png"
    if (-not (Test-Path $screenshot) -or (Get-Item $screenshot).Length -eq 0) {
        throw "$Name did not save its rendered-TV screenshot"
    }
    Copy-Item $screenshot (Join-Path $results "$Name-screenshot.png") -Force
    Publish-File $stdout "$Name-client-console.txt"
    Publish-File $stderr "$Name-client-stderr.txt"
    Publish-File (Join-Path $results "$Name-screenshot.png") "$Name-client-screenshot.png"
    Publish-Checkpoint "$Name pass"
}

try {
    New-Item -ItemType Directory -Path $work, $source, $results -Force | Out-Null
    if ([string]::IsNullOrWhiteSpace($MediaRoot)) {
        $volume = Get-Volume -FileSystemLabel "CINEMARRCLIENT" | Select-Object -First 1
        if ($null -eq $volume) { throw "CINEMARRCLIENT media not found" }
        $MediaRoot = "$($volume.DriveLetter):\"
    }
    if (-not (Test-Path (Join-Path $source "gradlew.bat"))) {
        Publish-Checkpoint "source extract-start"
        & tar.exe -xzf (Join-Path $MediaRoot "source.tar.gz") -C $source
        if ($LASTEXITCODE -ne 0) { throw "Unable to extract source archive" }
        Publish-Checkpoint "source extract-complete"
    }
    $javaArchives = @(
        @{ Name = "java8"; Archive = "jdk8.zip" },
        @{ Name = "java17"; Archive = "jdk17.zip" },
        @{ Name = "java21"; Archive = "jdk21.zip" },
        @{ Name = "java26"; Archive = "jre26.zip" }
    )
    $javaHomes = @{}
    foreach ($javaArchive in $javaArchives) {
        $mediaJavaHome = Get-CompleteJavaHome (Join-Path $MediaRoot $javaArchive.Name)
        if (-not [string]::IsNullOrWhiteSpace($mediaJavaHome)) {
            $javaHomes[$javaArchive.Name] = $mediaJavaHome
            Publish-Checkpoint "$($javaArchive.Name) using-read-only-media"
            continue
        }
        $javaRoot = Join-Path $javaBase $javaArchive.Name
        $javaHomeCandidate = Get-CompleteJavaHome $javaRoot
        if ([string]::IsNullOrWhiteSpace($javaHomeCandidate)) {
            Publish-Checkpoint "$($javaArchive.Name) extract-start"
            Remove-Item $javaRoot -Recurse -Force -ErrorAction SilentlyContinue
            New-Item -ItemType Directory -Path $javaRoot -Force | Out-Null
            & tar.exe -xf (Join-Path $MediaRoot $javaArchive.Archive) -C $javaRoot
            if ($LASTEXITCODE -ne 0) { throw "Unable to extract $($javaArchive.Archive)" }
            Publish-Checkpoint "$($javaArchive.Name) extract-complete"
        }
        $javaHomes[$javaArchive.Name] = Find-JavaHome $javaRoot $javaArchive.Name
    }
    $script:java8Home = $javaHomes["java8"]
    $script:java17Home = $javaHomes["java17"]
    $script:java21Home = $javaHomes["java21"]
    $script:java26Home = $javaHomes["java26"]
    Publish-Checkpoint "runtime setup-complete"
    Run-Profile "1.7.10-forge" "platforms\mc1.7.10\forge" 25695
    Run-Profile "1.21.1-neoforge" "." 25566
    Run-Profile "26.2-fabric" "platforms\mc26.2\fabric" 25645
    Publish-File $summary "windows-client-summary.txt"
    shutdown.exe /s /t 15 /f
    exit 0
} catch {
    New-Item -ItemType Directory -Path $results -Force | Out-Null
    Add-Content -Path $summary -Value "FAIL $(Get-Date -Format o) $($_.Exception.Message)"
    # Preserve per-profile diagnostics even when readiness or screenshot
    # validation fails. The VM is intentionally disposable, so the receiver is
    # the only durable copy once teardown runs.
    Get-ChildItem -Path $results -File -ErrorAction SilentlyContinue | ForEach-Object {
        try { Publish-File $_.FullName ("windows-client-" + $_.Name) } catch { }
    }
    try { Publish-File $summary "windows-client-summary.txt" } catch { }
    shutdown.exe /s /t 15 /f
    exit 1
}
