# Creates the app's release signing key ONCE. Every future update must be signed with this same
# key, or phones refuse to install it over the old version - so back up the .jks file.
#
# The password comes from Proton Pass ("Sprout Pals release signing key") and is handed straight
# to keytool through an environment variable; it is never typed, printed, or saved to disk.
#
# Run from the project folder:
#   pass-cli run --env-file signing.env -- powershell -NoProfile -File tools\make-release-key.ps1
$ErrorActionPreference = 'Stop'
$keyDir = Join-Path $env:USERPROFILE '.android-keys'
$keystore = Join-Path $keyDir 'sproutpals-release.jks'

if (-not $env:SPROUT_KEYSTORE_PASSWORD -or $env:SPROUT_KEYSTORE_PASSWORD.StartsWith('pass://')) {
    Write-Output 'No password from Proton Pass. Run this through: pass-cli run --env-file signing.env -- ...'
    exit 1
}
if (Test-Path $keystore) {
    Write-Output "Already exists, not replacing it (replacing it would break updates): $keystore"
    exit 0
}

New-Item -ItemType Directory -Force $keyDir | Out-Null
$keytool = 'C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe'
# keytool reports progress on stderr; with 'Stop' PowerShell would treat that as a failure
$ErrorActionPreference = 'Continue'
$p = Start-Process -FilePath $keytool -NoNewWindow -Wait -PassThru -RedirectStandardError (Join-Path $keyDir 'keytool.log') -ArgumentList @(
    '-genkeypair', '-keystore', "`"$keystore`"", '-storetype', 'PKCS12',
    '-alias', 'sproutpals', '-keyalg', 'RSA', '-keysize', '4096', '-validity', '36500',
    '-storepass:env', 'SPROUT_KEYSTORE_PASSWORD', '-keypass:env', 'SPROUT_KEYSTORE_PASSWORD',
    '-dname', '"CN=Sprout Pals, O=Jason Gibson, C=US"'
)
if ($p.ExitCode -ne 0) { Write-Output "keytool failed (exit $($p.ExitCode)); see $keyDir\keytool.log"; exit 1 }

if (Test-Path $keystore) { Write-Output "ok: release key created at $keystore - back this file up" }
else { Write-Output 'keytool did not create the key'; exit 1 }
