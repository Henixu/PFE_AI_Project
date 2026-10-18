$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$publish = Join-Path $root 'launcher\publish'

dotnet publish (Join-Path $PSScriptRoot 'ProjectLauncher.csproj') `
  --configuration Release `
  --runtime win-x64 `
  --self-contained true `
  -p:PublishSingleFile=true `
  --output $publish

Copy-Item (Join-Path $publish 'PFE-Project-Launcher.exe') (Join-Path $root 'PFE-Project-Launcher.exe') -Force
Write-Host "Created $root\PFE-Project-Launcher.exe"