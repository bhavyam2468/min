# AGENTS.md

Two kinds of agent work on **min**, a minimal Android launcher (Kotlin, plain framework Views, no
libraries). The **programming agent** writes code. The **testing agent** builds, runs and checks it,
and writes down what happened. They talk to each other through one file, `logs/LATEST.md`.

```
user prompt ──► programming agent ──► push to branch ──► testing agent ──► logs/LATEST.md ─┐
     ▲                                                                                     │
     └────────────────────── programming agent reads the log on its next turn ◄────────────┘
```

---

## The project in one screen

| Path (under `app/src/main/java/dev/bhavyam/min/`) | One job |
|---|---|
| `Globals.kt` | Every UI variable: default design tokens, type ramp, spacing, alphas, motion defaults. The `globals.css` of the app. |
| `MinApp.kt` | Process-wide state (settings, theme, favorites, app index, contacts, search engine). Background executor + main handler. |
| `LauncherActivity.kt` | Puts the layers together (home → search → settings → studio → popup) and decides what back, home and leaving do. |
| `LauncherUi.kt` | What the visible launcher exposes to the adb command line. |
| `motion/Spring.kt`, `motion/RubberBand.kt` | The one damped spring (closed-form, velocity-preserving) used for **every** animation; rubber-band resistance. |
| `apps/` | App index (`AppIndex`), launch options, usage recency, favorites. |
| `search/` | `FuzzyMatcher` (the strict matching rules), `Ranker`, the `SearchProvider` interface and its three providers (apps, contacts, fallback), `SearchTargets` (fallback catalogue), `SearchEngine`. |
| `settings/` | `SettingTypes` (vocabulary), `SettingsRegistry` (every setting, as data), `SettingsStore` (persistence + listeners). |
| `theme/` | Token Studio's model: `Tokens` → `Theme.derive` (OKLCH colour engine in `Oklch`), fonts, icon styles, button variants, `ThemeStore` (live + persisted), `TokenExport` (CSS / DTCG JSON). |
| `icons/` | SVG path parser, the icon set, a drawable that paints icons in the theme's icon style. |
| `ui/home/` | Home: date + time, favorites, the pull-down gesture. |
| `ui/search/` | Search layer, horizontal column pager, bottom-anchored result columns, keyboard-synced insets. |
| `ui/popup/` | The two-option context menu. |
| `ui/settings/` | Settings overlay built from the registry; switch, slider, provider list (drag to reorder). |
| `ui/studio/` | Token Studio: specimen card, editing zones, history, tooltip, ambient light, export sheet. |
| `ui/common/` | Small shared views/drawables (press highlight, buttons, icon views, max-width layout, text styling). |
| `ui/LaunchBlur.kt` | Blur-to-focus when the launcher returns, blur-out as an app opens. |
| `cli/` | adb receiver and the command table. |

Tests live in `app/src/test/java/dev/bhavyam/min/` (JVM unit tests, JUnit 4).

---

## Programming agent

You turn user prompts and the testing agent's log into code.

### Before writing anything
1. Read `logs/LATEST.md` if it exists. Fix every **FAIL** and **BUILD BREAK** it reports before
   starting new work, unless the user says otherwise. If the log shows the testing agent made a
   stop-gap fix, replace it with a proper one (or keep it, and say why).
2. Read the files you are about to touch. Match their style.

### Rules (non-negotiable)
- **Minimalism is the product.** Build what the user asked for, nothing more. No extra settings,
  options, screens or "improvements" to the design.
- **No dead code, no unused imports, no duplicate logic, no magic hacks.** Every class, function,
  constant and import must be used and must be explainable — the user asks why things exist.
- **One job per file.** If a file starts doing two things, split it.
- **No hard-coded UI values in views.** Sizes, colours, alphas, timings come from `Globals` or the
  `Theme`. Component-internal geometry may be a named `private const` with a comment.
- **All motion is `motion/Spring`.** No `ValueAnimator`, `ObjectAnimator`, `ViewPropertyAnimator`,
  `Transition`s or interpolators. Springs take their spec from a `MotionSetting` so the animation
  sliders in settings tune them.
- **Settings are data.** A new setting is one entry in `SettingsRegistry` (and a `Section`/`Group`
  placement). Storage, the settings screen, settings search and adb `get_setting`/`set_setting`
  pick it up with no other change. Only a brand-new *kind* of control needs a new row class.
- **Search goes through `FuzzyMatcher`.** Apps, contacts and settings share it. Its rules are in
  its KDoc and pinned by `FuzzyMatcherTest` — change them only on the user's instruction, and update
  the test in the same change.
- **Low allocation on hot paths.** Nothing allocated per keystroke (beyond the result list) or per
  animation frame. Spring callbacks must not allocate.
- **Respect insets everywhere** (system bars, display cutout, IME). Phones, tablets and landscape
  must all look intentional — use `MaxWidthLayout` for text-heavy screens.
- **No new dependencies** unless the user asks. The fonts in `assets/fonts` are OFL-licensed
  (`assets/fonts/OFL.txt`).
- Never commit anything under `logs/` except `logs/LATEST.md`, and never edit that file — it belongs
  to the testing agent.

### Extending, the intended way
- **Setting:** add to `SettingsRegistry`. Done.
- **Fallback search provider:** add a `SearchTarget` to `SearchTargets.CATALOG` (it is offered only
  when its intent resolves on the device).
- **Search category:** implement `SearchProvider`, add it to the engine's list in `MinApp`.
- **adb command:** add a `Command` to the table in `cli/Commands.kt`; `help` lists it automatically.
- **UI variable:** add it to `Globals` in the right object, with its unit in the comment.

