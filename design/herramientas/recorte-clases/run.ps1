param([string[]]$Names = @(), [string]$OutDir, [string]$Sheet, [int]$Cols = 8, [Parameter(Mandatory)][string]$Src)
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
Add-Type -AssemblyName System.Drawing
if (-not ('Cutout' -as [type])) { Add-Type -Path "$here\Cutout.cs" -ReferencedAssemblies System.Drawing }
New-Item -ItemType Directory -Force $OutDir | Out-Null
$ov = @(Import-Csv "$here\overrides.csv")
$Names = @($Names | ForEach-Object { $_ -split ',' } | Where-Object { $_ })
if ($Names.Count -eq 0) { $Names = (Get-ChildItem "$Src\*.png" | Sort-Object Name).BaseName }
$outs = @()
foreach ($n in $Names) {
  $excl = [double[]]@($ov | Where-Object { $_.name -eq $n -and $_.kind -eq 'excl' } | ForEach-Object { [double]$_.a, [double]$_.b, [double]$_.c, [double]$_.d })
  $samp = [double[]]@($ov | Where-Object { $_.name -eq $n -and $_.kind -eq 'sample' } | ForEach-Object { [double]$_.a, [double]$_.b })
  $o = Join-Path $OutDir "$n.png"
  $info = [Cutout]::Process("$Src\$n.png", $o, $excl, $samp)
  "$n  $info"
  $outs += $o
}
if ($Sheet) { [Cutout]::Sheet($outs, $Sheet, 200, 250, $Cols) }
