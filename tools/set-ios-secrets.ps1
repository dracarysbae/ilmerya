# Copies the team's Apple Distribution certificate (.p12) into the encrypted "apple-distribution"
# environment of dracarysbae/ilmerya. Values go to GitHub over stdin; nothing is printed or written.
# The App Store profile and App Store Connect API key secrets are already set.
# Run it yourself:  powershell -ExecutionPolicy Bypass -File tools\set-ios-secrets.ps1 -DistributionP12 "<path to .p12>"
param([Parameter(Mandatory = $true)][string]$DistributionP12)
$ErrorActionPreference = "Stop"
if (-not (Test-Path $DistributionP12)) { throw "Missing file: $DistributionP12" }
$secure = Read-Host "Password of the .p12 file" -AsSecureString
$password = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
[Convert]::ToBase64String([IO.File]::ReadAllBytes($DistributionP12)) | gh secret set APPLE_DISTRIBUTION_P12_B64 --repo dracarysbae/ilmerya --env apple-distribution
$password | gh secret set APPLE_DISTRIBUTION_P12_PASSWORD --repo dracarysbae/ilmerya --env apple-distribution
$password = $null
Write-Host "Distribution certificate stored. Tell Claude to start the iOS TestFlight build."
