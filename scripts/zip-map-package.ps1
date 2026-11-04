param(
    [Parameter(Mandatory=$true)][string]$InputDirectory,
    [Parameter(Mandatory=$true)][string]$OutputZip
)
$ErrorActionPreference = 'Stop'
$source = (Resolve-Path -LiteralPath $InputDirectory).Path
$destination = [IO.Path]::GetFullPath($OutputZip)
$jar = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/jar.exe' } else { (Get-Command jar.exe -ErrorAction SilentlyContinue).Source }
if (-not $jar) {
    $jdk = Get-ChildItem -Directory -LiteralPath (Join-Path $PSScriptRoot '../.tooling') -Filter 'jdk-*' -ErrorAction SilentlyContinue |
        Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin/jar.exe') } | Select-Object -First 1
    if (-not $jdk) { throw 'JDK 17 required. Set JAVA_HOME before packaging.' }
    $jar = Join-Path $jdk.FullName 'bin/jar.exe'
}
$manifest = Get-Content -Raw -LiteralPath (Join-Path $source 'bundle.json') | ConvertFrom-Json
$names = @('bundle.json') + @($manifest.files.PSObject.Properties.Name)
foreach ($name in $names) {
    if ($name -notmatch '^[A-Za-z0-9_][A-Za-z0-9_./ -]*$' -or $name.Split('/') -contains '..' -or $name.Split('/') -contains '.') {
        throw "Invalid map entry: $name"
    }
    if (-not (Test-Path -LiteralPath (Join-Path $source $name) -PathType Leaf)) { throw "Missing map entry: $name" }
}
# The JDK writes sizes and CRCs in STORED local headers, including ZIP64
# sizes. Android ZipInputStream cannot read STORED entries with descriptors.
$partial = "$destination.partial"
Push-Location -LiteralPath $source
try {
    & $jar --create --no-compress --no-manifest --file $partial @names
    if ($LASTEXITCODE -ne 0) { throw "jar failed with exit code $LASTEXITCODE" }
    Move-Item -LiteralPath $partial -Destination $destination -Force
} finally {
    Pop-Location
    if (Test-Path -LiteralPath $partial) { Remove-Item -LiteralPath $partial }
}
Get-Item -LiteralPath $destination | Select-Object Name,Length
