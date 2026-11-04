$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
$groups=Get-Content -Raw .tooling/urban-satellite-groups.json | ConvertFrom-Json
for($level=13;$level -ge 8;$level--) {
    $tiles=@($groups | Where-Object {$_.z -eq $level} | ForEach-Object {$_.tiles})
    $tiles | ForEach-Object -Parallel {
        Add-Type -AssemblyName System.Drawing
        $base=$using:projectRoot; $t=$_
        $dir=Join-Path $base ".tooling/urban-satellite-tiles/$($t.z)/$($t.x)"
        [IO.Directory]::CreateDirectory($dir) | Out-Null
        $out=Join-Path $dir "$($t.y).jpg"
        if(-not (Test-Path -LiteralPath $out)) {
            $canvas=[System.Drawing.Bitmap]::new(512,512)
            $graphics=[System.Drawing.Graphics]::FromImage($canvas)
            $graphics.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::HighQualityBilinear
            try {
                for($dy=0;$dy -lt 2;$dy++){for($dx=0;$dx -lt 2;$dx++){
                    $z=[int]$t.z+1;$x=[int]$t.x*2+$dx;$y=[int]$t.y*2+$dy
                    $found=$false
                    for($ancestor=$z;$ancestor -ge 0;$ancestor--) {
                        $factor=[math]::Pow(2,$z-$ancestor);$ax=[math]::Floor($x/$factor);$ay=[math]::Floor($y/$factor)
                        $file=Join-Path $base ".tooling/urban-satellite-tiles/$ancestor/$ax/$ay.jpg"
                        if(-not (Test-Path -LiteralPath $file)){$file=Join-Path $base ".tooling/satellite-cache/$ancestor-$ax-$ay.jpg"}
                        if(Test-Path -LiteralPath $file){
                            $image=[System.Drawing.Bitmap]::new($file)
                            try {
                                $size=256/$factor;$source=[System.Drawing.RectangleF]::new([single](($x%$factor)*$size),[single](($y%$factor)*$size),[single]$size,[single]$size)
                                $destination=[System.Drawing.RectangleF]::new([single]($dx*256),[single]($dy*256),256,256)
                                $graphics.DrawImage($image,$destination,$source,[System.Drawing.GraphicsUnit]::Pixel)
                            } finally {$image.Dispose()}
                            $found=$true;break
                        }
                    }
                    if(-not $found){throw "Missing real imagery at $z/$x/$y"}
                }}
                $small=[System.Drawing.Bitmap]::new(256,256);$g=[System.Drawing.Graphics]::FromImage($small)
                try {
                    $g.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::HighQualityBilinear
                    $g.DrawImage($canvas,0,0,256,256)
                    $codec=[System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object {$_.MimeType -eq 'image/jpeg'}
                    $parameters=[System.Drawing.Imaging.EncoderParameters]::new(1)
                    $parameters.Param[0]=[System.Drawing.Imaging.EncoderParameter]::new([System.Drawing.Imaging.Encoder]::Quality,[long]90)
                    try {$small.Save($out,$codec,$parameters)} finally {$parameters.Dispose()}
                } finally {$g.Dispose();$small.Dispose()}
            } finally {$graphics.Dispose();$canvas.Dispose()}
        }
    } -ThrottleLimit 4
    "Completed real-imagery pyramid Z$level ($($tiles.Count) target tiles)"
}
