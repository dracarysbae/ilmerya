# Copies Apple signing material into the encrypted "apple-distribution" environment of
# dracarysbae/ilmerya. Values go to GitHub over stdin; nothing is printed or written to disk.
# Run it yourself in PowerShell:  powershell -ExecutionPolicy Bypass -File tools\set-ios-secrets.ps1
param(
    [Parameter(Mandatory = $true)][string]$DistributionP12,   # Apple Distribution certificate (.p12)
    [string]$ApiKey = "$env:USERPROFILE\Downloads\AuthKey_HCVRZ6XS27.p8",
    [string]$ApiKeyId = "HCVRZ6XS27",
    [Parameter(Mandatory = $true)][string]$IssuerId           # App Store Connect > Users and Access > Integrations > Keys
)
$ErrorActionPreference = "Stop"
$repo = "dracarysbae/ilmerya"
$envName = "apple-distribution"
function Set-Secret([string]$name, [string]$value) {
    $value | gh secret set $name --repo $repo --env $envName
    if ($LASTEXITCODE -ne 0) { throw "Could not set $name" }
    Write-Host "set $name"
}
foreach ($path in @($DistributionP12, $ApiKey)) { if (-not (Test-Path $path)) { throw "Missing file: $path" } }
$secure = Read-Host "Password of the .p12 file" -AsSecureString
$password = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
Set-Secret "APPLE_DISTRIBUTION_P12_B64" ([Convert]::ToBase64String([IO.File]::ReadAllBytes($DistributionP12)))
Set-Secret "APPLE_DISTRIBUTION_P12_PASSWORD" $password
Set-Secret "ASC_PRIVATE_KEY_B64" ([Convert]::ToBase64String([IO.File]::ReadAllBytes($ApiKey)))
Set-Secret "ASC_KEY_ID" $ApiKeyId
Set-Secret "ASC_ISSUER_ID" $IssuerId
$password = $null
Write-Host "Done. The App Store provisioning profile (ILMERYA_APPSTORE_PROFILE_B64) is added separately."
