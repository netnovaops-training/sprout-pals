# Confirms the signing password reached this script from Proton Pass, without ever printing it.
# Run:  pass-cli run --env-file signing.env -- powershell -NoProfile -File tools\check-signing-secret.ps1
$p = $env:SPROUT_KEYSTORE_PASSWORD
if (-not $p) { Write-Output "missing: SPROUT_KEYSTORE_PASSWORD is not set"; exit 1 }
if ($p.StartsWith('pass://')) { Write-Output "not resolved: still a pass:// reference"; exit 1 }
Write-Output ("ok: password received, {0} characters" -f $p.Length)
