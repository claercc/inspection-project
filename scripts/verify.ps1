$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\Toolchain.ps1"
$env:JAVA_BIN = Get-ProjectJava
Push-Location $ProjectRoot
try { node tests/e2e.mjs; if ($LASTEXITCODE -ne 0) { throw 'End-to-end verification failed' } }
finally { Pop-Location }
