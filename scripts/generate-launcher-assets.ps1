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

function Save-Png {
    param(
        [System.Drawing.Bitmap]$Bitmap,
        [string]$Path
    )

    $directory = Split-Path -Parent $Path
    [System.IO.Directory]::CreateDirectory($directory) | Out-Null
    $Bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
}

function Draw-RoundedImage {
    param(
        [System.Drawing.Graphics]$Graphics,
        [System.Drawing.Image]$Image,
        [System.Drawing.RectangleF]$Bounds,
        [float]$Radius
    )

    $path = New-RoundedRectanglePath -Rectangle $Bounds -Radius $Radius
    $state = $Graphics.Save()
    try {
        $Graphics.SetClip($path)
        $Graphics.DrawImage($Image, $Bounds)
    }
    finally {
        $Graphics.Restore($state)
        $path.Dispose()
    }
}

function Draw-AtvBadge {
    param(
        [System.Drawing.Graphics]$Graphics,
        [System.Drawing.RectangleF]$Bounds,
        [float]$Scale
    )

    $shadowBounds = [System.Drawing.RectangleF]::new(
        $Bounds.X + (1.4 * $Scale),
        $Bounds.Y + (2.0 * $Scale),
        $Bounds.Width,
        $Bounds.Height
    )
    $shadowPath = New-RoundedRectanglePath -Rectangle $shadowBounds -Radius (5.5 * $Scale)
    $shadowBrush = [System.Drawing.SolidBrush]::new(
        [System.Drawing.Color]::FromArgb(120, 0, 0, 0)
    )
    $Graphics.FillPath($shadowBrush, $shadowPath)
    $shadowBrush.Dispose()
    $shadowPath.Dispose()

    $path = New-RoundedRectanglePath -Rectangle $Bounds -Radius (5.5 * $Scale)
    $background = [System.Drawing.Drawing2D.LinearGradientBrush]::new(
        $Bounds,
        [System.Drawing.ColorTranslator]::FromHtml('#52E4EC'),
        [System.Drawing.ColorTranslator]::FromHtml('#168EC0'),
        90.0
    )
    $Graphics.FillPath($background, $path)
    $background.Dispose()

    $outline = [System.Drawing.Pen]::new(
        [System.Drawing.Color]::FromArgb(210, 226, 252, 255),
        [Math]::Max(1.0, 0.75 * $Scale)
    )
    $Graphics.DrawPath($outline, $path)
    $outline.Dispose()

    $font = [System.Drawing.Font]::new(
        'Segoe UI',
        10.5 * $Scale,
        [System.Drawing.FontStyle]::Bold,
        [System.Drawing.GraphicsUnit]::Pixel
    )
    $brush = [System.Drawing.SolidBrush]::new(
        [System.Drawing.ColorTranslator]::FromHtml('#061014')
    )
    $format = [System.Drawing.StringFormat]::new()
    $format.Alignment = [System.Drawing.StringAlignment]::Center
    $format.LineAlignment = [System.Drawing.StringAlignment]::Center
    $format.FormatFlags = [System.Drawing.StringFormatFlags]::NoWrap
    $Graphics.DrawString('ATV', $font, $brush, $Bounds, $format)
    $format.Dispose()
    $brush.Dispose()
    $font.Dispose()
    $path.Dispose()
}

