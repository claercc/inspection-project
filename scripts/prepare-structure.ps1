# Empty directories describe planned boundaries; no placeholder Java/Vue classes.
$ErrorActionPreference = 'Stop'
$projectDir = Split-Path -Parent $PSScriptRoot
$directories = @(
    'web/src/assets/images', 'web/src/assets/styles', 'web/src/layouts',
    'web/src/components/common', 'web/src/components/monitoring', 'web/src/components/charts',
    'web/src/views/monitoring', 'web/src/views/inspections', 'web/src/views/alarms',
    'web/src/views/history', 'web/src/views/diagnostics', 'web/src/views/devices', 'web/src/views/settings',
    'web/src/api', 'web/src/composables', 'web/src/router', 'web/src/stores', 'web/src/config', 'web/src/utils',
    'server/src/main/resources/db/migration', 'server/src/main/resources/config',
    'edge/src/main/java/demo/inspection/edge/application', 'edge/src/main/java/demo/inspection/edge/domain',
    'edge/src/main/java/demo/inspection/edge/infrastructure/mqtt',
    'edge/src/main/java/demo/inspection/edge/infrastructure/modbus',
    'edge/src/main/java/demo/inspection/edge/infrastructure/camera',
    'edge/src/main/java/demo/inspection/edge/config',
    'simulator/camera', 'deploy/environments', 'deploy/media'
)
foreach ($module in @('device', 'inspection', 'alarm', 'file', 'camera', 'access', 'audit', 'diagnostics')) {
    foreach ($layer in @('api', 'application', 'domain', 'infrastructure')) {
        $directories += "server/src/main/java/demo/inspection/server/$module/$layer"
    }
}
$created = 0
foreach ($directory in $directories) {
    $destination = Join-Path $projectDir $directory
    if (!(Test-Path -LiteralPath $destination -PathType Container)) {
        New-Item -ItemType Directory -Path $destination -Force | Out-Null
        $created++
    }
}
Write-Output "Architecture directories ready: $($directories.Count) planned, $created newly created."