### Before you push
- The project must compile (`./gradlew assembleDebug`) and unit tests must pass
  (`./gradlew testDebugUnitTest`). If you can't run Gradle in your environment, at least type-check
  the sources with `kotlinc` against a **public SDK** `android.jar` (API 35) — AOSP jars contain
  hidden APIs that compile but won't link.
- Grep for leftovers: unused imports, unused private members, `TODO`, debug logging.
- Commit with a message that says *what* and *why*. Push to the session branch only.

---

## Testing agent

You take the latest version of the branch, build it, test it, and write an honest log. You do
**not** develop features or redesign anything.

### 1. Start clean
```sh
git fetch origin && git checkout <branch> && git reset --hard origin/<branch>
rm -f logs/*.md logs/*.txt            # delete every log from past turns
mkdir -p logs
```
Then create `logs/LATEST.md` from the template below and keep appending to it as you go — write
down every command you run and its outcome *as you run it*, not afterwards from memory.

### 2. Environment
- JDK 17, Android SDK with `platforms;android-35`, `build-tools;35.0.0`, `platform-tools`.
- `local.properties` with `sdk.dir=…` (never commit it).
- Record versions: `java -version`, `./gradlew --version`, `sdkmanager --list_installed`.

### 3. Build and unit tests
```sh
./gradlew --no-daemon assembleDebug testDebugUnitTest lintDebug 2>&1 | tee logs/build.txt
```
`logs/build.txt` is scratch (not committed); copy the relevant part of any failure into
`LATEST.md`.

### 4. Debug just enough to make it work
If the build breaks you may make the **smallest** change that gets it compiling and running
(a wrong import, a missing resource, a Gradle version). Rules:
- No refactors, no style changes, no "while I'm here" fixes, no new features.
- Every change is listed in the log under **Stop-gap fixes** with file, diff summary and reason.
- If a fix would need a design decision, don't make it: log it as **BUILD BREAK** and stop.

### 5. Run it
On an emulator (API 31 and API 35 if possible; one phone, one tablet or landscape run):
```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell cmd package set-home-activity dev.bhavyam.min/.LauncherActivity
adb shell input keyevent KEYCODE_HOME
```
Drive it with the adb command line (see below) and `adb shell input`, and capture evidence:
`adb exec-out screencap -p > logs/<name>.png`, `adb logcat -d -b crash`, `adb shell dumpsys gfxinfo
dev.bhavyam.min` for jank. Screenshots are scratch — describe what they show in the log.

### 6. What to check (each gets PASS / FAIL / SKIP with a one-line reason)
- Home: black, date + time top-left, live, follows 12/24 h. Long-press clock opens settings.
  Favorites seeded on first run; tap launches; long press shows the two-option popup.
- Swipe down opens search with keyboard; input sits right above the keyboard while it animates;
  swipe up closes. Category label sits right on top of the input.
- Categories page horizontally; contacts page hidden without permission.
- Fuzzy rules: `search chastgpt` / `search chtgpt` find ChatGPT; `search cshtgpt` / `search ctgpt`
  don't (install or mock an app labelled ChatGPT, or use another multi-word label).
- Enter runs the top result. Empty query lists all apps, recent first.
- Settings: every section present, sliders/switches persist, provider drag-reorder persists,
  permission rows grant.
- Token Studio: every zone edits (corners, button edges, fill drag/tap, text tap/drag, icon tap/drag,
  surface drag, elevation drag, pinch), undo/redo/shuffle/reset, hold-to-preview, export + copy.
  The whole launcher restyles.
- Blur-to-focus on return from an app and on unlock; launcher resets to home after leaving.
- Rotation, tablet width, display cutout, gesture vs 3-button navigation.
- Every adb command, including error cases (unknown command, unknown app, bad value).
- `adb logcat -d -b crash` is empty at the end.

### 7. Log template — `logs/LATEST.md`
```md
# Test run — <date, UTC> — <branch>@<short sha>

## Environment
## Commands run (in order, with outcome)
## Build
## Unit tests
## Stop-gap fixes
## Checks
| Area | Result | Notes |
## Failures (each: steps, expected, actual, logcat/stack trace)
## Performance notes
## For the programming agent (prioritised list)
```

### 8. Finish
Commit `logs/LATEST.md` and any stop-gap fixes (nothing else) to the same branch, message
`test: <summary>`, and push.

---

## adb command line

The launcher accepts commands through an exported receiver protected by `android.permission.DUMP`,
which `adb shell` holds and ordinary apps can't get. Reply text comes back as the broadcast's result
data; result code 0 = ok, 1 = error.

```sh
adb shell am broadcast -a dev.bhavyam.min.CMD -n dev.bhavyam.min/.cli.CliReceiver --es cmd "'help'"
```

**Quoting:** `adb shell` joins its arguments and hands them to the device's shell, which splits
them again. So a command with spaces needs two layers: double quotes for your shell, single quotes
inside for the device shell — `--es cmd "'add_favorite chat gpt'"`. With only one layer the device
sees `--es cmd add_favorite` followed by a stray `chat gpt`.

| Command | Does |
|---|---|
| `help` | lists commands |
| `status` | launcher / search / settings state, counts |
| `search <query>` | results per category, ranked like the search screen |
| `open_app <name>` | launches the best match |
| `list_favorites` | favorites, top to bottom |
| `add_favorite <name>` / `remove_favorite <name>` | edit favorites (`ok: added X to favorites`) |
| `open_settings` / `close_settings` | settings overlay |
| `open_search [query]` / `close_search` | search layer |
| `get_setting [key]` | one or all settings (`status_bar`, `blur`, `anim_speed`, …) |
| `set_setting <key> <value>` | `on`/`off` for switches, a number in range for sliders |
