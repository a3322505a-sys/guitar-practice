param(
    [string]$Keystore = 'D:\软件\001-release.p12',
    [string]$PasswordFile = 'D:\软件\001-release.password',
    [ValidateSet('release','compare','p0')][string]$BuildType = 'release'
)
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$toolchain = 'C:\Users\b3322\Documents\Codex\projects\001\work\toolchain'
$java = Join-Path $toolchain 'jdk17\jdk-17.0.20.1+1\bin\java.exe'
$keytool = Join-Path $toolchain 'jdk17\jdk-17.0.20.1+1\bin\keytool.exe'
$sdkTools = Join-Path $toolchain 'android-sdk\build-tools\35.0.0'
$output = Join-Path $project 'output'
New-Item -ItemType Directory -Path $output -Force | Out-Null
$cert = Join-Path $output 'release-cert.der'
& $keytool -exportcert -keystore $Keystore -storepass:file $PasswordFile -alias guitar-release -file $cert
if($LASTEXITCODE -ne 0) { throw 'Cannot export existing certificate' }
if((Get-FileHash -LiteralPath $cert -Algorithm SHA256).Hash -ne '43b7af607adb264fb2faa79437d91dbc7e1bea7d7ce92804965fe0f1c8daf320') { throw 'Existing release identity does not match guitar-practice' }
$aligned = Join-Path $output "$BuildType-aligned-unsigned.apk"
$apkDirectory = Join-Path $project "app\build\outputs\apk\$BuildType"
$metadata = Get-Content -LiteralPath (Join-Path $apkDirectory 'output-metadata.json') -Raw | ConvertFrom-Json
if($metadata.variantName -ne $BuildType -or $metadata.elements.Count -ne 1) { throw 'Expected one APK for the selected build type' }
$expectedApplicationId = 'com.a3322505a.guitarlearning'
if($BuildType -eq 'compare') { $expectedApplicationId += '.compare' }
if($metadata.applicationId -ne $expectedApplicationId) { throw 'APK applicationId does not match the selected build type' }
$version = $metadata.elements[0]
& (Join-Path $sdkTools 'zipalign.exe') -f -p 4 (Join-Path $apkDirectory $version.outputFile) $aligned
if($LASTEXITCODE -ne 0) { throw 'APK alignment failed' }
$signed = Join-Path $output ("guitar-practice-{0}-v{1}.apk" -f $version.versionName, $version.versionCode)
if(Test-Path -LiteralPath $signed) { throw "Signed output already exists: $signed" }
& $java -jar (Join-Path $sdkTools 'lib\apksigner.jar') sign --ks $Keystore --ks-key-alias guitar-release --ks-pass "file:$PasswordFile" --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --out $signed $aligned
if($LASTEXITCODE -ne 0) { throw 'Signing failed' }
& $java -jar (Join-Path $sdkTools 'lib\apksigner.jar') verify --verbose --print-certs $signed
if($LASTEXITCODE -ne 0) { throw 'Signature verification failed' }
& (Join-Path $sdkTools 'zipalign.exe') -c -v 4 $signed | Select-Object -Last 1
if($LASTEXITCODE -ne 0) { throw 'Alignment verification failed' }
Get-FileHash -LiteralPath $signed -Algorithm SHA256
