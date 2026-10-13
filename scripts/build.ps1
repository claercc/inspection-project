$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\Toolchain.ps1"
$javaExe = Get-ProjectJava
$env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $javaExe)
$maven = Get-ProjectMaven
Push-Location $ProjectRoot
try {
    & $maven "-Dmaven.repo.local=$ProjectRoot\.tools\m2" -B -ntp clean verify
    if ($LASTEXITCODE -ne 0) { throw 'Maven build / protocol tests failed' }
    $cacheDir = Join-Path $ProjectRoot '.tools\npm-cache'
    if (Test-Path -LiteralPath 'package-lock.json') { npm.cmd ci --cache $cacheDir } else { npm.cmd install --cache $cacheDir }
    if ($LASTEXITCODE -ne 0) { throw 'Tool dependency installation failed' }
    Push-Location 'web'
    try {
        if (Test-Path -LiteralPath 'package-lock.json') { npm.cmd ci --cache $cacheDir } else { npm.cmd install --cache $cacheDir }
        if ($LASTEXITCODE -ne 0) { throw 'Web dependency installation failed' }
        npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw 'Vue build failed' }
    } finally { Pop-Location }
} finally { Pop-Location }
