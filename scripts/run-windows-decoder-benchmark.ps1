param([string]$Stage = "Install")

$ErrorActionPreference = "Stop"
$work = "C:\CinemarrDecoderBenchmark"
$log = Join-Path $work "validation.log"
$receiver = "http://192.168.1.15:18080"

function Write-Log([string]$Message) {
    "$(Get-Date -Format o) $Message" | Tee-Object -FilePath $log -Append
}

function Publish-File([string]$Path, [string]$Name) {
    if (Test-Path $Path) {
        & curl.exe --fail --retry 5 --retry-delay 3 -X PUT --data-binary "@$Path" "$receiver/$Name"
        if ($LASTEXITCODE -ne 0) { throw "Unable to publish $Name" }
    }
}

function Stop-Validation([int]$ExitCode) {
    try {
        $archive = Join-Path $work "results.zip"
        if (Test-Path $archive) { Remove-Item $archive -Force }
        Compress-Archive -Path "$work\results", $log -DestinationPath $archive -Force
        Publish-File $archive "results.zip"
    } catch {
        Write-Log "Result upload failed: $($_.Exception.Message)"
    }
    shutdown.exe /s /t 15 /f
    exit $ExitCode
}

try {
    if ($Stage -eq "QueueBenchmark") {
        $mediaVolume = Get-Volume -FileSystemLabel "CINEMARR" | Select-Object -First 1
        if ($null -eq $mediaVolume) { throw "CINEMARR media not found" }
        $mediaScript = "$($mediaVolume.DriveLetter):\run.ps1"
        $command = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$mediaScript`" -Stage Benchmark"
        $runOncePath = "HKCU:\Software\Microsoft\Windows\CurrentVersion\RunOnce"
        New-Item -Path $runOncePath -Force | Out-Null
        New-ItemProperty -Path $runOncePath -Name "CinemarrDecoderBenchmark" -Value $command -PropertyType String -Force | Out-Null
        shutdown.exe /s /t 15 /f
        exit 0
    }

    if ($Stage -eq "Install") {
        $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
        $principal = New-Object Security.Principal.WindowsPrincipal($identity)
        if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
            $arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`" -Stage Install"
            Start-Process -FilePath "powershell.exe" -ArgumentList $arguments -Verb RunAs
            exit 0
        }
        New-Item -ItemType Directory -Path $work -Force | Out-Null
        $mediaVolume = Get-Volume -FileSystemLabel "CINEMARR" | Select-Object -First 1
        if ($null -eq $mediaVolume) { throw "CINEMARR media not found" }
        $media = "$($mediaVolume.DriveLetter):\"
        Copy-Item "$media*" $work -Recurse -Force
        Write-Log "Validation payload copied"
        Expand-Archive -Path (Join-Path $work "jre.zip") -DestinationPath (Join-Path $work "java") -Force
        $driverPath = Join-Path $work "nvidia-driver.exe"
        $signature = Get-AuthenticodeSignature $driverPath
        if ($signature.Status -ne "Valid" -or $signature.SignerCertificate.Subject -notmatch "NVIDIA") {
            throw "NVIDIA driver signature is not valid"
        }
        Write-Log "NVIDIA driver Authenticode signature valid"
        $command = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$work\run.ps1`" -Stage Benchmark"
        $runOncePath = "HKCU:\Software\Microsoft\Windows\CurrentVersion\RunOnce"
        New-Item -Path $runOncePath -Force | Out-Null
        New-ItemProperty -Path $runOncePath -Name "CinemarrDecoderBenchmark" -Value $command -PropertyType String -Force | Out-Null
        $driver = Start-Process -FilePath $driverPath -ArgumentList "-s", "-noreboot" -Wait -PassThru
        Write-Log "NVIDIA installer exit code $($driver.ExitCode)"
        Publish-File $log "install-stage.log"
        # Stop between installation and measurement so the host can make the
        # passed-through P600 the only display adapter. RunOnce resumes the
        # benchmark on the next boot without embedding host credentials.
        shutdown.exe /s /t 15 /f
        exit 0
    }

    New-Item -ItemType Directory -Path (Join-Path $work "results") -Force | Out-Null
    Start-Sleep -Seconds 30
    Get-CimInstance Win32_OperatingSystem | Format-List Caption, Version, BuildNumber | Out-File (Join-Path $work "results\windows.txt")
    Get-CimInstance Win32_VideoController | Format-List Name, DriverVersion, Status | Out-File (Join-Path $work "results\video-controller.txt")
    if (Get-Command nvidia-smi.exe -ErrorAction SilentlyContinue) {
        & nvidia-smi.exe | Out-File (Join-Path $work "results\nvidia-smi.txt")
        & nvidia-smi.exe dmon -s u -c 20 -d 1 | Out-File (Join-Path $work "results\nvidia-utilization.txt")
    }
    $java = Get-ChildItem (Join-Path $work "java") -Filter java.exe -Recurse | Where-Object { $_.FullName -like "*\bin\java.exe" } | Select-Object -First 1
    if ($null -eq $java) { throw "Java runtime not found" }
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    & $java.FullName -version 2>&1 | Out-File (Join-Path $work "results\java.txt")
    $javaVersionExitCode = $LASTEXITCODE
    $ErrorActionPreference = $previous
    if ($javaVersionExitCode -ne 0) { throw "Java version probe exited $javaVersionExitCode" }
    $classpath = "$work\classes;$work\lib\*"
    $main = "stonytark.cinemarr.client.VideoDecoderBenchmark"
    $overall = 0
    $backends = @(
        @{ Name="software"; Effective="software"; Hardware="false" },
        @{ Name="d3d11va"; Effective="d3d11va"; Hardware="true" },
        @{ Name="dxva2"; Effective="dxva2"; Hardware="true" },
        @{ Name="cuda"; Effective="cuda"; Hardware="true" },
        @{ Name="auto"; Effective="d3d11va"; Hardware="true" }
    )
    foreach ($backend in $backends) {
        $name = $backend.Name
        $output = Join-Path $work "results\$name"
        New-Item -ItemType Directory -Path $output -Force | Out-Null
        Write-Log "Running $name decoder benchmark"
        $previous = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        & $java.FullName -Xmx4g -cp $classpath $main --backend $name --output $output --warmups 2 --runs 5 --seconds 0.6 --require-hardware $backend.Hardware --expected-effective $backend.Effective --expect-fallback false --fail-on-acceptance false --gpu "NVIDIA Quadro P600" --driver "NVIDIA 576.02" --classifier windows-x86_64 --minecraft-profile standalone --fixture-dir "$work\fixtures" --resolutions "144p,240p,480p,720p,1080p,1440p,4k" *>&1 | Tee-Object -FilePath (Join-Path $output "console.txt")
        $benchmarkExitCode = $LASTEXITCODE
        $ErrorActionPreference = $previous
        if ($benchmarkExitCode -ne 0) {
            Write-Log "$name benchmark exited $benchmarkExitCode"
            $overall = 1
        }
        $json = Join-Path $output "decoder-benchmark.json"
        if (Test-Path $json) {
            $result = Get-Content $json -Raw | ConvertFrom-Json
            $failed = @($result.rows | Where-Object { -not $_.accepted }).Count
            Write-Log "$name measured rows=$(@($result.rows).Count) failed=$failed"
            if ($failed -ne 0) { $overall = 1 }
        } else {
            $overall = 1
        }
        foreach ($evidenceName in @("decoder-benchmark.json", "decoder-benchmark.csv", "console.txt")) {
            Publish-File (Join-Path $output $evidenceName) "$name-$evidenceName"
        }
    }
    Publish-File (Join-Path $work "results\windows.txt") "windows.txt"
    Publish-File (Join-Path $work "results\video-controller.txt") "video-controller.txt"
    Publish-File (Join-Path $work "results\nvidia-smi.txt") "nvidia-smi.txt"
    Publish-File (Join-Path $work "results\nvidia-utilization.txt") "nvidia-utilization.txt"
    $clientBootstrap = Join-Path $work "CinemarrClientGate-bootstrap.ps1"
    @'
$deadline = (Get-Date).AddMinutes(20)
while ((Get-Date) -lt $deadline) {
    $volume = Get-Volume -FileSystemLabel "CINEMARRCLIENT" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -ne $volume) {
        $root = "$($volume.DriveLetter):\"
        & (Join-Path $root "run-client.ps1") -MediaRoot $root
        exit $LASTEXITCODE
    }
    Start-Sleep -Seconds 3
}
exit 2
'@ | Set-Content -Path $clientBootstrap -Encoding UTF8
    $clientCommand = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$clientBootstrap`""
    $runOncePath = "HKCU:\Software\Microsoft\Windows\CurrentVersion\RunOnce"
    New-Item -Path $runOncePath -Force | Out-Null
    New-ItemProperty -Path $runOncePath -Name "CinemarrClientGate" -Value $clientCommand -PropertyType String -Force | Out-Null
    Stop-Validation $overall
} catch {
    New-Item -ItemType Directory -Path $work -Force | Out-Null
    Write-Log "Fatal validation error: $($_.Exception.ToString())"
    Stop-Validation 2
}
