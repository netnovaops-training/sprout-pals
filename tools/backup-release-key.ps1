# Backs up the release signing key FILE into Proton Pass as a secure note (Base64 text),
# piped straight into pass-cli - nothing is printed. The key's password is already in the item
# "Sprout Pals release signing key"; this keeps the file itself safe too.
# (Proton Pass's CLI can't upload attachments, so the file travels as text.)
#
# Run from the project folder:   powershell -NoProfile -File tools\backup-release-key.ps1
# Check it afterwards:            powershell -NoProfile -File tools\backup-release-key.ps1 -Verify
param([switch]$Verify)
$ErrorActionPreference = 'Stop'
$passCli = Join-Path $env:LOCALAPPDATA 'Programs\ProtonPass\pass-cli.exe'
$keystore = Join-Path $env:USERPROFILE '.android-keys\sproutpals-release.jks'
$title = 'Sprout Pals release signing key FILE (backup)'

if (-not (Test-Path $keystore)) { Write-Output "No key file at $keystore"; exit 1 }
$original = (Get-FileHash $keystore -Algorithm SHA256).Hash

if ($Verify) {
    # Pull the note back, decode it to a temporary file, and compare fingerprints. Prints only the verdict.
    $note = & $passCli item view --vault-name 'Passwords' --item-title $title --field note 2>$null | Out-String
    if (-not $note.Trim()) { Write-Output 'Backup note not found.'; exit 1 }
    # everything below the "-----" divider is the file, wrapped at 76 characters (the last line is shorter)
    $parts = $note -split "(?m)^-----\s*$", 2
    if ($parts.Count -lt 2) { Write-Output 'Backup note has no divider line.'; exit 1 }
    $b64 = $parts[1] -replace '\s', ''
    $tmp = [System.IO.Path]::GetTempFileName()
    try {
        [System.IO.File]::WriteAllBytes($tmp, [Convert]::FromBase64String($b64))
        $restored = (Get-FileHash $tmp -Algorithm SHA256).Hash
    } finally { Remove-Item $tmp -Force -ErrorAction SilentlyContinue }
    if ($restored -eq $original) { Write-Output 'ok: the backup in Proton Pass restores to an identical key file' }
    else { Write-Output 'MISMATCH: the backup does not restore to the same file'; exit 1 }
    exit 0
}

$b64 = [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($keystore))
# wrap at 76 characters so the note stays readable in the app
$lines = for ($i = 0; $i -lt $b64.Length; $i += 76) { $b64.Substring($i, [Math]::Min(76, $b64.Length - $i)) }
$note = @"
Sprout Pals Android release signing key (sproutpals-release.jks), Base64.
SHA-256 of the file: $original
Password: the item "Sprout Pals release signing key" in this vault. Key alias: sproutpals.

To restore on a PC (PowerShell): copy everything below the line into key.b64, then
  [IO.File]::WriteAllBytes("`$env:USERPROFILE\.android-keys\sproutpals-release.jks", [Convert]::FromBase64String((Get-Content key.b64 -Raw) -replace '\s',''))
-----
$($lines -join "`n")
"@

@{ title = $title; note = $note } | ConvertTo-Json | & $passCli item create note --vault-name 'Passwords' --from-template - *> $null
if ($LASTEXITCODE -ne 0) { Write-Output 'Proton Pass did not save the note.'; exit 1 }
Write-Output "ok: key file backed up to Proton Pass as '$title'"
