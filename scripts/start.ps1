param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\Toolchain.ps1"
if (!$SkipBuild) { & "$PSScriptRoot\build.ps1" }
$runDir = Join-Path $ProjectRoot '.run'
$pidFile = Join-Path $runDir 'processes.json'
if (Test-Path -LiteralPath $pidFile) {
    foreach ($entry in (Get-Content $pidFile -Raw | ConvertFrom-Json)) {
        $existing = Get-Process -Id $entry.pid -ErrorAction SilentlyContinue
        if ($existing -and $existing.StartTime.ToUniversalTime().Ticks.ToString() -eq $entry.started) { throw 'Demo already running; use scripts/stop.ps1 first.' }
    }
}
foreach ($port in @(1883,1502,8080,8088)) {
    $tcp = New-Object Net.Sockets.TcpClient
    try { $tcp.Connect('127.0.0.1', $port); throw "Port $port is already in use" }
    catch [Net.Sockets.SocketException] {} finally { $tcp.Dispose() }
}
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
$javaExe = Get-ProjectJava
$nodeExe = (Get-Command node).Source
$entries = @()
function Launch-Demo($role, $exe, $arguments) {
    $process = Start-Process -FilePath $exe -ArgumentList $arguments -WorkingDirectory $ProjectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runDir "$role.out.log") -RedirectStandardError (Join-Path $runDir "$role.err.log")
    $script:entries += [pscustomobject]@{ role=$role; pid=$process.Id; started=$process.StartTime.ToUniversalTime().Ticks.ToString() }
    $script:entries | ConvertTo-Json | Set-Content -LiteralPath $pidFile -Encoding UTF8
}
$vars = @('MQTT_PORT','MQTT_URL','MODBUS_BIND','MODBUS_HOST','MODBUS_PORT','MODBUS_UNIT','DEVICE_ID','SERVER_BIND','SERVER_PORT','API_URL')
$saved = @{}; foreach ($v in $vars) { $saved[$v] = [Environment]::GetEnvironmentVariable($v, 'Process') }
try {
    $env:MQTT_PORT='1883'; $env:MQTT_URL='tcp://127.0.0.1:1883'
    $env:MODBUS_BIND='127.0.0.1'; $env:MODBUS_HOST='127.0.0.1'; $env:MODBUS_PORT='1502'; $env:MODBUS_UNIT='1'
    $env:DEVICE_ID='robot-001'; $env:SERVER_BIND='127.0.0.1'; $env:SERVER_PORT='8080'; $env:API_URL='http://127.0.0.1:8080'
    Launch-Demo 'broker' $nodeExe @('"' + (Join-Path $ProjectRoot 'scripts\broker.mjs') + '"')
    Launch-Demo 'robot' $javaExe @('-jar', ('"' + (Join-Path $ProjectRoot 'simulator\robot\target\robot-simulator-0.1.0.jar') + '"'))
    Launch-Demo 'edge' $javaExe @('-jar', ('"' + (Join-Path $ProjectRoot 'edge\target\edge-0.1.0.jar') + '"'))
    Launch-Demo 'server' $javaExe @('-jar', ('"' + (Join-Path $ProjectRoot 'server\target\server-0.1.0.jar') + '"'))
    Launch-Demo 'web' $nodeExe @(('"' + (Join-Path $ProjectRoot 'web\node_modules\vite\bin\vite.js') + '"'), 'preview', '--host', '127.0.0.1', '--port', '8088', '--strictPort', '--config', ('"' + (Join-Path $ProjectRoot 'web\vite.config.js') + '"'), ('"' + (Join-Path $ProjectRoot 'web') + '"'))
    $ready = $false
    for ($i=0; $i -lt 45; $i++) {
        try { $state = Invoke-RestMethod 'http://127.0.0.1:8088/api/state'; if ($state.online) { $ready=$true; break } } catch {}
        Start-Sleep -Seconds 1
    }
    if (!$ready) { throw 'Demo readiness failed; inspect .run/*.log' }
    Write-Output 'Demo ready: http://127.0.0.1:8088 (simulation, local only)'
} catch { & "$PSScriptRoot\stop.ps1"; throw }
finally { foreach ($v in $vars) { [Environment]::SetEnvironmentVariable($v, $saved[$v], 'Process') } }
