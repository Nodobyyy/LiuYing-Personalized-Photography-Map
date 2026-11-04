param([switch]$CoreOnly, [switch]$UseMirror, [ValidateSet('Both', 'Offline', 'Online')][string]$MapMode = 'Both')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
if (-not $env:JAVA_HOME) {
    $jdk = Get-ChildItem -LiteralPath "$projectRoot/.tooling" -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin/java.exe') } | Select-Object -First 1
    if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
}
if (-not $env:JAVA_HOME) { throw 'JDK 17 required. Set JAVA_HOME or extract a JDK under .tooling.' }
$env:GRADLE_USER_HOME = "$projectRoot/.tooling/gradle-home"
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = "$projectRoot/.tooling/android-sdk" }
$gradle = "$projectRoot/.tooling/gradle-8.9/bin/gradle.bat"
if (-not (Test-Path -LiteralPath $gradle)) { $gradle = "$projectRoot/gradlew.bat" }
$extraArgs = @()
if ($UseMirror) { $extraArgs += '-PuseMirror=true' }
$tasks = @(':core:test')
if (-not $CoreOnly) {
    $flavors = if ($MapMode -eq 'Both') { 'Offline', 'Online' } else { $MapMode }
    foreach ($flavor in $flavors) {
        $tasks += ":app:test${flavor}DebugUnitTest", ":app:assemble${flavor}Debug", ":app:lint${flavor}Debug"
    }
}
& $gradle @tasks --console=plain @extraArgs
if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
