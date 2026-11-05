$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\Toolchain.ps1"
$javaExe = Get-ProjectJava
$env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $javaExe)
$maven = Get-ProjectMaven
Push-Location $ProjectRoot
try {
    & $maven "-Dmaven.repo.local=$ProjectRoot\.tools\m2" -B -ntp clean verify
    if ($LASTEXITCODE -ne 0) { throw 'Maven 构建或协议测试失败' }
    $cacheDir = Join-Path $ProjectRoot '.tools\npm-cache'
    if (Test-Path -LiteralPath 'package-lock.json') { npm.cmd ci --cache $cacheDir } else { npm.cmd install --cache $cacheDir }
    if ($LASTEXITCODE -ne 0) { throw '工程工具依赖安装失败' }
    npm.cmd run check
    if ($LASTEXITCODE -ne 0) { throw '格式或 JavaScript 代码检查失败' }
    Push-Location 'web'
    try {
        if (Test-Path -LiteralPath 'package-lock.json') { npm.cmd ci --cache $cacheDir } else { npm.cmd install --cache $cacheDir }
        if ($LASTEXITCODE -ne 0) { throw '前端依赖安装失败' }
        npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw 'Vue 生产构建失败' }
    } finally { Pop-Location }
} finally { Pop-Location }
