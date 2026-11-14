[CmdletBinding()]
param(
    [string]$SigningDirectory = (Join-Path ([Environment]::GetFolderPath('UserProfile')) '.lvlup-life-signing'),
    [string]$JdkPath
)

$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$signingRoot = [IO.Path]::GetFullPath($SigningDirectory)
if ($signingRoot.Equals($repoRoot, [StringComparison]::OrdinalIgnoreCase) -or
    $signingRoot.StartsWith($repoRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'La clave de distribucion debe quedar fuera del repositorio.'
}
if (-not $JdkPath) { $JdkPath = Join-Path $repoRoot '.tooling/jdk-17.0.21+9' }
$keytool = Join-Path $JdkPath 'bin/keytool.exe'
if (-not (Test-Path -LiteralPath $keytool)) { throw 'Falta keytool de JDK 17.' }
if ($signingRoot.Equals([IO.Path]::GetPathRoot($signingRoot), [StringComparison]::OrdinalIgnoreCase)) {
    throw 'La firma requiere una carpeta privada exclusiva, no la raiz de una unidad.'
}
if (Test-Path -LiteralPath $signingRoot) {
    $unexpected = Get-ChildItem -LiteralPath $signingRoot -Force | Where-Object {
        $_.Name -notin @('lvlup-life-release.p12', 'password.dpapi', 'release.cer', 'README.txt')
    }
    if ($unexpected) { throw 'La carpeta elegida contiene archivos ajenos a la firma.' }
}

New-Item -ItemType Directory -Path $signingRoot -Force | Out-Null
if ((Get-Item -LiteralPath $signingRoot).Attributes -band [IO.FileAttributes]::ReparsePoint) {
    throw 'La carpeta de firma no puede ser un enlace.'
}
# A private Windows folder, separate from the APK distribution directory.
$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$acl = [Security.AccessControl.DirectorySecurity]::new()
$acl.SetAccessRuleProtection($true, $false)
$acl.SetOwner($identity.User)
foreach ($sid in @($identity.User, [Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {
    $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new(
        $sid, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow'))
}
$existingAcl = Get-Acl -LiteralPath $signingRoot
$allowedSids = @($identity.User.Value, 'S-1-5-18')
$existingRules = @($existingAcl.GetAccessRules($true, $true, [Security.Principal.SecurityIdentifier]))
$alreadyPrivate = $existingAcl.AreAccessRulesProtected -and $existingRules.Count -eq 2 -and
    @($existingRules | Where-Object {
        $_.IdentityReference.Value -notin $allowedSids -or $_.AccessControlType -ne 'Allow' -or
        $_.FileSystemRights -ne 'FullControl'
    }).Count -eq 0
if (-not $alreadyPrivate) { Set-Acl -LiteralPath $signingRoot -AclObject $acl }

$storeFile = Join-Path $signingRoot 'lvlup-life-release.p12'
$passwordFile = Join-Path $signingRoot 'password.dpapi'
$certificateFile = Join-Path $signingRoot 'release.cer'
$alias = 'lvlup-life-release'
if ((Test-Path -LiteralPath $storeFile) -and -not (Test-Path -LiteralPath $passwordFile)) {
    throw 'Existe una clave sin su secreto local. No se reemplazara la firma.'
}
if (-not (Test-Path -LiteralPath $passwordFile)) {
    $generated = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
    $secure = ConvertTo-SecureString $generated -AsPlainText -Force
    ConvertFrom-SecureString $secure | Set-Content -LiteralPath $passwordFile -Encoding ascii
    $generated = $null
}
$secure = (Get-Content -Raw -LiteralPath $passwordFile).Trim() | ConvertTo-SecureString
$secretPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
$previousPassword = [Environment]::GetEnvironmentVariable('LVLUP_RELEASE_SIGNING_PASSWORD', 'Process')
try {
    $env:LVLUP_RELEASE_SIGNING_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($secretPointer)
    if (-not (Test-Path -LiteralPath $storeFile)) {
        & $keytool -genkeypair -keystore $storeFile -storetype PKCS12 -alias $alias -keyalg RSA -keysize 3072 `
            -sigalg SHA256withRSA -validity 10000 -dname 'CN=Lvl Up Life' `
            -storepass:env LVLUP_RELEASE_SIGNING_PASSWORD -keypass:env LVLUP_RELEASE_SIGNING_PASSWORD | Out-Host
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear la firma de distribucion.' }
    }
    & $keytool -exportcert -keystore $storeFile -storetype PKCS12 -alias $alias `
        -storepass:env LVLUP_RELEASE_SIGNING_PASSWORD -file $certificateFile | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo verificar la clave existente.' }
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secretPointer)
    if ($null -eq $previousPassword) { Remove-Item Env:LVLUP_RELEASE_SIGNING_PASSWORD -ErrorAction SilentlyContinue }
    else { $env:LVLUP_RELEASE_SIGNING_PASSWORD = $previousPassword }
}
$certificate = [Security.Cryptography.X509Certificates.X509Certificate2]::new([IO.File]::ReadAllBytes($certificateFile))
[pscustomobject]@{
    StoreFile = $storeFile
    PasswordFile = $passwordFile
    Alias = $alias
    CertificateSha1 = $certificate.Thumbprint
    CertificateSha256 = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($certificate.RawData))
}
