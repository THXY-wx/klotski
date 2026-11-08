#requires -Version 5.1
<#
.SYNOPSIS
    构建数字华容道的 Windows 便携版。

.DESCRIPTION
    先使用 Maven 构建应用 JAR 并收集运行时依赖，再调用 JDK 自带的
    jpackage 生成便携版目录：

      dist\Klotski\Klotski.exe

    同时生成 SHA256SUMS.txt。不需要 WiX，也不需要管理员权限。
#>

$ErrorActionPreference = 'Stop'

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$Version = '1.0.0'
$AppName = 'Klotski'
$MainJar = 'klotski-1.0-SNAPSHOT.jar'
$MainClass = 'com.thxy.MainKt'
$InputDir = Join-Path $ProjectRoot 'target\jpackage-input'
$DistDir = Join-Path $ProjectRoot 'dist'

if (-not $env:JAVA_HOME) {
    throw '请先设置 JAVA_HOME 指向 JDK 21 或更高版本。'
}

$JPackage = Join-Path $env:JAVA_HOME 'bin\jpackage.exe'
if (-not (Test-Path -LiteralPath $JPackage)) {
    throw "找不到 jpackage：$JPackage"
}

Push-Location $ProjectRoot
try {
    Write-Host '=== 1/3 构建应用 JAR ==='
    & mvn -B clean package
    if ($LASTEXITCODE -ne 0) { throw 'Maven 构建失败。' }

    Write-Host '=== 2/3 收集运行时依赖 ==='
    if (Test-Path -LiteralPath $InputDir) {
        Remove-Item -LiteralPath $InputDir -Recurse -Force
    }
    New-Item -ItemType Directory -Path $InputDir -Force | Out-Null
    & mvn -B dependency:copy-dependencies "-DoutputDirectory=$InputDir" "-DincludeScope=runtime"
    if ($LASTEXITCODE -ne 0) { throw 'Maven 依赖复制失败。' }

    Copy-Item -LiteralPath (Join-Path $ProjectRoot "target\$MainJar") `
        -Destination $InputDir -Force

    if (Test-Path -LiteralPath $DistDir) {
        Remove-Item -LiteralPath $DistDir -Recurse -Force
    }
    New-Item -ItemType Directory -Path $DistDir -Force | Out-Null

    $packageArgs = @(
        '--type', 'app-image',
        '--dest', $DistDir,
        '--name', $AppName,
        '--app-version', $Version,
        '--vendor', 'THXY',
        '--description', '数字华容道',
        '--input', $InputDir,
        '--main-jar', $MainJar,
        '--main-class', $MainClass,
        '--add-modules', 'java.se,jdk.jsobject,jdk.unsupported,jdk.xml.dom,jdk.localedata'
    )

    Write-Host '=== 3/3 生成便携版 ==='
    & $JPackage @packageArgs
    if ($LASTEXITCODE -ne 0) { throw '便携版生成失败。' }

    $portableExe = Join-Path $DistDir "$AppName\$AppName.exe"
    $portableZip = Join-Path $DistDir "$AppName-portable.zip"
    $debugLog = Join-Path $DistDir "$AppName\debug.log"
    if (Test-Path -LiteralPath $debugLog) {
        Remove-Item -LiteralPath $debugLog -Force
    }
    if (Test-Path -LiteralPath $portableZip) {
        Remove-Item -LiteralPath $portableZip -Force
    }
    Compress-Archive -Path (Join-Path $DistDir $AppName) -DestinationPath $portableZip -CompressionLevel Optimal -Force

    $hash = (Get-FileHash -LiteralPath $portableZip -Algorithm SHA256).Hash.ToLower()
    "$hash  $AppName-portable.zip" |
        Set-Content -LiteralPath (Join-Path $DistDir 'SHA256SUMS.txt') -Encoding UTF8

    Write-Host ''
    Write-Host '打包完成：'
    Write-Host "  便携版目录：$DistDir\$AppName"
    Write-Host "  便携版压缩包：$portableZip"
    Write-Host "  校验值：$DistDir\SHA256SUMS.txt"
} finally {
    Pop-Location
}
