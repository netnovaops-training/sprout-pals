# Sprout Pals

A 90s-style virtual pet for Android, drawn like a 16-bit garden game. Shake the egg open and
you get one of the household: Jun, Kona, Rusty, Kara, DJ or Maui (and, 1 time in 100, a rare
digital Jason). Feed it, clean up after it, give it medicine, turn the lights off at night, and
walk — the phone's pedometer turns your steps into points and into a walking route past real
garden shops around Clifton Park, NY, with a buzz and a unique prize every 50 miles.

Lives run in real time: three days each as a baby, kid and adult, then old age, about two weeks
in all. Neglect can end a life early, the classic way. When a pet returns to the garden a memory
card is kept and a new egg appears.

## What's in here

| Folder | What |
|---|---|
| `app/` | The Android app (Kotlin, Jetpack Compose). |
| `app/src/main/kotlin/.../engine/` | The rules of life, pure Kotlin, no Android code, so a whole two-week life runs in the unit tests in milliseconds. |
| `app/src/main/kotlin/.../art/` | All the pixel art, drawn as text grids with per-character palettes. |
| `app/src/main/kotlin/.../ui/` | The screen: a 96×104 pixel "LCD", SNES-style shaded sprites, title scene. |
| `app/src/main/kotlin/.../app/` | Android glue: save file, pedometer, shake detector, reminders, chiptune sound, self-update. |

## Building

Open the folder in Android Studio, or from a terminal with the Studio JDK:

```
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest :app:assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/`. Debug builds have testing shortcuts:
**B** opens the egg (no shaking), long-press **A** fakes a walk, long-press **C** jumps to the day
before the next birthday, long-press the title to skip an hour.

## Self-update

The app checks `https://junsgarden.zapto.org/apps/sproutpals-version.json` on launch and offers
to download the new APK. The manifest and APK are published by the release script (coming).

## Credits

Game design and art direction: Jason A. Gibson. Code and pixel art drafted with Claude (Anthropic)
from Jason's descriptions, reference pictures and photos of the real Rusty, Kara, DJ and Maui.
Sound is generated in code (square waves); the background tune is original.
