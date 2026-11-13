[CmdletBinding()]
param([ValidateRange(1024,65535)][int]$Port = 8081)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$adcPath = Join-Path $repoRoot '.tooling/gcloud-config/application_default_credentials.json'
$serverPath = Join-Path $repoRoot 'server/build/install/server/lib'
if (-not (Test-Path -LiteralPath $adcPath)) { throw 'Falta ADC local; consultar firebase-real.md.' }
if (-not (Test-Path -LiteralPath $serverPath)) { throw 'Ejecutar primero gradlew.bat :server:installDist.' }
$adc = Get-Content -Raw -LiteralPath $adcPath | ConvertFrom-Json
if ($adc.type -ne 'impersonated_service_account' -or
    $adc.service_account_impersonation_url -notlike 'https://iamcredentials.googleapis.com/v1/projects/-/serviceAccounts/lvlup-server-test@liveluplife.iam.gserviceaccount.com:generateAccessToken') {
    throw 'ADC no corresponde a la cuenta de servidor acordada.'
}
$settings = @{
    GOOGLE_APPLICATION_CREDENTIALS = $adcPath
    GOOGLE_CLOUD_PROJECT = 'liveluplife'
    GCLOUD_PROJECT = 'liveluplife'
    LVLUP_ENV = 'production'
    LVLUP_BIND_HOST = '127.0.0.1'
    PORT = "$Port"
    FIREBASE_AUTH_EMULATOR_HOST = $null
    FIRESTORE_EMULATOR_HOST = $null
}
if (Test-Path (Join-Path $repoRoot '.tooling/jdk-17.0.21+9')) {
    $settings.JAVA_HOME = Join-Path $repoRoot '.tooling/jdk-17.0.21+9'
}
$previous = @{}
try {
    foreach ($name in $settings.Keys) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        if ($null -eq $settings[$name]) {
            Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable($name, $settings[$name], 'Process')
        }
    }
    Write-Host "Firebase real liveluplife; API local http://127.0.0.1:$Port; sin despliegue remoto."
    $javaPath = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
    # Java expands this wildcard; avoid the generated BAT's Windows command length limit.
    & $javaPath -cp (Join-Path $serverPath '*') lvluplife.api.ServerKt
    if ($LASTEXITCODE -ne 0) { throw 'El servidor finalizo con error.' }
} finally {
    foreach ($name in $previous.Keys) {
        if ($null -eq $previous[$name]) {
            Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
        }
    }
}
