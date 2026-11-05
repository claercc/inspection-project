$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\Toolchain.ps1"
$env:JAVA_BIN = Get-ProjectJava
Push-Location $ProjectRoot
try { node tests/e2e.mjs; if ($LASTEXITCODE -ne 0) { throw '端到端验证失败' } }
finally { Pop-Location }
