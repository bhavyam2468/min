# min

A minimal Android launcher: a black screen, the date and time, a few favorites, and a search you
pull down. Everything else is a gesture away.

## Using it

- **Home** — date and time top-left (12/24 h follows the system), favorites bottom-left.
  Tap a favorite to open it; long-press for *Remove from favorites* / *App info*.
  Long-press the clock for settings.
- **Search** — swipe **down** anywhere on home. The keyboard comes up and the input rides right on
  top of it. Results stack upwards from the input, best match closest. Swipe sideways between
  **apps**, **contacts** (once allowed) and **search** (send the query to the web or to apps like
  YouTube, Spotify, Maps, Play Store…). Enter opens the top result; swipe **up** to go back.
  Empty search lists every app, most recently used first. Long-press an app result for the menu.
- **Matching** — forgiving but strict: exact text wins, then word starts, then exactly one typo
  (`chtgpt` → ChatGPT), never two.
- **Settings** — display, customisation, search providers (switch and drag to reorder), animations
  (every spring is tunable), permissions. Settings has its own search.
- **Token Studio** (Settings → Customisation) — restyle the whole launcher by touching one card:
  drag its corners for roundness, button edges for padding, fills for colour (tap to change button
  type), text for typeface and weight, icons for icon style and stroke, the card for light/dark and
  tint, the space below for elevation; pinch to scale. Undo, redo, surprise me, reset, hold the eye to
  preview home, and export as `globals.css` or `tokens.json`. Ported from
  [token-studio](https://github.com/bhavyam2468/token-studio).

## Building

Requirements: JDK 17 and the Android SDK (platform 35). No third-party libraries.

```sh
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # fuzzy matcher tests
```

Android 12 (API 31) or newer. Install, press home, and choose **min**.

## adb

```sh
adb shell am broadcast -a dev.bhavyam.min.CMD -n dev.bhavyam.min/.cli.CliReceiver --es cmd "'search chat'"
```

Note the double-then-single quotes: `adb shell` re-splits arguments on the device, so commands with
spaces need both. `help` lists every command; see [AGENTS.md](AGENTS.md#adb-command-line) for the
full table. The receiver requires the `DUMP` permission, which only the adb shell holds.

## Working on it

See [AGENTS.md](AGENTS.md) for the project map, code rules, and the programming / testing agent
workflow. All UI variables live in `app/src/main/java/dev/bhavyam/min/Globals.kt`.

Fonts (Inter, Manrope, Space Grotesk, Nunito, Fraunces, JetBrains Mono) are bundled under the SIL
Open Font License — see `app/src/main/assets/fonts/OFL.txt`. Icons are drawn from Lucide (ISC).
