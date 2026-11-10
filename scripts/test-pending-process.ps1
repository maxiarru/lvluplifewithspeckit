param([Parameter(Mandatory = $true)][string]$Adb)
$ErrorActionPreference = 'Stop'
$runner = 'lvluplife.android.test/androidx.test.runner.AndroidJUnitRunner'
function Invoke-Stage([string]$Method) {
    $output = & $Adb shell am instrument -w -e class "lvluplife.data.PendingStoreProcessStages#$Method" $runner
    $output | Write-Output
    if ($LASTEXITCODE -ne 0 -or ($output -join "`n") -notmatch 'OK \(1 test\)') {
        throw "Instrumentation stage failed: $Method"
    }
}
Invoke-Stage 'persistBeforeProcessDeath'
& $Adb shell am force-stop lvluplife.android
if ($LASTEXITCODE -ne 0) { throw 'Could not stop target process' }
Invoke-Stage 'recoverAfterProcessDeath'
