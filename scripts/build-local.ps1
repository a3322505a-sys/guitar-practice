param([Parameter(ValueFromRemainingArguments=$true)][string[]]$Tasks)
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
if ($env:JAVA_HOME) { $env:PATH = "$env:JAVA_HOME\bin;$env:PATH" }
if (-not $env:ANDROID_HOME -and $env:ANDROID_SDK_ROOT) { $env:ANDROID_HOME = $env:ANDROID_SDK_ROOT }
if (-not $Tasks) { $Tasks = @(':app:assembleRelease') }
Push-Location -LiteralPath $project
try { & .\gradlew.bat @Tasks; $result = $LASTEXITCODE } finally { Pop-Location }
exit $result
