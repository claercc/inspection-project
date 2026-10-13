$ErrorActionPreference = 'Stop'
$projectDir = Split-Path -Parent $PSScriptRoot
$pidFile = Join-Path $projectDir '.run\processes.json'
if (!(Test-Path -LiteralPath $pidFile)) { Write-Output 'No managed demo processes'; return }
$entries = Get-Content $pidFile -Raw | ConvertFrom-Json
foreach ($entry in $entries) {
    $process = Get-Process -Id $entry.pid -ErrorAction SilentlyContinue
    if ($process -and $process.StartTime.ToUniversalTime().Ticks.ToString() -eq $entry.started) {
        Stop-Process -Id $entry.pid -Force
        Write-Output "Stopped $($entry.role) PID=$($entry.pid)"
    }
}
# Only this workspace-owned metadata file, no recursive deletion.
Remove-Item -LiteralPath $pidFile
