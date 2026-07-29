param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing

function New-RoundedRectanglePath {
    param(
        [System.Drawing.RectangleF]$Rectangle,
        [float]$Radius
    )

    $path = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $diameter = $Radius * 2.0

    if ($diameter -le 0.0) {
        $path.AddRectangle($Rectangle)
        return $path
    }

    $arc = [System.Drawing.RectangleF]::new(
        $Rectangle.X,
        $Rectangle.Y,
        $diameter,
        $diameter
    )
    $path.AddArc($arc, 180, 90)

    $arc.X = $Rectangle.Right - $diameter
    $path.AddArc($arc, 270, 90)

    $arc.Y = $Rectangle.Bottom - $diameter
    $path.AddArc($arc, 0, 90)

    $arc.X = $Rectangle.Left
    $path.AddArc($arc, 90, 90)
    $path.CloseFigure()

    return $path
}

function New-Canvas {
    param(
        [int]$Width,
        [int]$Height
    )

    $bitmap = [System.Drawing.Bitmap]::new(
        $Width,
        $Height,
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
    )
    $bitmap.SetResolution(96.0, 96.0)
    return $bitmap
}

function Initialize-Graphics {
    param([System.Drawing.Bitmap]$Bitmap)

    $graphics = [System.Drawing.Graphics]::FromImage($Bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    return $graphics
}

function Draw-PlaybackMark {
    param(
        [System.Drawing.Graphics]$Graphics,
        [System.Drawing.RectangleF]$Bounds
    )

    $scale = $Bounds.Width / 96.0
    $shadowBounds = [System.Drawing.RectangleF]::new(
        $Bounds.X + (3.0 * $scale),
        $Bounds.Y + (5.0 * $scale),
        $Bounds.Width,
        $Bounds.Height
    )
    $shadowPath = New-RoundedRectanglePath -Rectangle $shadowBounds -Radius (20.0 * $scale)
    $shadowBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(74, 0, 0, 0))
    $Graphics.FillPath($shadowBrush, $shadowPath)
    $shadowBrush.Dispose()
    $shadowPath.Dispose()

    $logoPath = New-RoundedRectanglePath -Rectangle $Bounds -Radius (20.0 * $scale)
    $logoBrush = [System.Drawing.Drawing2D.LinearGradientBrush]::new(
        $Bounds,
        [System.Drawing.ColorTranslator]::FromHtml('#7C3AED'),
        [System.Drawing.ColorTranslator]::FromHtml('#0EA5E9'),
        32.0
    )
    $Graphics.FillPath($logoBrush, $logoPath)
    $logoBrush.Dispose()

    $glowBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(80, 255, 255, 255))
    $Graphics.FillEllipse(
        $glowBrush,
        $Bounds.X + (43.0 * $scale),
        $Bounds.Y - (17.0 * $scale),
        76.0 * $scale,
        76.0 * $scale
    )
    $glowBrush.Dispose()

    $outlinePen = [System.Drawing.Pen]::new(
        [System.Drawing.Color]::FromArgb(112, 255, 255, 255),
        [Math]::Max(1.0, 1.4 * $scale)
    )
    $Graphics.DrawPath($outlinePen, $logoPath)
    $outlinePen.Dispose()

    $slotBrush = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#FFD166'))
    foreach ($slotY in @(17.0, 40.0, 63.0)) {
        $slot = [System.Drawing.RectangleF]::new(
            $Bounds.X + (12.0 * $scale),
            $Bounds.Y + ($slotY * $scale),
            7.0 * $scale,
            12.0 * $scale
        )
        $slotPath = New-RoundedRectanglePath -Rectangle $slot -Radius (2.5 * $scale)
        $Graphics.FillPath($slotBrush, $slotPath)
        $slotPath.Dispose()
    }
    $slotBrush.Dispose()

    $play = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $play.AddPolygon([System.Drawing.PointF[]]@(
        [System.Drawing.PointF]::new($Bounds.X + (39.0 * $scale), $Bounds.Y + (27.0 * $scale)),
        [System.Drawing.PointF]::new($Bounds.X + (39.0 * $scale), $Bounds.Y + (69.0 * $scale)),
        [System.Drawing.PointF]::new($Bounds.X + (75.0 * $scale), $Bounds.Y + (48.0 * $scale))
    ))
    $playBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::White)
    $Graphics.FillPath($playBrush, $play)
    $playBrush.Dispose()
    $play.Dispose()
    $logoPath.Dispose()
}