function New-TvBanner {
    param(
        [int]$Width,
        [int]$Height,
        [System.Drawing.Image]$OfficialIcon,
        [string]$OutputPath
    )

    $scale = $Width / 320.0
    $bitmap = New-Canvas -Width $Width -Height $Height
    $graphics = Initialize-Graphics -Bitmap $bitmap

    try {
        $canvas = [System.Drawing.RectangleF]::new(0.0, 0.0, [float]$Width, [float]$Height)
        $background = [System.Drawing.Drawing2D.LinearGradientBrush]::new(
            $canvas,
            [System.Drawing.ColorTranslator]::FromHtml('#050608'),
            [System.Drawing.ColorTranslator]::FromHtml('#171E24'),
            12.0
        )
        $graphics.FillRectangle($background, $canvas)
        $background.Dispose()

        $steelGlow = [System.Drawing.SolidBrush]::new(
            [System.Drawing.Color]::FromArgb(44, 63, 118, 133)
        )
        $graphics.FillEllipse(
            $steelGlow,
            185.0 * $scale,
            -92.0 * $scale,
            235.0 * $scale,
            260.0 * $scale
        )
        $steelGlow.Dispose()

        $iconBounds = [System.Drawing.RectangleF]::new(
            19.0 * $scale,
            31.0 * $scale,
            118.0 * $scale,
            118.0 * $scale
        )
        $iconShadow = [System.Drawing.RectangleF]::new(
            $iconBounds.X + (3.0 * $scale),
            $iconBounds.Y + (5.0 * $scale),
            $iconBounds.Width,
            $iconBounds.Height
        )
        $shadowPath = New-RoundedRectanglePath -Rectangle $iconShadow -Radius (22.0 * $scale)
        $shadow = [System.Drawing.SolidBrush]::new(
            [System.Drawing.Color]::FromArgb(145, 0, 0, 0)
        )
        $graphics.FillPath($shadow, $shadowPath)
        $shadow.Dispose()
        $shadowPath.Dispose()

        Draw-RoundedImage `
            -Graphics $graphics `
            -Image $OfficialIcon `
            -Bounds $iconBounds `
            -Radius (22.0 * $scale)

        $iconPath = New-RoundedRectanglePath -Rectangle $iconBounds -Radius (22.0 * $scale)
        $iconOutline = [System.Drawing.Pen]::new(
            [System.Drawing.Color]::FromArgb(150, 132, 178, 188),
            [Math]::Max(1.0, 1.0 * $scale)
        )
        $graphics.DrawPath($iconOutline, $iconPath)
        $iconOutline.Dispose()
        $iconPath.Dispose()

        $fontFamily = [System.Drawing.FontFamily]::new('Segoe UI')
        $titleFont = [System.Drawing.Font]::new(
            $fontFamily,
            31.0 * $scale,
            [System.Drawing.FontStyle]::Bold,
            [System.Drawing.GraphicsUnit]::Pixel
        )
        $titleBrush = [System.Drawing.SolidBrush]::new(
            [System.Drawing.ColorTranslator]::FromHtml('#F5F9FA')
        )
        $graphics.DrawString(
            'KINOGO',
            $titleFont,
            $titleBrush,
            151.0 * $scale,
            52.0 * $scale
        )
        $titleBrush.Dispose()
        $titleFont.Dispose()

        $subtitleFont = [System.Drawing.Font]::new(
            $fontFamily,
            14.5 * $scale,
            [System.Drawing.FontStyle]::Regular,
            [System.Drawing.GraphicsUnit]::Pixel
        )
        $subtitleBrush = [System.Drawing.SolidBrush]::new(
            [System.Drawing.ColorTranslator]::FromHtml('#6EDCE8')
        )
        $graphics.DrawString(
            'for Android TV',
            $subtitleFont,
            $subtitleBrush,
            153.0 * $scale,
            101.0 * $scale
        )
        $subtitleBrush.Dispose()
        $subtitleFont.Dispose()
        $fontFamily.Dispose()

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
        [System.Drawing.Image]$OfficialIcon,
        [string]$OutputPath
    )

    $scale = $Size / 96.0
    $bitmap = New-Canvas -Width $Size -Height $Size
    $graphics = Initialize-Graphics -Bitmap $bitmap

    try {
        $graphics.Clear([System.Drawing.Color]::Transparent)
        $tileBounds = [System.Drawing.RectangleF]::new(
            4.0 * $scale,
            4.0 * $scale,
            88.0 * $scale,
            88.0 * $scale
        )
        Draw-RoundedImage `
            -Graphics $graphics `
            -Image $OfficialIcon `
            -Bounds $tileBounds `
            -Radius (20.0 * $scale)

        $tilePath = New-RoundedRectanglePath -Rectangle $tileBounds -Radius (20.0 * $scale)
        $outline = [System.Drawing.Pen]::new(
            [System.Drawing.Color]::FromArgb(160, 126, 198, 211),
            [Math]::Max(1.0, 0.9 * $scale)
        )
        $graphics.DrawPath($outline, $tilePath)
        $outline.Dispose()
        $tilePath.Dispose()

        $badgeBounds = [System.Drawing.RectangleF]::new(
            55.0 * $scale,
            64.0 * $scale,
            31.0 * $scale,
            18.0 * $scale
        )
        Draw-AtvBadge -Graphics $graphics -Bounds $badgeBounds -Scale $scale
        Save-Png -Bitmap $bitmap -Path $OutputPath
    }
    finally {
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

function New-AdaptiveForeground {
    param(
        [int]$Size,
        [System.Drawing.Image]$OfficialIcon,
        [string]$OutputPath
    )

    $scale = $Size / 108.0
    $bitmap = New-Canvas -Width $Size -Height $Size
    $graphics = Initialize-Graphics -Bitmap $bitmap

    try {
        $graphics.Clear([System.Drawing.Color]::Transparent)
        $tileBounds = [System.Drawing.RectangleF]::new(
            15.0 * $scale,
            15.0 * $scale,
            78.0 * $scale,
            78.0 * $scale
        )
        Draw-RoundedImage `
            -Graphics $graphics `
            -Image $OfficialIcon `
            -Bounds $tileBounds `
            -Radius (17.0 * $scale)

        $badgeBounds = [System.Drawing.RectangleF]::new(
            59.0 * $scale,
            66.0 * $scale,
            28.0 * $scale,
            16.5 * $scale
        )
        Draw-AtvBadge -Graphics $graphics -Bounds $badgeBounds -Scale $scale
        Save-Png -Bitmap $bitmap -Path $OutputPath
    }
    finally {
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

$resourceRoot = Join-Path $ProjectRoot 'app\src\main\res'
$officialIconPath = Join-Path $resourceRoot 'drawable-nodpi\ic_kinogo_original.png'
if (-not (Test-Path -LiteralPath $officialIconPath)) {
    throw "Original Kinogo icon is missing: $officialIconPath"
}

$officialIcon = [System.Drawing.Image]::FromFile($officialIconPath)
try {
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
        New-TvBanner `
            -Width $dimensions[0] `
            -Height $dimensions[1] `
            -OfficialIcon $officialIcon `
            -OutputPath $output
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
        New-LegacyLauncherIcon `
            -Size $iconSizes[$density] `
            -OfficialIcon $officialIcon `
            -OutputPath $output
    }

    $foregroundSizes = [ordered]@{
        mdpi    = 108
        hdpi    = 162
        xhdpi   = 216
        xxhdpi  = 324
        xxxhdpi = 432
    }
    foreach ($density in $foregroundSizes.Keys) {
        $output = Join-Path $resourceRoot "drawable-$density\ic_launcher_foreground_atv.png"
        New-AdaptiveForeground `
            -Size $foregroundSizes[$density] `
            -OfficialIcon $officialIcon `
            -OutputPath $output
    }
}
finally {
    $officialIcon.Dispose()
}

Write-Host "Generated Kinogo Android TV banner and launcher icons under $resourceRoot"
