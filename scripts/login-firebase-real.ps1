# Ejecutar de forma interactiva; no mostrar ni copiar codigos OAuth al chat.
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$gcloudPath = Join-Path $repoRoot '.tooling/gcloud-584/google-cloud-sdk/bin/gcloud.cmd'
if (-not (Test-Path -LiteralPath $gcloudPath)) {
    throw 'Google Cloud CLI local no disponible; consultar firebase-real.md.'
}

$previousConfig = $env:CLOUDSDK_CONFIG
$previousUsage = $env:CLOUDSDK_CORE_DISABLE_USAGE_REPORTING
try {
    $env:CLOUDSDK_CONFIG = Join-Path $repoRoot '.tooling/gcloud-config'
    $env:CLOUDSDK_CORE_DISABLE_USAGE_REPORTING = 'true'
    & $gcloudPath auth login --project=liveluplife
    if ($LASTEXITCODE -ne 0) { throw 'No se completo el inicio de sesion de Google Cloud CLI.' }
    Write-Host 'Sesion CLI preparada. ADC y permisos del servidor requieren la etapa siguiente.'
} finally {
    $env:CLOUDSDK_CONFIG = $previousConfig
    $env:CLOUDSDK_CORE_DISABLE_USAGE_REPORTING = $previousUsage
}
