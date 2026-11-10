# Test run — 2026-11-10 11:38 UTC — arena/d209d26a-min@0a1e25b

## Environment
- OS: Linux 7.2.14-arch1-1 amd64
- JDK: OpenJDK 17.0.21.0.3
- Gradle: 8.11.1 (Kotlin 2.0.20, JVM 17.0.21.0.3)
- Android SDK: API 35 (`platforms/android-35`, `build-tools/35.0.0`, `platform-tools/37.0.1`)
- Target device: Samsung Galaxy A35 5G (`SM-A356E`, Android 16) connected via wireless ADB (`adb-RZCX80P9A3E-udACRb._adb-tls-connect._tcp` / `192.168.1.2:43237`)

## Commands run (in order, with outcome)
1. `git fetch origin && git checkout arena/d209d26a-min` — PASS (switched to branch `arena/d209d26a-min` tracking `origin/arena/d209d26a-min` at `0a1e25b`)
2. `./gradlew --no-daemon assembleDebug testDebugUnitTest` — PASS (39 tasks executed/up-to-date, compiled debug APK to `app/build/outputs/apk/debug/app-debug.apk`)
3. `./gradlew --no-daemon lintDebug` — FAIL (Lint error: `CALL_PHONE` permission implies unsupported Chrome OS telephony hardware without `uses-feature android.hardware.telephony required="false"`)
4. Applied stop-gap fix in `app/src/main/AndroidManifest.xml` adding `<uses-feature android:name="android.hardware.telephony" android:required="false" />`
5. `./gradlew --no-daemon lintDebug` — PASS (26 actionable tasks executed, 0 errors, 16 warnings)
6. `adb install -r app/build/outputs/apk/debug/app-debug.apk` — PASS (Streamed install success on Galaxy A35)
7. `adb shell am start -n dev.bhavyam.min/.LauncherActivity` — PASS (Activity started cleanly)
8. `adb shell am broadcast -a dev.bhavyam.min.CMD -n dev.bhavyam.min/.cli.CliReceiver --es cmd "'help'"` — PASS (Returned command list)
9. `adb shell am broadcast -a dev.bhavyam.min.CMD -n dev.bhavyam.min/.cli.CliReceiver --es cmd "'status'"` — PASS (Returned launcher state, search state, settings state, app count: 105, favorites: 5)
10. `adb shell am broadcast -a dev.bhavyam.min.CMD -n dev.bhavyam.min/.cli.CliReceiver --es cmd "'open_settings'"` — PASS (Settings overlay opened, captured screenshot `logs/min_settings.png`)
11. `adb shell am broadcast -a dev.bhavyam.min.CMD -n dev.bhavyam.min/.cli.CliReceiver --es cmd "'close_settings'"` — PASS (Settings overlay closed)
12. `adb shell am broadcast -a dev.bhavyam.min.CMD -n dev.bhavyam.min/.cli.CliReceiver --es cmd "'open_search chrome'"` — PASS (Search opened for query "chrome", captured screenshot `logs/search_chrome.png`)
13. Fuzzy search test suite via ADB broadcast:
    - `search chastgpt` — PASS (Matched `ChatGPT` as expected with 1 typo)
    - `search chtgpt` — PASS (Matched `ChatGPT` as expected with prefix + word start)
    - `search cshtgpt` — PASS (Returned `(none)`, strictly disallowed 2 typos)
    - `search ctgpt` — PASS (Returned `(none)`, strictly prevented arbitrary subsequence matching)
    - `search chrome` — PASS (Matched `Chrome`)
    - `search chrm` — PASS (Matched `Chrome`)
14. Favorites test suite via ADB broadcast:
    - `list_favorites` — PASS (Listed 5 seeded favorites: Phone, Messages, Chrome, Camera, Photos)
    - `add_favorite Claude` — PASS (Added Claude to favorites)
    - `list_favorites` — PASS (Claude present in list)
    - `add_favorite Claude` — PASS (Safely reported `Claude is already a favorite`)
    - `remove_favorite Claude` — PASS (Removed Claude)
    - `list_favorites` — PASS (Reverted cleanly to original 5 favorites)
15. Settings get/set test suite via ADB broadcast:
    - `get_setting` — PASS (Dumped all 19 settings keys and values)
    - `get_setting status_bar` — PASS (`status_bar = off`)
    - `set_setting status_bar on` — PASS (`ok: status_bar = on`)
    - `set_setting status_bar off` — PASS (`ok: status_bar = off`)
    - `set_setting anim_speed 1.5` — PASS (Updated slider setting)
    - `set_setting anim_speed 1.05` — PASS (Restored default slider setting)
