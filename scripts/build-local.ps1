param([Parameter(ValueFromRemainingArguments=$true)][string[]]$Tasks)
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$toolchain = 'C:\Users\b3322\Documents\Codex\projects\001\work\toolchain'
$env:JAVA_HOME = Join-Path $toolchain 'jdk17\jdk-17.0.20.1+1'
$env:ANDROID_HOME = Join-Path $toolchain 'android-sdk'
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"
Set-Location -LiteralPath $project
& .\gradlew.bat @Tasks
exit $LASTEXITCODE
