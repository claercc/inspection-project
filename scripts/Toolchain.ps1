$ProjectRoot = Split-Path -Parent $PSScriptRoot
function Get-ProjectJava {
    $portable = Get-ChildItem -LiteralPath (Join-Path $ProjectRoot '.tools') -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($portable) { return Join-Path $portable.FullName 'bin\java.exe' }
    if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        return Join-Path $env:JAVA_HOME 'bin\java.exe'
    }
    return (Get-Command java -ErrorAction Stop).Source
}
function Get-ProjectMaven {
    $portable = Join-Path $ProjectRoot '.tools\apache-maven-3.9.11\bin\mvn.cmd'
    if (Test-Path -LiteralPath $portable) { return $portable }
    return (Get-Command mvn.cmd -ErrorAction Stop).Source
}
