# min

A minimal Android launcher: a clock, your favourites, and a search that is always one swipe
away. Black and quiet out of the box — and, through a built-in token studio, as colourful,
round, bold or dense as you like.

- **Home** — date and time in the corner, favourites down the left. Tap to open, hold for
  *App info* and *Set as / Remove from favorites*. Hold the clock for settings.
- **Search** — swipe down. Type; results stack upward from the input, which rides on the
  keyboard. Every category is searched at once and the screen glides to the closest match;
  swipe sideways between apps, contacts and "search … on". Enter runs the top result.
  Swipe up to go home.
- **Forgiving, not sloppy** — one typo is forgiven (`chtgpt` → ChatGPT), two never are.
- **Customisation** — settings → customisation turns the real home screen into the editor:
  touch anything and drag. Hold and glide a favourite for its colour, tap it for the next
  button type, drag corners and edges for shape and padding, drag the background to shift
  it. Drag the favourites to any of nine magnetic spots, and resize the clock by its corner
  to pick its size and how much date it shows. Search results and the long-press menu are
  edited the same way. Undo, shuffle, reset, export.
- **Feel** — every motion is a real spring you can tune in settings → animations. The
  launcher blurs out as apps open and comes back into focus when you return.
- **adb** — drive and inspect it from a terminal: `scripts/min.sh help`.

Requires Android 13 (API 33) or newer.

## Build

```sh
./gradlew assembleDebug            # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest        # matcher and clock-format tests
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Needs JDK 17 and the Android SDK (platform 35). No other dependencies.

## adb

```sh
scripts/min.sh status
scripts/min.sh search chat
scripts/min.sh add_favorite Google Maps
scripts/min.sh set_setting anim_speed 1.5
```

Without the script, quote twice — once for your shell, once for the device's:

```sh
adb shell am broadcast -n com.bhavyam.min/.cli.CliReceiver -a com.bhavyam.min.CLI \
  --es cmd "'add_favorite Google Maps'"
```

## Docs

- [`docs/SPEC.md`](docs/SPEC.md) — behaviour contract and what every file is for
- [`AGENTS.md`](AGENTS.md) — how the programming and testing agents work on this repo
- [`THIRD_PARTY.md`](THIRD_PARTY.md) — fonts and icons, and their licences
