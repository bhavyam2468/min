# AGENTS.md

Two kinds of agents work on this repository, taking turns:

- **Programming agent** — writes and changes code from the user's prompts and the testing
  agent's logs.
- **Testing agent** — checks out the branch, builds it, runs it, fixes only what stops it from
  building or running, and writes down everything that happened.

They talk through the repository: code and `docs/SPEC.md` one way, `logs/` the other.
Read `docs/SPEC.md` before doing anything — it is the behaviour contract and the map of which
file does what.

---

## Shared rules

1. **The spec is the contract.** Behaviour lives in `docs/SPEC.md`. A change in behaviour
   updates the spec in the same commit. Never "improve" the design beyond what was asked —
   minimal by default is the point of this launcher.
2. **Stay on the session branch.** Commit to it and push only to it. Never force-push, never
   rewrite history someone else may have pulled.
3. **No secrets** in code, logs or commits.
4. **Toolchain:** JDK 17, Android SDK platform 35, Gradle via `./gradlew` (wrapper 8.11.1,
   AGP 8.7.3, Kotlin 2.1.0). Kotlin warnings are errors.
5. **Small commits with real messages:** what changed and why, in the imperative
   (`Fix popup clipping under the status bar`).

---

## Programming agent

### Inputs, in this order
1. The user's latest prompt (it wins over everything else).
2. `logs/REPORT.md` from the testing agent, then the detailed logs it points to.
3. `docs/SPEC.md`.

### Code rules (non-negotiable — the user asks why each piece exists)
- **One job per file.** The table in `docs/SPEC.md` lists every file's job; a new file gets a
  row there. If a file starts doing two things, split it.
- **No dead code**: no unused imports, parameters, members, files, resources or comments
  describing code that no longer exists. Delete instead of commenting out.
- **No duplicate logic.** If two places need it, it gets one home.
- **No magic numbers in views.** UI values live in `theme/Globals.kt`; a local constant is
  fine when it is truly local, named, and commented with what it means.
- **No hacks**: no `postDelayed` to "wait for layout", no try/catch to hide a bug, no
  reflection on framework internals.
- **Every animation uses `motion/Spring`** with a config from `SettingsRegistry` via
  `App.motion` — never `ValueAnimator`/`ObjectAnimator`/`ViewPropertyAnimator`.
- **Every colour, radius, padding, font and icon style comes from the theme** (`ThemeStore`).
  Views subscribe while attached (`themes.add/remove`) and restyle in `onTheme`.
- **Stores emit `Signal`s; views subscribe with `View.observe`** so nothing leaks past its window.
- **Settings are data**: add a `val` to `SettingsRegistry` and list it in a category — the
  view, settings search and adb CLI pick it up. Don't special-case a setting anywhere else.
- **Search categories implement `SearchProvider`**; add one to `App.search`, nothing else.
- **Low allocation per keystroke/frame**: reuse lists, buffers and rows (see `Ranker`,
  `ResultsPage`). No allocations inside `onDraw`.
- **Insets everywhere**: every layer honours `WindowInsets.safe()` (bars + cutouts) and works
  in landscape and on tablets.
- **No `R` usage in Kotlin.** Strings are inline, fonts are assets.
- **No new dependencies** without a reason written in the commit message and a row in
  `THIRD_PARTY.md` for anything shipped.

### Before handing over
1. `./gradlew assembleDebug testDebugUnitTest lintDebug` passes locally if you can run it.
   If you can't (no SDK), typecheck what you can and say so in the commit message.
2. New pure logic (matching, formatting, parsing) gets a JVM unit test in `app/src/test`.
3. If you changed behaviour the testing agent should look at, say so in the commit message
   body as `Verify: …` lines (one per thing to check). The testing agent reads those.

---

## Testing agent

Your job is to tell the truth about whether the branch builds and runs, and to make it build
and run with the **smallest possible fix**. You are not here to refactor, restyle or add
features.

### Procedure
1. **Start clean.** Delete every file in `logs/` from previous turns (`git rm -r logs/*`),
   then create `logs/` again. Old logs are history in git, not clutter in the tree.
2. **Pull the branch** and record `git log -1 --format='%H %s'` and the `Verify:` lines of
   the commits since your last report.
3. **Build and test**, each step's full output to its own log file:
   | Step | Command | Log |
   |---|---|---|
   | environment | `java -version; ./gradlew --version; sdkmanager --list_installed` | `logs/00-env.log` |
   | compile | `./gradlew assembleDebug --stacktrace` | `logs/10-build.log` |
   | unit tests | `./gradlew testDebugUnitTest` | `logs/20-test.log` (+ copy `app/build/reports/tests`) |
   | lint | `./gradlew lintDebug` | `logs/30-lint.log` (+ `app/build/reports/lint-results-debug.txt`) |
   | release | `./gradlew assembleRelease` (R8) | `logs/40-release.log` |
4. **Run it** if an emulator or device is available (`adb devices`):
   - install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
   - make it home: `adb shell cmd role add-role-holder android.app.role.HOME com.bhavyam.min`
   - start: `adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME`
   - drive it with the CLI (`scripts/min.sh`): `help`, `status`, `search chat`,
     `search chtgpt`, `list_favorites`, `add_favorite <an installed app>`,
     `remove_favorite <same>`, `open_search maps`, `close_search`, `open_settings`,
     `close_settings`, `get_setting`, `set_setting anim_speed 1.5`, `set_setting blur false`,
     and an unknown command (must answer `error: …`, not crash).
   - screenshots of home, search, settings, and the studio on each scene (home, search, menu):
     `adb exec-out screencap -p > logs/shot-<name>.png`
   - universal search: `search <query>` answers `best: <category>` first — the page the
     search screen would glide to (e.g. a contact "Aarav" beats every app for `aa`)
   - crashes and warnings: `adb logcat -d -v time > logs/50-logcat.log`, and
     `adb logcat -d -b crash > logs/51-crash.log`.
   - CLI transcript (command → answer) in `logs/60-cli.log`.
5. **Fix only what blocks** building, testing or launching: a compile error, a failing test
   whose expectation matches the spec, a crash on start. Each fix is minimal and its own
   commit (`test-agent: fix … (blocked build)`). If the right fix needs a design decision,
   don't guess — log it and leave it.
6. **Never** weaken a test or the spec to make something pass, suppress a warning or lint
   rule, or change behaviour the spec defines.
7. **Write `logs/REPORT.md`** — short, factual, in this shape:
   ```
   # Test report — <date> — <commit sha> <subject>
   Build: pass/fail · Tests: x/y · Lint: n errors, m warnings · Release: pass/fail · Ran on: <device/emulator/none>
   ## Fixed (with commit shas)
   ## Broken (what, where, log file + line, steps to reproduce)
   ## Looks wrong vs SPEC (section, observed vs expected, screenshot)
   ## Not checked (and why)
   ```
8. **Commit** `logs/` and your fixes, push the branch.

### Logging rules
- Log everything you ran, with its exit code; never trim an error out of a log.
- Prefer facts over opinions: "favourites list overlaps the clock at 600dp width
  (shot-home-tablet.png)" beats "layout looks off".
- Keep logs text (plus screenshots). No binaries over a few MB; no APKs in git.
