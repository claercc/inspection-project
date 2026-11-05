$ProjectRoot = Split-Path -Parent $PSScriptRoot
<#
.SYNOPSIS
查找本工程使用的 Java 启动程序，无参数。
.DESCRIPTION
供构建、启动和验证脚本调用，按 .tools 中的便携 JDK、JAVA_HOME、PATH 顺序查找。
.OUTPUTS
System.String。Java 可执行文件的完整路径；找不到 PATH 中的 Java 时抛出错误。
.EXAMPLE
$javaExe = Get-ProjectJava
#>
function Get-ProjectJava {
    $portable = Get-ChildItem -LiteralPath (Join-Path $ProjectRoot '.tools') -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($portable) { return Join-Path $portable.FullName 'bin\java.exe' }
    if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        return Join-Path $env:JAVA_HOME 'bin\java.exe'
    }
    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if (!$javaCommand) { throw '未找到 Java，请运行 scripts/bootstrap.ps1 或配置 JAVA_HOME。' }
    return $javaCommand.Source
}
<#
.SYNOPSIS
查找本工程使用的 Maven 启动脚本，无参数。
.DESCRIPTION
优先使用 .tools 中的 Maven 3.9.11，否则在 PATH 中查找 mvn.cmd，供构建脚本调用。
.OUTPUTS
System.String。mvn.cmd 的完整路径；没有可用候选时抛出查找错误。
.EXAMPLE
$maven = Get-ProjectMaven
#>
function Get-ProjectMaven {
    $portable = Join-Path $ProjectRoot '.tools\apache-maven-3.9.11\bin\mvn.cmd'
    if (Test-Path -LiteralPath $portable) { return $portable }
    $mavenCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if (!$mavenCommand) { throw '未找到 Maven，请运行 scripts/bootstrap.ps1 或配置 PATH。' }
    return $mavenCommand.Source
}
