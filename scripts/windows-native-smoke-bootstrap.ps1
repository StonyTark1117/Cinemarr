$ErrorActionPreference = "Stop"

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


# Always execute the runner shipped on the current read-only payload.
$media = Resolve-VolumeRoot "CINEMARR"
& (Join-Path $media "windows-native-decoder-smoke.ps1")
