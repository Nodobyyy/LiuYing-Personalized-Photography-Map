# Fallback when Maven is unreachable; uses Gradle's bundled Kotlin 1.9.23 and JUnit.
# This checks the pure algorithm, not the Kotlin 2.0 Android build.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
$java = Join-Path $projectRoot '.tooling/jdk-17.0.2/bin/java.exe'
$lib = Join-Path $projectRoot '.tooling/gradle-8.9/lib'
$output = Join-Path $projectRoot '.tooling/core-tests.jar'
$compilePath = "$lib/kotlin-stdlib-1.9.23.jar;$lib/junit-4.13.2.jar"
$sources = Get-ChildItem core/src -Filter '*.kt' -Recurse -File | ForEach-Object FullName
& $java -cp "$lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -classpath $compilePath -d $output @sources
if ($LASTEXITCODE -ne 0) { throw 'Core compilation failed' }
& $java -cp "$output;$lib/*" org.junit.runner.JUnitCore cn.photomap.core.PhotoClusteringTest
if ($LASTEXITCODE -ne 0) { throw 'Core tests failed' }
