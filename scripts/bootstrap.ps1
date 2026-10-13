# Portable toolchain only; never installs software globally. Network access required.
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\Toolchain.ps1"
$toolsDir = Join-Path $ProjectRoot '.tools'
New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
node "$PSScriptRoot\download-toolchain.mjs"
if ($LASTEXITCODE -ne 0) { throw 'Official toolchain download / checksum verification failed' }
if (!(Test-Path -LiteralPath (Join-Path $toolsDir 'apache-maven-3.9.11'))) {
    Expand-Archive -LiteralPath (Join-Path $toolsDir 'maven.zip') -DestinationPath $toolsDir -Force
}
if (!(Get-ChildItem -LiteralPath $toolsDir -Directory -Filter 'jdk-17*')) {
    Expand-Archive -LiteralPath (Join-Path $toolsDir 'microsoft-jdk17.zip') -DestinationPath $toolsDir -Force
}
Write-Output 'Portable Java 17 and Maven ready under .tools/'
