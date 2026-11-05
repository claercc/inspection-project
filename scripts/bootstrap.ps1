# Portable toolchain only; never installs software globally. Network access required.
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\Toolchain.ps1"
$toolsDir = Join-Path $ProjectRoot '.tools'
New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
node "$PSScriptRoot\download-toolchain.mjs"
if ($LASTEXITCODE -ne 0) { throw '官方工具下载或校验失败' }
if (!(Test-Path -LiteralPath (Join-Path $toolsDir 'apache-maven-3.9.11'))) {
    Expand-Archive -LiteralPath (Join-Path $toolsDir 'maven.zip') -DestinationPath $toolsDir -Force
}
if (!(Get-ChildItem -LiteralPath $toolsDir -Directory -Filter 'jdk-17*')) {
    Expand-Archive -LiteralPath (Join-Path $toolsDir 'microsoft-jdk17.zip') -DestinationPath $toolsDir -Force
}
Write-Output '便携 Java 17 和 Maven 已在 .tools/ 目录准备就绪'
