$ErrorActionPreference = 'Stop'
$projectDir = Split-Path -Parent $PSScriptRoot
$pidFile = Join-Path $projectDir '.run\processes.json'
if (!(Test-Path -LiteralPath $pidFile)) { Write-Output '没有由本工程管理的演示进程'; return }
$entries = Get-Content $pidFile -Raw | ConvertFrom-Json
foreach ($entry in $entries) {
    $process = Get-Process -Id $entry.pid -ErrorAction SilentlyContinue
    if ($process -and $process.StartTime.ToUniversalTime().Ticks.ToString() -eq $entry.started) {
        Stop-Process -Id $entry.pid -Force
        Write-Output "已停止 $($entry.role)，进程编号=$($entry.pid)"
    }
}
# Only this workspace-owned metadata file, no recursive deletion.
Remove-Item -LiteralPath $pidFile
