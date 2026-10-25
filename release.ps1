# Publish a new Sprout Pals version to phones. One command, from the project folder:
#
#   pass-cli run --env-file signing.env -- powershell -NoProfile -File release.ps1 "what changed"
#
# 1. builds the release APK, signed with the release key (password from Proton Pass, never shown)
# 2. writes version.json (versionCode, versionName, sha256, md5, notes) - the app compares versionCode;
#    0.14+ checks the download's sha256, md5 stays only so phones on 0.13 or older can still update
# 3. copies both to the NUC's web/public/game/, served at https://jasonagibson.zapto.org/game/
#
# Bump versionCode AND versionName in app/build.gradle.kts before each release, or phones won't see
# an update (this script does not bump them).
param([string]$Notes = "")
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if (-not $env:SPROUT_KEYSTORE_PASSWORD -or $env:SPROUT_KEYSTORE_PASSWORD.StartsWith('pass://')) {
    Write-Output 'No signing password. Run through: pass-cli run --env-file signing.env -- powershell -NoProfile -File release.ps1'
    exit 1
}

$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$ErrorActionPreference = 'Continue'   # gradle chats on stderr
& .\gradlew.bat --no-daemon -q ":app:testDebugUnitTest" ":app:assembleRelease"
if ($LASTEXITCODE -ne 0) { Write-Output 'Build or tests failed - nothing published.'; exit 1 }
$ErrorActionPreference = 'Stop'

$apk = 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $apk)) { Write-Output "No signed APK at $apk (is the release key set up? tools\make-release-key.ps1)"; exit 1 }

$gradle = Get-Content 'app\build.gradle.kts' -Raw
$code = [int]([regex]::Match($gradle, 'versionCode\s*=\s*(\d+)').Groups[1].Value)
$name = [regex]::Match($gradle, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
$sha256 = (Get-FileHash $apk -Algorithm SHA256).Hash.ToLower()
$md5 = (Get-FileHash $apk -Algorithm MD5).Hash.ToLower()   # for apps before 0.14 only

$out = Join-Path $PSScriptRoot 'build-release'
New-Item -ItemType Directory -Force $out | Out-Null
Copy-Item $apk (Join-Path $out 'sproutpals.apk') -Force
$manifest = [ordered]@{
    versionCode = $code
    versionName = $name
    url         = 'https://jasonagibson.zapto.org/game/sproutpals.apk'
    sha256      = $sha256
    md5         = $md5
    notes       = $Notes
} | ConvertTo-Json
# UTF-8 without a byte-order mark (Windows PowerShell 5.1 has no utf8NoBOM option)
[System.IO.File]::WriteAllText((Join-Path $out 'version.json'), $manifest, (New-Object System.Text.UTF8Encoding $false))

# APK first, then the manifest, so a phone never sees a version.json pointing at an APK not yet there
& scp -q -o BatchMode=yes (Join-Path $out 'sproutpals.apk') 'server@192.168.86.86:nuc-homelab/web/public/game/sproutpals.apk'
if ($LASTEXITCODE -ne 0) { Write-Output 'Copy to the NUC failed.'; exit 1 }
& scp -q -o BatchMode=yes (Join-Path $out 'version.json') 'server@192.168.86.86:nuc-homelab/web/public/game/version.json'
if ($LASTEXITCODE -ne 0) { Write-Output 'Copy to the NUC failed.'; exit 1 }

Write-Output "Published Sprout Pals $name (code $code), sha256 $sha256"
Write-Output 'Install link: https://jasonagibson.zapto.org/game/sproutpals.apk'
