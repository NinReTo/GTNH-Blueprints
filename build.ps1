param([string] $Game = "$env:APPDATA/.minecraft/versions/GTNH - помогите", [string] $Libraries = "$env:APPDATA/.minecraft/libraries")
$ErrorActionPreference = 'Stop'
python (Join-Path $PSScriptRoot 'tools/build.py') $Game $Libraries
exit $LASTEXITCODE