function Save-Png {
    param(
        [System.Drawing.Bitmap]$Bitmap,
        [string]$Path
    )

    $directory = Split-Path -Parent $Path
    [System.IO.Directory]::CreateDirectory($directory) | Out-Null
    $Bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
}

function New-TvBanner {
    param(
        [int]$Width,
        [int]$Height,
        [string]$OutputPath
    )

    $scale = $Width / 320.0
    $bitmap = New-Canvas -Width $Width -Height $Height
    $graphics = Initialize-Graphics -Bitmap $bitmap

    try {
        $canvas = [System.Drawing.RectangleF]::new(0.0, 0.0, [float]$Width, [float]$Height)
        $background = [System.Drawing.Drawing2D.LinearGradientBrush]::new(
            $canvas,
            [System.Drawing.ColorTranslator]::FromHtml('#07111F'),
            [System.Drawing.ColorTranslator]::FromHtml('#152A47'),
            18.0
        )
        $graphics.FillRectangle($background, $canvas)
        $background.Dispose()

        $violetGlow = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(52, 124, 58, 237))
        $graphics.FillEllipse($violetGlow, -70.0 * $scale, -95.0 * $scale, 265.0 * $scale, 265.0 * $scale)
        $violetGlow.Dispose()

        $cyanGlow = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(34, 14, 165, 233))
        $graphics.FillEllipse($cyanGlow, 213.0 * $scale, 55.0 * $scale, 170.0 * $scale, 170.0 * $scale)
        $cyanGlow.Dispose()

        $stripePen = [System.Drawing.Pen]::new(
            [System.Drawing.Color]::FromArgb(20, 255, 255, 255),
            [Math]::Max(1.0, 0.55 * $scale)
        )
        for ($x = -120.0; $x -lt 430.0; $x += 28.0) {
            $graphics.DrawLine(
                $stripePen,
                [float](($x - 20.0) * $scale),
                180.0 * $scale,
                [float](($x + 75.0) * $scale),
                0.0
            )
        }
        $stripePen.Dispose()

        $logoBounds = [System.Drawing.RectangleF]::new(
            23.0 * $scale,
            42.0 * $scale,
            96.0 * $scale,
            96.0 * $scale
        )
        Draw-PlaybackMark -Graphics $graphics -Bounds $logoBounds

        $fontFamily = [System.Drawing.FontFamily]::new('Segoe UI')
        $titleFont = [System.Drawing.Font]::new(
            $fontFamily,
            30.5 * $scale,
            [System.Drawing.FontStyle]::Bold,
            [System.Drawing.GraphicsUnit]::Pixel
        )
        $titleBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::White)
        $graphics.DrawString('KINOGO', $titleFont, $titleBrush, 134.0 * $scale, 48.0 * $scale)
        $titleBrush.Dispose()
        $titleFont.Dispose()

        $tvBounds = [System.Drawing.RectangleF]::new(134.0 * $scale, 93.0 * $scale, 55.0 * $scale, 35.0 * $scale)
        $tvPath = New-RoundedRectanglePath -Rectangle $tvBounds -Radius (8.0 * $scale)
        $tvBrush = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#FFD166'))
        $graphics.FillPath($tvBrush, $tvPath)
        $tvBrush.Dispose()

        $tvFont = [System.Drawing.Font]::new(
            $fontFamily,
            23.0 * $scale,
            [System.Drawing.FontStyle]::Bold,
            [System.Drawing.GraphicsUnit]::Pixel
        )
        $tvTextBrush = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#07111F'))
        $tvFormat = [System.Drawing.StringFormat]::new()
        $tvFormat.Alignment = [System.Drawing.StringAlignment]::Center
        $tvFormat.LineAlignment = [System.Drawing.StringAlignment]::Center
        $graphics.DrawString('TV', $tvFont, $tvTextBrush, $tvBounds, $tvFormat)
        $tvFormat.Dispose()
        $tvTextBrush.Dispose()
        $tvFont.Dispose()
        $tvPath.Dispose()

        $taglineFont = [System.Drawing.Font]::new(
            $fontFamily,
            8.5 * $scale,
            [System.Drawing.FontStyle]::Bold,
            [System.Drawing.GraphicsUnit]::Pixel
        )
        $taglineBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(210, 217, 230, 244))
        $graphics.DrawString('КИНОТЕАТР НА БОЛЬШОМ ЭКРАНЕ', $taglineFont, $taglineBrush, 134.0 * $scale, 138.0 * $scale)
        $taglineBrush.Dispose()
        $taglineFont.Dispose()
        $fontFamily.Dispose()

        $accentBounds = [System.Drawing.RectangleF]::new(0.0, 174.0 * $scale, [float]$Width, 6.0 * $scale)
        $accent = [System.Drawing.Drawing2D.LinearGradientBrush]::new(
            $accentBounds,
            [System.Drawing.ColorTranslator]::FromHtml('#FFD166'),
            [System.Drawing.ColorTranslator]::FromHtml('#0EA5E9'),
            0.0
        )
        $graphics.FillRectangle($accent, $accentBounds)
        $accent.Dispose()

        Save-Png -Bitmap $bitmap -Path $OutputPath
    }
    finally {
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

function New-LegacyLauncherIcon {
    param(
        [int]$Size,
        [string]$OutputPath
    )

    $scale = $Size / 96.0
    $bitmap = New-Canvas -Width $Size -Height $Size
    $graphics = Initialize-Graphics -Bitmap $bitmap

    try {
        $graphics.Clear([System.Drawing.Color]::Transparent)

        # Legacy launchers expect the artwork itself to provide its silhouette.
        # Keep the outer pixels transparent and all important details inside the
        # safe inset so OEM launchers can apply focus rings without clipping.
        $tileBounds = [System.Drawing.RectangleF]::new(
            4.0 * $scale,
            4.0 * $scale,
            88.0 * $scale,
            88.0 * $scale
        )
        $tilePath = New-RoundedRectanglePath -Rectangle $tileBounds -Radius (22.0 * $scale)
        $background = [System.Drawing.Drawing2D.LinearGradientBrush]::new(
            $tileBounds,
            [System.Drawing.ColorTranslator]::FromHtml('#07111F'),
            [System.Drawing.ColorTranslator]::FromHtml('#183452'),
            38.0
        )
        $graphics.FillPath($background, $tilePath)
        $background.Dispose()

        $graphics.SetClip($tilePath)
        $glow = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(55, 14, 165, 233))
        $graphics.FillEllipse($glow, 36.0 * $scale, 34.0 * $scale, 90.0 * $scale, 90.0 * $scale)
        $glow.Dispose()
        $graphics.ResetClip()

        $outline = [System.Drawing.Pen]::new(
            [System.Drawing.Color]::FromArgb(68, 255, 255, 255),
            [Math]::Max(1.0, 1.0 * $scale)
        )
        $graphics.DrawPath($outline, $tilePath)
        $outline.Dispose()
        $tilePath.Dispose()

        $logoBounds = [System.Drawing.RectangleF]::new(14.0 * $scale, 14.0 * $scale, 68.0 * $scale, 68.0 * $scale)
        Draw-PlaybackMark -Graphics $graphics -Bounds $logoBounds

        Save-Png -Bitmap $bitmap -Path $OutputPath
    }
    finally {
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

$resourceRoot = Join-Path $ProjectRoot 'app\src\main\res'

$bannerSizes = [ordered]@{
    mdpi    = @(160, 90)
    hdpi    = @(240, 135)
    xhdpi   = @(320, 180)
    xxhdpi  = @(480, 270)
    xxxhdpi = @(640, 360)
}

foreach ($density in $bannerSizes.Keys) {
    $dimensions = $bannerSizes[$density]
    $output = Join-Path $resourceRoot "mipmap-$density\tv_banner.png"
    New-TvBanner -Width $dimensions[0] -Height $dimensions[1] -OutputPath $output
}

$iconSizes = [ordered]@{
    mdpi    = 48
    hdpi    = 72
    xhdpi   = 96
    xxhdpi  = 144
    xxxhdpi = 192
}

foreach ($density in $iconSizes.Keys) {
    $output = Join-Path $resourceRoot "mipmap-$density\ic_launcher.png"
    New-LegacyLauncherIcon -Size $iconSizes[$density] -OutputPath $output
}

Write-Host "Generated Android TV banner and launcher icons under $resourceRoot"
