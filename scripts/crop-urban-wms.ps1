$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
$groups=Get-Content -Raw .tooling/urban-satellite-groups.json | ConvertFrom-Json | Where-Object {$_.z -eq 14}
$groups | ForEach-Object -Parallel {
    Add-Type -AssemblyName System.Drawing
    $g=$_; $base=$using:projectRoot
    $key="$($g.z)-$($g.x)-$($g.y)"
    $inputFile=Join-Path $base ".tooling/urban-wms/$key.jpg"
    $marker=Join-Path $base ".tooling/urban-wms/$key.cropped"
    if(-not (Test-Path -LiteralPath $marker)) {
        while(-not (Test-Path -LiteralPath $inputFile) -and -not (Test-Path -LiteralPath $marker)){Start-Sleep -Seconds 1}
        if(Test-Path -LiteralPath $marker){return}
        $left=($g.tiles.x | Measure-Object -Minimum).Minimum
        $top=($g.tiles.y | Measure-Object -Minimum).Minimum
        $image=[System.Drawing.Bitmap]::new($inputFile)
        try {
            $codec=[System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object {$_.MimeType -eq 'image/jpeg'}
            $parameters=[System.Drawing.Imaging.EncoderParameters]::new(1)
            $parameters.Param[0]=[System.Drawing.Imaging.EncoderParameter]::new([System.Drawing.Imaging.Encoder]::Quality,[long]90)
            foreach($tile in $g.tiles) {
                $dir=Join-Path $base ".tooling/urban-satellite-tiles/$($tile.z)/$($tile.x)"
                [IO.Directory]::CreateDirectory($dir) | Out-Null
                $file=Join-Path $dir "$($tile.y).jpg"
                if(-not (Test-Path -LiteralPath $file)) {
                    $rect=[System.Drawing.Rectangle]::new([int](($tile.x-$left)*256),[int](($tile.y-$top)*256),256,256)
                    $part=$image.Clone($rect,[System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
                    try {$part.Save($file,$codec,$parameters)} finally {$part.Dispose()}
                }
            }
            $parameters.Dispose()
            [IO.File]::WriteAllText($marker,'WMS EPSG:3857 exact 256px grid crops; JPEG quality 90; EOX 2016 CC BY 4.0')
        } finally {$image.Dispose()}
    }
} -ThrottleLimit 4
'All urban WMS windows cropped to exact XYZ grid.'
