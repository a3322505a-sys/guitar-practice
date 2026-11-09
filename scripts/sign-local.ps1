param(
    [Parameter(Mandatory=$true)][string]$Keystore,
    [Parameter(Mandatory=$true)][string]$PasswordFile,
    [string]$KeyAlias = 'guitar-release'
)
$ErrorActionPreference = 'Stop'
$names = @('ANDROID_KEYSTORE_PATH','ANDROID_KEYSTORE_PASSWORD','ANDROID_KEY_ALIAS','ANDROID_KEY_PASSWORD')
$before = @{}
foreach ($name in $names) { $before[$name] = [Environment]::GetEnvironmentVariable($name) }
try {
    $env:ANDROID_KEYSTORE_PATH = (Resolve-Path -LiteralPath $Keystore).Path
    $env:ANDROID_KEYSTORE_PASSWORD = (Get-Content -LiteralPath $PasswordFile -Raw).Trim()
    $env:ANDROID_KEY_ALIAS = $KeyAlias
    $env:ANDROID_KEY_PASSWORD = $env:ANDROID_KEYSTORE_PASSWORD
    & "$PSScriptRoot\build-local.ps1" ':app:assembleRelease'
    $result = $LASTEXITCODE
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $before[$name]) }
}
exit $result
