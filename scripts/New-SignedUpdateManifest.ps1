<#
.SYNOPSIS
Creates KinogoATV's signer-authenticated update manifest from an already signed APK.

.EXAMPLE
$expires = [DateTimeOffset]::UtcNow.AddDays(30)
.\scripts\New-SignedUpdateManifest.ps1 `
  -ApkPath .\dist\KinogoATV-0.5.1-code15.apk `
  -VersionName 0.5.1 -VersionCode 15 -ExpiresAt $expires `
  -DownloadUrl @(
    'https://reziarlleh.github.io/KinogoATV/update/KinogoATV-0.5.1-code15.apk',
    'https://github.com/reziarlleh/KinogoATV/releases/download/v0.5.1/KinogoATV-0.5.1-code15.apk'
  )

.NOTES
The keystore remains local. Passwords are read from KINOGO_SIGNING_STORE_PASSWORD and
KINOGO_SIGNING_KEY_PASSWORD, or requested as SecureString input. They are never command-line
arguments and are removed from the child-process environment after Java exits.
#>
[CmdletBinding(DefaultParameterSetName = 'Sign')]
param(
    [Parameter(Mandatory = $true, ParameterSetName = 'SelfTest')]
    [switch]$SelfTest,

    [Parameter(Mandatory = $true, ParameterSetName = 'Sign')]
    [string]$ApkPath,

    [Parameter(Mandatory = $true, ParameterSetName = 'Sign')]
    [ValidatePattern('^\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?$')]
    [string]$VersionName,

    [Parameter(Mandatory = $true, ParameterSetName = 'Sign')]
    [ValidateRange(1, [long]::MaxValue)]
    [long]$VersionCode,

    [Parameter(Mandatory = $true, ParameterSetName = 'Sign')]
    [ValidateCount(1, 4)]
    [string[]]$DownloadUrl,

    [Parameter(Mandatory = $true, ParameterSetName = 'Sign')]
    [DateTimeOffset]$ExpiresAt,

    [Parameter(ParameterSetName = 'Sign')]
    [Nullable[DateTimeOffset]]$IssuedAt,

    [Parameter(ParameterSetName = 'Sign')]
    [string]$KeyStorePath,

    [Parameter(ParameterSetName = 'Sign')]
    [string]$KeyAlias,

    [Parameter(ParameterSetName = 'Sign')]
    [string]$OutputPath = 'update/manifest.json',

    [Parameter(ParameterSetName = 'Sign')]
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptRoot
$javaSource = Join-Path $scriptRoot 'UpdateManifestSigner.java'

function Find-Java17 {
    $candidates = [System.Collections.Generic.List[string]]::new()
    if ($env:JAVA_HOME) {
        $candidates.Add((Join-Path $env:JAVA_HOME 'bin\java.exe'))
        $candidates.Add((Join-Path $env:JAVA_HOME 'bin/java'))
    }
    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCommand) {
        $candidates.Add($javaCommand.Source)
    }
    $bundledRoot = Join-Path $repoRoot '.tools\jdk-17'
    if (Test-Path -LiteralPath $bundledRoot) {
        Get-ChildItem -LiteralPath $bundledRoot -Recurse -File -Filter java.exe |
            ForEach-Object { $candidates.Add($_.FullName) }
    }
    foreach ($candidate in $candidates | Select-Object -Unique) {
        if (Test-Path -LiteralPath $candidate) {
            $versionOutput = (& $candidate -version 2>&1 | Out-String)
            if ($LASTEXITCODE -eq 0 -and $versionOutput -match 'version "(?<major>\d+)') {
                if ([int]$Matches.major -ge 17) {
                    return $candidate
                }
            }
        }
    }
    throw 'JDK 17 or newer was not found. Set JAVA_HOME or restore .tools/jdk-17.'
}

function ConvertTo-PlainText {
    param([Parameter(Mandatory = $true)][SecureString]$Value)
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Value)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

function Resolve-RequiredFile {
    param([Parameter(Mandatory = $true)][string]$Path, [string]$Label)
    $resolved = Resolve-Path -LiteralPath $Path -ErrorAction Stop
    if (-not (Test-Path -LiteralPath $resolved.Path -PathType Leaf)) {
        throw "$Label is not a file."
    }
    return $resolved.Path
}

function Find-AndroidTool {
    param([Parameter(Mandatory = $true)][string[]]$Name)
    foreach ($candidateName in $Name) {
        $command = Get-Command $candidateName -ErrorAction SilentlyContinue
        if ($command) {
            return $command.Source
        }
    }
    $sdkRoots = [System.Collections.Generic.List[string]]::new()
    foreach ($root in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $repoRoot '.tools\android-sdk'))) {
        if ($root -and (Test-Path -LiteralPath $root)) {
            $sdkRoots.Add($root)
        }
    }
    foreach ($root in $sdkRoots | Select-Object -Unique) {
        foreach ($candidateName in $Name) {
            $found = Get-ChildItem -LiteralPath $root -Recurse -File -Filter $candidateName |
                Sort-Object FullName -Descending |
                Select-Object -First 1
            if ($found) {
                return $found.FullName
            }
        }
    }
    throw "Required Android SDK tool was not found: $($Name -join ', ')."
}

$java = Find-Java17
if ($SelfTest) {
    & $java $javaSource self-test
    if ($LASTEXITCODE -ne 0) {
        throw "Update manifest signer self-test failed with exit code $LASTEXITCODE."
    }
    exit 0
}

Push-Location $repoRoot
try {
    if (-not $KeyStorePath) {
        $KeyStorePath = if ($env:KINOGO_SIGNING_STORE_FILE) {
            $env:KINOGO_SIGNING_STORE_FILE
        } else {
            '.signing/kinogo-tv-dev.keystore'
        }
    }
    if (-not $KeyAlias) {
        $KeyAlias = if ($env:KINOGO_SIGNING_KEY_ALIAS) {
            $env:KINOGO_SIGNING_KEY_ALIAS
        } else {
            'androiddebugkey'
        }
    }

    $resolvedApk = Resolve-RequiredFile -Path $ApkPath -Label 'APK'
    $resolvedKeyStore = Resolve-RequiredFile -Path $KeyStorePath -Label 'Signing keystore'
    $resolvedOutput = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath(
        $OutputPath
    )
    $issuedAtValue = if ($IssuedAt.HasValue) {
        $IssuedAt.Value.ToUnixTimeSeconds()
    } else {
        [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    }
    $expiresAtValue = $ExpiresAt.ToUnixTimeSeconds()
    if ($expiresAtValue -le $issuedAtValue) {
        throw 'ExpiresAt must be later than IssuedAt.'
    }
    if (($expiresAtValue - $issuedAtValue) -gt (90 * 24 * 60 * 60)) {
        throw 'Manifest lifetime must not exceed 90 days.'
    }

    $previousStorePassword = $env:KINOGO_SIGNING_STORE_PASSWORD
    $previousKeyPassword = $env:KINOGO_SIGNING_KEY_PASSWORD
    $storePasswordValue = $previousStorePassword
    $keyPasswordValue = $previousKeyPassword
    try {
        if (-not $storePasswordValue) {
            $storePasswordValue = ConvertTo-PlainText (
                Read-Host 'Signing keystore password' -AsSecureString
            )
        }
        if (-not $keyPasswordValue) {
            $keyPasswordValue = $storePasswordValue
        }

        # Android SDK tools do not need keystore passwords. Keep the secrets out of their
        # child-process environments even when the caller supplied them as environment values.
        $env:KINOGO_SIGNING_STORE_PASSWORD = $null
        $env:KINOGO_SIGNING_KEY_PASSWORD = $null

        $apkSigner = Find-AndroidTool -Name @('apksigner.bat', 'apksigner')
        $apkSignerReport = (& $apkSigner verify --verbose --print-certs $resolvedApk 2>&1 | Out-String)
        if ($LASTEXITCODE -ne 0) {
            throw 'APK signature verification failed.'
        }
        if ($apkSignerReport -notmatch 'Number of signers:\s*1(?:\r?\n|$)') {
            throw 'The release APK must have exactly one signer.'
        }
        if ($apkSignerReport -notmatch 'Verified using v2 scheme \(APK Signature Scheme v2\):\s*true') {
            throw 'The release APK must have a valid APK Signature Scheme v2 signature.'
        }
        if ($apkSignerReport -notmatch 'Signer #1 certificate SHA-256 digest:\s*(?<digest>[0-9a-fA-F]{64})') {
            throw 'APK signing certificate digest was not reported.'
        }
        $apkCertificateDigest = $Matches.digest.ToLowerInvariant()

        $zipAlign = Find-AndroidTool -Name @('zipalign.exe', 'zipalign')
        & $zipAlign -c -v 4 $resolvedApk *> $null
        if ($LASTEXITCODE -ne 0) {
            throw 'APK zip alignment verification failed.'
        }

        $apkAnalyzer = Find-AndroidTool -Name @('apkanalyzer.bat', 'apkanalyzer')
        $applicationId = (& $apkAnalyzer manifest application-id $resolvedApk 2>&1 | Out-String).Trim()
        if ($LASTEXITCODE -ne 0 -or $applicationId -ne 'com.kinogo.atv') {
            throw 'APK application ID is not com.kinogo.atv.'
        }
        $apkVersionName = (& $apkAnalyzer manifest version-name $resolvedApk 2>&1 | Out-String).Trim()
        if ($LASTEXITCODE -ne 0 -or $apkVersionName -ne $VersionName) {
            throw 'APK version name does not match the requested manifest version.'
        }
        $apkVersionCode = (& $apkAnalyzer manifest version-code $resolvedApk 2>&1 | Out-String).Trim()
        if ($LASTEXITCODE -ne 0 -or $apkVersionCode -ne $VersionCode.ToString(
            [Globalization.CultureInfo]::InvariantCulture
        )) {
            throw 'APK version code does not match the requested manifest version.'
        }
        $apkMinSdk = (& $apkAnalyzer manifest min-sdk $resolvedApk 2>&1 | Out-String).Trim()
        if ($LASTEXITCODE -ne 0 -or $apkMinSdk -ne '28') {
            throw 'APK minimum SDK must be Android 9 / API 28.'
        }

        $arguments = [System.Collections.Generic.List[string]]::new()
        @(
            'sign',
            '--apk', $resolvedApk,
            '--keystore', $resolvedKeyStore,
            '--alias', $KeyAlias,
            '--expected-certificate-sha256', $apkCertificateDigest,
            '--version-name', $VersionName,
            '--version-code', $VersionCode.ToString([Globalization.CultureInfo]::InvariantCulture),
            '--issued-at', $issuedAtValue.ToString([Globalization.CultureInfo]::InvariantCulture),
            '--expires-at', $expiresAtValue.ToString([Globalization.CultureInfo]::InvariantCulture),
            '--output', $resolvedOutput
        ) | ForEach-Object { $arguments.Add([string]$_) }
        foreach ($url in $DownloadUrl) {
            $arguments.Add('--download-url')
            $arguments.Add($url)
        }
        if ($DryRun) {
            $arguments.Add('--dry-run')
        }

        # Only the Java signer receives the secrets, and only for the duration of this process.
        $env:KINOGO_SIGNING_STORE_PASSWORD = $storePasswordValue
        $env:KINOGO_SIGNING_KEY_PASSWORD = $keyPasswordValue
        & $java $javaSource @arguments
        if ($LASTEXITCODE -ne 0) {
            throw "Update manifest signing failed with exit code $LASTEXITCODE."
        }
    } finally {
        $env:KINOGO_SIGNING_STORE_PASSWORD = $previousStorePassword
        $env:KINOGO_SIGNING_KEY_PASSWORD = $previousKeyPassword
        $storePasswordValue = $null
        $keyPasswordValue = $null
    }
} finally {
    Pop-Location
}