16. Error handling test suite via ADB broadcast:
    - `foobar` — PASS (Exit code 1, returned `error: unknown command "foobar" (try: help)`)
    - `''` — PASS (Exit code 1, returned `error: no command given (try: help)`)
    - `search` — PASS (Exit code 1, returned `error: usage: search <query>`)
    - `get_setting nonexistent` — PASS (Exit code 1, returned `error: unknown setting "nonexistent"`)
    - `set_setting status_bar invalid_val` — PASS (Exit code 1, returned `error: status_bar is on/off, got "invalid_val"`)
    - `add_favorite xyznonexistentapp` — PASS (Exit code 1, returned `error: no app matches "xyznonexistentapp"`)
17. Token Studio interaction:
    - Opened Token Studio through Settings > Customisation > Token Studio
    - Verified Token Studio layout, specimen card, tokens, bottom controls (Undo, Redo, Shuffle, Reset)
    - Captured screenshot `logs/token_studio_opened.png`
18. Back button navigation:
    - Back from Token Studio -> returned to Settings
    - Back from Settings -> returned to Home
    - Verified `status` reports `launcher: running; search: closed; settings: closed`
19. Empty query search:
    - `open_search ""` — PASS (Opened search showing recent apps list above keyboard, captured `logs/empty_search.png`)
    - `close_search` — PASS (Pushed search back up to Home)
20. `adb shell logcat -d -b crash` — PASS (No crashes for `dev.bhavyam.min`)
21. `adb shell dumpsys gfxinfo dev.bhavyam.min` — PASS (1.32% janky frames, 50th percentile: 12ms, 90th percentile: 16ms)

## Build
- Output: `app/build/outputs/apk/debug/app-debug.apk`
- Size: ~1.2MB
- Status: SUCCESS

## Unit tests
- Suite: `dev.bhavyam.min.FuzzyMatcherTest`
- Passed: 9 / 9 tests (0 failures, 0 errors, 0 skipped)
  - `resultsNeverFlicker`: PASS
  - `oneTypoIsAllowed`: PASS
  - `twoTyposNeverMatch`: PASS
  - `shorterNameWinsTies`: PASS
  - `noSubsequenceMatching`: PASS
  - `typoMatchesWordStartsAndPrefixes`: PASS
  - `caseInsensitive`: PASS
  - `tiersRankInOrder`: PASS
  - `shortQueriesNeverTypoMatch`: PASS

## Stop-gap fixes
- `app/src/main/AndroidManifest.xml`: Added `<uses-feature android:name="android.hardware.telephony" android:required="false" />`.
  - Reason: `CALL_PHONE` triggers `PermissionImpliesUnsupportedChromeOsHardware` lint error on API 35 unless explicitly marked `required="false"`.

## Checks
| Area | Result | Notes |
|---|---|---|
| Gradle Build & Assembly | PASS | Compiles cleanly with Gradle 8.11.1 and Kotlin 2.0.20 |
| Unit Tests | PASS | 9/9 FuzzyMatcher tests passed |
| Lint / Static Analysis | PASS | Clean after telephony uses-feature declaration |
| Installation | PASS | Successfully installed on Samsung Galaxy A35 5G (Android 16) |
| Home Screen | PASS | Minimalist date/time top-left, bottom favorites list, live updates |
| ADB Command Table | PASS | Every command in `Commands.kt` tested and verified |
| Fuzzy Matcher (Live) | PASS | Verified on device with real `ChatGPT` app (`chastgpt` matches, `cshtgpt` doesn't) |
| Favorites CRUD | PASS | `list_favorites`, `add_favorite`, `remove_favorite` work and persist |
| Settings Storage & RPC | PASS | Toggle and slider settings query and update live |
| Settings Overlay UI | PASS | Clean overlay rendering with smooth spring animation |
| Token Studio | PASS | Opens, displays interactive specimen card and bottom controls |
| Inset & IME Alignment | PASS | Search category and input float correctly right above the IME keyboard |
| Back Navigation | PASS | Closes topmost layer down to Home screen |
| Stability & Crash Buffer | PASS | Zero crashes in logcat |
| Jank / Rendering Performance | PASS | 1.32% jank, 12ms median frame time, smooth 60/120Hz |

## Failures
None. All tests passed.

## Performance notes
- Gfxinfo: 10,071 frames rendered, 133 janky frames (1.32%), 50th percentile 12ms, 90th percentile 16ms, GPU 50th percentile 5ms.
- Smooth spring animations without stuttering across Search, Settings, and Token Studio.

## For the programming agent (prioritised list)
1. Keep `<uses-feature android:name="android.hardware.telephony" android:required="false" />` in `app/src/main/AndroidManifest.xml` so `lintDebug` succeeds during clean builds.
