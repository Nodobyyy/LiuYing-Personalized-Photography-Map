param(
    [string]$ArchiveDirectory = 'app/src/online/assets/maps/overview',
    [string]$Pmtiles = '.tooling/pmtiles/pmtiles.exe',
    [string]$OutputDirectory = 'app/src/online/assets/maps'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

# Read the polygon rings from an MVT layer. Geometry command coordinates are
# delta encoded and accumulate across rings of the same feature.
Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Text;

public static class OverviewTile
{
    private static int ReadVarint(byte[] data, ref int offset)
    {
        int value = 0, shift = 0;
        byte next;
        do {
            next = data[offset++];
            value |= (next & 127) << shift;
            shift += 7;
        } while ((next & 128) != 0);
        return value;
    }

    private static IEnumerable<(int field, byte[] data)> Messages(byte[] data)
    {
        int offset = 0;
        while (offset < data.Length) {
            int tag = ReadVarint(data, ref offset);
            switch (tag & 7) {
                case 0: ReadVarint(data, ref offset); break;
                case 1: offset += 8; break;
                case 2:
                    int size = ReadVarint(data, ref offset);
                    var message = new byte[size];
                    Buffer.BlockCopy(data, offset, message, 0, size);
                    offset += size;
                    yield return (tag >> 3, message);
                    break;
                case 5: offset += 4; break;
                default: throw new InvalidDataException("Unsupported protobuf wire type.");
            }
        }
    }

    private static int Delta(int value) => (value >> 1) ^ -(value & 1);

    public static float[][] ReadRings(byte[] tile, string name)
    {
        using var input = new GZipStream(new MemoryStream(tile), CompressionMode.Decompress);
        using var decoded = new MemoryStream();
        input.CopyTo(decoded);
        foreach (var layer in Messages(decoded.ToArray())) {
            if (layer.field != 3) continue;
            var features = new List<byte[]>();
            string layerName = null;
            foreach (var field in Messages(layer.data)) {
                if (field.field == 1) layerName = Encoding.UTF8.GetString(field.data);
                if (field.field == 2) features.Add(field.data);
            }
            if (layerName != name) continue;

            // The Protomaps overview uses the default MVT extent of 4096.
            var rings = new List<float[]>();
            foreach (var feature in features) {
                foreach (var field in Messages(feature)) {
                    if (field.field != 4) continue;
                    int offset = 0, x = 0, y = 0;
                    var ring = new List<float>();
                    while (offset < field.data.Length) {
                        int command = ReadVarint(field.data, ref offset);
                        int kind = command & 7;
                        if (kind == 7) {
                            if (ring.Count >= 6) rings.Add(ring.ToArray());
                            ring.Clear();
                            continue;
                        }
                        if (kind != 1 && kind != 2) throw new InvalidDataException("Unknown MVT geometry command.");
                        for (int i = 0; i < (command >> 3); i++) {
                            x += Delta(ReadVarint(field.data, ref offset));
                            y += Delta(ReadVarint(field.data, ref offset));
                            ring.Add(x / 4096f);
                            ring.Add(y / 4096f);
                        }
                    }
                }
            }
            return rings.ToArray();
        }
        throw new InvalidDataException("Missing MVT layer: " + name);
    }
}
'@

$pmtilesPath = (Resolve-Path $Pmtiles).Path
function Read-Tile([string]$Archive, [int]$Zoom, [int]$X, [int]$Y) {
    $info = [Diagnostics.ProcessStartInfo]::new($pmtilesPath)
    $info.UseShellExecute = $false
    $info.RedirectStandardOutput = $true
    foreach ($arg in @('tile', $Archive, $Zoom, $X, $Y)) { $info.ArgumentList.Add([string]$arg) }
    $process = [Diagnostics.Process]::Start($info)
    $bytes = [IO.MemoryStream]::new()
    try {
        $process.StandardOutput.BaseStream.CopyTo($bytes)
        $process.WaitForExit()
        if ($process.ExitCode -ne 0 -or !$bytes.Length) { throw "Could not read tile $Zoom/$X/$Y" }
        return ,$bytes.ToArray()
    } finally {
        $bytes.Dispose()
        $process.Dispose()
    }
}

$outputPath = (Resolve-Path $OutputDirectory).Path
$base = Read-Tile (Join-Path $ArchiveDirectory 'context.pmtiles') 0 0 0
$land = [OverviewTile]::ReadRings($base, 'earth')
$water = [OverviewTile]::ReadRings($base, 'water')
$size = 2048

function Draw-Polygons($graphics, $rings, $color) {
    $path = [Drawing.Drawing2D.GraphicsPath]::new([Drawing.Drawing2D.FillMode]::Winding)
    $brush = [Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml($color))
    try {
        foreach ($ring in $rings) {
            $points = [Drawing.PointF[]]::new($ring.Length / 2)
            for ($i = 0; $i -lt $points.Length; $i++) {
                $points[$i] = [Drawing.PointF]::new($ring[2*$i]*$size, $ring[2*$i+1]*$size)
            }
            $path.AddPolygon($points)
        }
        $graphics.FillPath($brush, $path)
    } finally {
        $brush.Dispose()
        $path.Dispose()
    }
}

foreach ($theme in @(
    @{ Name='day'; Land='#efeee7'; Water='#a9d4e9' },
    @{ Name='night'; Land='#202d25'; Water='#172f3b' }
)) {
    $bitmap = [Drawing.Bitmap]::new($size, $size)
    $graphics = [Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.Clear([Drawing.ColorTranslator]::FromHtml($theme.Water))
        $graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
        Draw-Polygons $graphics $land $theme.Land
        Draw-Polygons $graphics $water $theme.Water
        $bitmap.Save((Join-Path $outputPath "globe-streets-$($theme.Name).png"), [Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

$atlas = [Drawing.Bitmap]::new(1024, 1024)
$graphics = [Drawing.Graphics]::FromImage($atlas)
try {
    $archive = Join-Path $ArchiveDirectory 'world-satellite.pmtiles'
    foreach ($x in 0..3) {
        foreach ($y in 0..3) {
            $stream = [IO.MemoryStream]::new((Read-Tile $archive 2 $x $y))
            $tile = [Drawing.Bitmap]::new($stream)
            try {
                if ($tile.Width -ne 256 -or $tile.Height -ne 256) { throw 'Unexpected satellite tile size' }
                $graphics.DrawImageUnscaled($tile, 256*$x, 256*$y)
            } finally {
                $tile.Dispose()
                $stream.Dispose()
            }
        }
    }
    $atlas.Save((Join-Path $outputPath 'globe-satellite.png'), [Drawing.Imaging.ImageFormat]::Png)
} finally {
    $graphics.Dispose()
    $atlas.Dispose()
}
