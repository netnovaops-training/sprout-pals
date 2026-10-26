# Installing Sprout Pals

Sprout Pals runs on Android and iPhone. Both versions are the same game and get updates.

Downloads live on the [Releases page](https://github.com/netnovaops-training/sprout-pals/releases):
- **Android:** `SproutPals-<version>.apk` (releases named "Sprout Pals for Android")
- **iPhone:** `SproutPals.ipa` (releases named "Sprout Pals for iPhone")

Each file has a `.sha256` next to it, so you can check the download is exactly the published one.

## Android

Needs Android 14 or newer.

1. On the phone, open the Releases page and download the newest `SproutPals-<version>.apk`.
2. Open the downloaded file. If Android asks, allow your browser or Files app to **install unknown apps**, then go back and tap **Install**.
3. Open Sprout Pals. The first time you use steps or shaking, it asks for the motion permission. This is only used to count steps and feel a shake.

**Updates:** the app checks for new versions by itself and offers the download. You can also install a newer APK over the old one; your pet is kept.

## iPhone

Apple only allows App Store apps by default, so the iPhone version is installed with **SideStore**. SideStore is a free app that signs apps with your own Apple ID.

1. Install SideStore by following the official guide at <https://sidestore.io>. Do this once, on a computer.
2. On the iPhone, open SideStore → **Sources** → **+** and add:
   `https://jasonagibson.zapto.org/game/sidestore.json`
3. Find **Sprout Pals** in that source and tap **Get**.
4. Open SideStore about once a week and tap **Refresh All**. Free Apple IDs need apps re-signed every 7 days.

**Updates:** SideStore shows an update when a new version is in the source.

Or, without the source: download `SproutPals.ipa` from the Releases page on the iPhone, then in SideStore go to **My Apps** → **+** and pick the file.

## Check a download (optional)

On a computer:

```
sha256sum SproutPals-0.16.apk        # Linux / Mac
certutil -hashfile SproutPals-0.16.apk SHA256   # Windows
```

The result must match the `.sha256` file on the release.
