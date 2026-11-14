[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$trialRoot = Join-Path $repoRoot '.tooling/vercel-trial'
$uploadPath = Join-Path $trialRoot ([DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff'))
if (Test-Path -LiteralPath $uploadPath) { throw 'El directorio de esta prueba ya existe.' }
New-Item -ItemType Directory -Path $uploadPath -Force | Out-Null

function Copy-TrialFile([string]$SourceRelativePath, [string]$TargetRelativePath) {
    $source = Join-Path $repoRoot $SourceRelativePath
    $target = [IO.Path]::GetFullPath((Join-Path $uploadPath $TargetRelativePath))
    if (-not $target.StartsWith($uploadPath + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'El destino debe estar dentro del paquete de prueba.'
    }
    $item = Get-Item -LiteralPath $source
    if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) {
        throw 'No se permiten enlaces en el paquete de prueba.'
    }
    New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
    Copy-Item -LiteralPath $source -Destination $target
}

foreach ($name in @('settings.gradle.kts', 'build.gradle.kts', 'gradle.properties', 'Dockerfile', '.dockerignore', '.vercelignore')) {
    Copy-TrialFile "deployment/vercel/$name" $name
}
foreach ($name in @('gradlew', 'gradlew.bat', 'gradle/wrapper/gradle-wrapper.jar', 'gradle/wrapper/gradle-wrapper.properties', 'gradle/libs.versions.toml')) {
    Copy-TrialFile $name $name
}
foreach ($module in @('domain', 'server')) {
    foreach ($name in @('build.gradle.kts', 'gradle.lockfile')) {
        Copy-TrialFile "$module/$name" "$module/$name"
    }
    # Only Kotlin sources are included; no local JSON, ADC, keys or build outputs.
    $sourceRoot = Join-Path $repoRoot "$module/src"
    $directories = @(Get-Item -LiteralPath $sourceRoot) + @(Get-ChildItem -LiteralPath $sourceRoot -Directory -Recurse)
    if ($directories | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }) {
        throw 'No se permiten directorios enlazados en las fuentes.'
    }
    Get-ChildItem -LiteralPath $sourceRoot -Recurse -File -Filter '*.kt' | ForEach-Object {
        $relative = [IO.Path]::GetRelativePath($repoRoot, $_.FullName)
        Copy-TrialFile $relative $relative
    }
}

# Linux must be able to execute the wrapper even from a Windows checkout.
foreach ($name in @('gradlew')) {
    $scriptPath = Join-Path $uploadPath $name
    $script = [IO.File]::ReadAllText($scriptPath).Replace("`r`n", "`n")
    [IO.File]::WriteAllText($scriptPath, $script, [Text.UTF8Encoding]::new($false))
}

$manifest = Get-ChildItem -LiteralPath $uploadPath -Recurse -File -Force | Sort-Object FullName | ForEach-Object {
    $relative = [IO.Path]::GetRelativePath($uploadPath, $_.FullName).Replace('\', '/')
    $hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $relative"
}
[IO.File]::WriteAllLines((Join-Path $uploadPath 'SHA256SUMS'), $manifest, [Text.UTF8Encoding]::new($false))
Write-Output $uploadPath
