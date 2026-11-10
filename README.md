# min

A minimal Android launcher: a black screen, the date and time, a few favorites, and a search you
pull down. Everything else is a gesture away.

## Using it

- **Home** — date and time top-left (12/24 h follows the system), favorites bottom-left (both
  movable, see *Customise home*).
  Tap a favorite to open it; long-press for *Remove from favorites* / *App info*.
  Long-press the clock for settings.
- **Search** — swipe **down** anywhere on home. The keyboard comes up and the input rides right on
  top of it. Results stack upwards from the input, best match closest. Swipe sideways between
  **apps**, **contacts** (once allowed) and **search** (send the query to the web or to apps like
  YouTube, Spotify, Maps, Play Store…). Enter opens the top result; swipe **up** to go back.
  Empty search lists every app, most recently used first. Long-press an app result for the menu.
  Search is universal: as you type it slides to whichever category has the closest match (type
  `aa` and contact *Aarav* beats app *Adobe Scan*). Lists fade out at their edges as they scroll.
- **Matching** — forgiving but strict: exact text wins, then word starts, then exactly one typo
  (`chtgpt` → ChatGPT), never two.
- **Settings** — display, customisation, search providers (switch and drag to reorder), animations
  (every spring is tunable), permissions. Settings has its own search.
- **Customise home** (Settings → Customisation) — home itself becomes the editor (the idea of
  [token-studio](https://github.com/bhavyam2468/token-studio), applied in place). Touch, then glide:
  - **background** — glide up/down for dark ⇄ light, sideways to tint it; pinch to scale everything.
  - **apps** — glide sideways for hue, up/down for vividness; tap for the next style (plain, solid,
    soft, outline, ghost, gradient); drag their sides for padding, top/bottom for row spacing,
    corners for roundness (keep going for a pill); pinch to resize.
  - **clock** — tap for the next typeface, glide for weight; drag its sides for more or less detail
    (`2:25` → `Tue, 10  2:25` → … → `Tuesday, 10 November 2026  2:25`), its top/bottom to split it over
    up to three lines, its corners (or pinch) to resize. Double-tap to type your own format.
  - **hold** the clock or the apps to lift them, drag, and they snap into one of nine spots
    (left · centre · right × top · middle · bottom).
  Undo, redo, surprise me and reset sit at the bottom; the legend chips show where each control lives.

  Clock formats work like KWGT: `d` `dd` day (2, 02) · `D` `DD` weekday (Fri, Friday) · `m` `mm`
  month (5, 05) · `M` `MM` month name (May, May / Jan, January) · `y` `yy` `yyyy` year (8, 08, 2008) ·
  `h` `hh` hour (12/24 h follows the system) · `i` `ii` minutes · `a` `A` am/pm. Anything else prints
  as typed; quote words to keep their letters (`'at' h:ii`). So `D, dd mm yyyy` reads `Fri, 02 05 2008`.

## Building

Requirements: JDK 17 and the Android SDK (platform 35). No third-party libraries.

```sh
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # fuzzy matcher and clock format tests
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
