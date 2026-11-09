param([Parameter(Mandatory=$true)][string]$BackupZip)
$ErrorActionPreference = 'Stop'
$repository = 'a3322505a-sys/guitar-practice'
& gh auth status
if ($LASTEXITCODE -ne 0) { throw 'Run gh auth login first, then run this command again.' }
$privateDir = Join-Path ([IO.Path]::GetTempPath()) ([guid]::NewGuid().ToString())
try {
    Expand-Archive -LiteralPath $BackupZip -DestinationPath $privateDir
    $keystore = Join-Path $privateDir '001-release.p12'
    $password = (Get-Content -LiteralPath (Join-Path $privateDir '001-release.password') -Raw).Trim()
    $certificate = Join-Path $privateDir 'certificate.der'
    $env:GUITAR_SETUP_PASSWORD = $password
    & keytool -exportcert -keystore $keystore -storepass:env GUITAR_SETUP_PASSWORD -alias guitar-release -file $certificate
    if ($LASTEXITCODE -ne 0) { throw 'Cannot open the backed-up release key.' }
    if ((Get-FileHash $certificate -Algorithm SHA256).Hash.ToLower() -ne '43b7af607adb264fb2faa79437d91dbc7e1bea7d7ce92804965fe0f1c8daf320') { throw 'Wrong release certificate.' }
    $values = [ordered]@{
        ANDROID_KEYSTORE_BASE64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystore))
        ANDROID_KEYSTORE_PASSWORD = $password
        ANDROID_KEY_ALIAS = 'guitar-release'
        ANDROID_KEY_PASSWORD = $password
    }
    foreach ($entry in $values.GetEnumerator()) {
        $start = New-Object System.Diagnostics.ProcessStartInfo
        $start.FileName = (Get-Command gh).Source
        $start.Arguments = "secret set $($entry.Key) --repo $repository"
        $start.UseShellExecute = $false
        $start.RedirectStandardInput = $true
        $process = [System.Diagnostics.Process]::Start($start)
        $process.StandardInput.Write($entry.Value)
        $process.StandardInput.Close()
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) { throw "Could not configure $($entry.Key)." }
    }
    Write-Host 'Signing configured. Run Android core on main in GitHub Actions to publish.'
} finally {
    Remove-Item Env:GUITAR_SETUP_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $privateDir -Recurse -Force -ErrorAction SilentlyContinue
}
