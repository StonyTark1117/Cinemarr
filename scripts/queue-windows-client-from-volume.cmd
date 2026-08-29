@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$v = Get-Volume -FileSystemLabel 'CINEMARRCLIENT' | Select-Object -First 1; if ($null -eq $v) { exit 2 }; $root = $v.DriveLetter + ':\'; & ($root + 'run-client.ps1') -MediaRoot $root -QueueOnly; exit $LASTEXITCODE"
