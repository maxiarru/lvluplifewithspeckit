[CmdletBinding()]
param(
    [Parameter(Mandatory)][uri]$ApiBaseUrl,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [string]$SigningDirectory = (Join-Path ([Environment]::GetFolderPath('UserProfile')) '.lvlup-life-signing'),
    [string]$AndroidSdkPath = (Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'Android/Sdk'),
    [string]$JdkPath
)

$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (-not $ApiBaseUrl.IsAbsoluteUri -or $ApiBaseUrl.Scheme -ne 'https' -or $ApiBaseUrl.UserInfo -or
    $ApiBaseUrl.Query -or $ApiBaseUrl.Fragment -or $ApiBaseUrl.AbsolutePath -ne '/' -or
    $ApiBaseUrl.Host.TrimEnd('.') -in @('localhost', '127.0.0.1', '10.0.2.2', '::1')) {
    throw 'La API debe ser un origen HTTPS, sin rutas ni credenciales.'
}
$outputRoot = [IO.Path]::GetFullPath($OutputDirectory)
if ($outputRoot.Equals($repoRoot, [StringComparison]::OrdinalIgnoreCase) -or
    $outputRoot.StartsWith($repoRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'El APK de entrega debe quedar fuera del repositorio.'
}
$signingRoot = [IO.Path]::GetFullPath($SigningDirectory)
if ($signingRoot.Equals($outputRoot, [StringComparison]::OrdinalIgnoreCase) -or
    $signingRoot.StartsWith($outputRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
    $outputRoot.StartsWith($signingRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'La carpeta privada de firma debe estar separada de la carpeta de entrega.'
}
if (-not $JdkPath) { $JdkPath = Join-Path $repoRoot '.tooling/jdk-17.0.21+9' }
$signing = & (Join-Path $PSScriptRoot 'initialize-android-release-signing.ps1') -SigningDirectory $SigningDirectory -JdkPath $JdkPath
$buildTools = Get-ChildItem -LiteralPath (Join-Path $AndroidSdkPath 'build-tools') -Directory |
    Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } | Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
if (-not $buildTools) { throw 'Faltan Android build-tools.' }
$apiOrigin = $ApiBaseUrl.GetLeftPart([UriPartial]::Authority)
$previousJava = $env:JAVA_HOME
$previousSdk = $env:ANDROID_HOME
$previousGradle = $env:GRADLE_USER_HOME
$previousPassword = [Environment]::GetEnvironmentVariable('LVLUP_RELEASE_SIGNING_PASSWORD', 'Process')
$pointer = [IntPtr]::Zero
try {
    $env:JAVA_HOME = $JdkPath
    $env:ANDROID_HOME = $AndroidSdkPath
    $env:GRADLE_USER_HOME = Join-Path $repoRoot '.tooling/gradle-user-home'
    Push-Location $repoRoot
    try {
        & (Join-Path $repoRoot 'gradlew.bat') ':app:assembleRelease' "-Plvlup.apiBaseUrl=$apiOrigin" '--no-daemon' '--console=plain'
        if ($LASTEXITCODE -ne 0) { throw 'Fallo la compilacion release.' }
    } finally { Pop-Location }

    $releaseRoot = Join-Path $repoRoot 'app/build/outputs/apk/release'
    $metadata = Get-Content -LiteralPath (Join-Path $releaseRoot 'output-metadata.json') -Raw | ConvertFrom-Json
    if ($metadata.elements.Count -ne 1) { throw 'Se esperaba un unico APK universal.' }
    $unsigned = Join-Path $releaseRoot $metadata.elements[0].outputFile
    $buildConfig = Get-Content -LiteralPath (Join-Path $repoRoot 'app/build/generated/source/buildConfig/release/lvluplife/BuildConfig.java') -Raw
    if ($buildConfig -notmatch 'DEBUG = false' -or -not $buildConfig.Contains('"' + $apiOrigin + '"')) {
        throw 'El release no contiene la URL o el modo esperado.'
    }
    $workPath = Join-Path $repoRoot ('.tooling/release/' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $workPath -Force | Out-Null
    $aligned = Join-Path $workPath 'aligned.apk'
    $signed = Join-Path $workPath 'signed.apk'
    & (Join-Path $buildTools.FullName 'zipalign.exe') -P 16 -f 4 $unsigned $aligned
    if ($LASTEXITCODE -ne 0) { throw 'Fallo la alineacion del APK.' }
    $secure = (Get-Content -LiteralPath $signing.PasswordFile -Raw).Trim() | ConvertTo-SecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    $env:LVLUP_RELEASE_SIGNING_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    & (Join-Path $buildTools.FullName 'apksigner.bat') sign --ks $signing.StoreFile --ks-key-alias $signing.Alias `
        --ks-pass env:LVLUP_RELEASE_SIGNING_PASSWORD --key-pass env:LVLUP_RELEASE_SIGNING_PASSWORD --out $signed $aligned
    if ($LASTEXITCODE -ne 0) { throw 'Fallo la firma del APK.' }
    & (Join-Path $buildTools.FullName 'apksigner.bat') verify --verbose --print-certs $signed
    if ($LASTEXITCODE -ne 0) { throw 'La firma del APK no verifica.' }
    & (Join-Path $buildTools.FullName 'zipalign.exe') -c -P 16 4 $signed
    if ($LASTEXITCODE -ne 0) { throw 'La alineacion final no verifica.' }
    $badging = & (Join-Path $buildTools.FullName 'aapt.exe') dump badging $signed
    $badgingText = $badging -join "`n"
    if ($LASTEXITCODE -ne 0 -or $badgingText -notmatch "sdkVersion:'29'" -or $badgingText -match 'application-debuggable') {
        throw 'El manifiesto no cumple el minimo Android 10 o no es release.'
    }
    New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null
    $fileName = 'LvlUpLife-' + $metadata.elements[0].versionName + '-release-' + (Get-Date -Format 'yyyy-MM-dd') + '.apk'
    $outputApk = Join-Path $outputRoot $fileName
    Copy-Item -LiteralPath $signed -Destination $outputApk
    $hash = (Get-FileHash -LiteralPath $outputApk -Algorithm SHA256).Hash
    if ($hash -ne (Get-FileHash -LiteralPath $signed -Algorithm SHA256).Hash) { throw 'La copia final no coincide.' }
    "$hash  $fileName" | Set-Content -LiteralPath (Join-Path $outputRoot 'SHA256-release.txt') -Encoding ascii
    $result = [pscustomobject]@{
        Apk = $outputApk; ApplicationId = $metadata.applicationId; Version = $metadata.elements[0].versionName
        ApiBaseUrl = $apiOrigin; Sha256 = $hash; Bytes = (Get-Item -LiteralPath $outputApk).Length
        CertificateSha1 = $signing.CertificateSha1; CertificateSha256 = $signing.CertificateSha256
    }
    $result | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $outputRoot 'release-metadata.json') -Encoding utf8
    $result
} finally {
    if ($pointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
    foreach ($setting in @(@('JAVA_HOME', $previousJava), @('ANDROID_HOME', $previousSdk), @('GRADLE_USER_HOME', $previousGradle), @('LVLUP_RELEASE_SIGNING_PASSWORD', $previousPassword))) {
        [Environment]::SetEnvironmentVariable($setting[0], $setting[1], 'Process')
    }
}
