# Publishes the iPhone app on jasonagibson.zapto.org/game, next to the Android one.
#
#   powershell -NoProfile -File release-ios.ps1 0.12
#
# 1. Downloads SproutPals.ipa from the GitHub Release "ios-v<version>" (made by .github/workflows/ios.yml
#    when that tag is pushed) and checks it against the SHA-256 the build wrote next to it.
# 2. Writes sidestore.json - a SideStore / AltStore "source", so the iPhone can add the app by URL and
#    see updates by itself.
# 3. Copies both to the NUC's web/public/game/ (served by Caddy; nothing new is opened).
param([Parameter(Mandatory = $true)][string]$Version)
$ErrorActionPreference = 'Stop'

$repo = 'netnovaops-training/sprout-pals'   # the Apple version is the "apple" branch there for now
$tag = "ios-v$Version"
$base = 'https://jasonagibson.zapto.org/game'
$out = Join-Path $PSScriptRoot 'build-release'
New-Item -ItemType Directory -Force $out | Out-Null
$ipa = Join-Path $out 'sproutpals.ipa'

"Downloading $tag from GitHub..."
Invoke-WebRequest "https://github.com/$repo/releases/download/$tag/SproutPals.ipa" -OutFile $ipa -UseBasicParsing
$expected = ((Invoke-WebRequest "https://github.com/$repo/releases/download/$tag/SproutPals.ipa.sha256" -UseBasicParsing).Content -split '\s+')[0]
$actual = (Get-FileHash $ipa -Algorithm SHA256).Hash.ToLower()
if ($expected.ToLower() -ne $actual) { throw "The download does not match the build's SHA-256 - stopped." }
$size = (Get-Item $ipa).Length
"OK: $([math]::Round($size / 1MB, 1)) MB, sha256 $actual"

# SideStore / AltStore source (https://faq.altstore.io/developers/make-a-source)
$source = [ordered]@{
    name       = 'Sprout Pals'
    identifier = 'org.jasonagibson.sproutpals.source'
    sourceURL  = "$base/sidestore.json"
    apps       = @(
        [ordered]@{
            name                 = 'Sprout Pals'
            bundleIdentifier     = 'org.jasonagibson.sproutpals'
            developerName        = 'Jason Gibson'
            subtitle             = 'A 16-bit garden virtual pet'
            localizedDescription = 'Hatch a pal, care for it, grow a garden, visit friends. The same game as the Android app.'
            iconURL              = "$base/sproutpals-icon.png"
            tintColor            = '2E5E3A'
            versions             = @(
                [ordered]@{
                    version      = $Version
                    date         = (Get-Date -Format 'yyyy-MM-dd')
                    downloadURL  = "$base/sproutpals.ipa"
                    size         = $size
                    minOSVersion = '16.0'
                }
            )
        }
    )
}
$json = Join-Path $out 'sidestore.json'
[IO.File]::WriteAllText($json, ($source | ConvertTo-Json -Depth 8), (New-Object Text.UTF8Encoding($false)))
$icon = Join-Path $PSScriptRoot 'iosApp\Resources\Assets.xcassets\AppIcon.appiconset\icon-1024.png'

"Copying to the NUC..."
# the app first, then the source that points at it, so nobody is offered a file that isn't there yet
scp $ipa "server@192.168.86.86:nuc-homelab/web/public/game/sproutpals.ipa"
scp $icon "server@192.168.86.86:nuc-homelab/web/public/game/sproutpals-icon.png"
scp $json "server@192.168.86.86:nuc-homelab/web/public/game/sidestore.json"
"Published Sprout Pals for iPhone $Version"
"  App:              $base/sproutpals.ipa"
"  SideStore source: $base/sidestore.json"
