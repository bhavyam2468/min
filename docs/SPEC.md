# min — specification and architecture

This is the contract the code is held to. When behaviour and this file disagree, one of them
is a bug: fix the code, or update this file in the same change.

## Behaviour

### Home
- Background is the `surface` token's page colour — pure black out of the box.
- Top-left: date and time side by side in small white text (`Tue, 10 November  2:25`), live,
  12/24-hour following the system. Patterns, sizes and arrangement are clock tokens.
- Favourites: a vertical list, centred vertically on the left. A fresh install picks defaults
  by name (phone, messages, browser, camera, …) and skips any that aren't installed. Persisted.
- Tap launches the app; it grows out of the row (scale-up animation) while home blurs out.
- Long press: a small popup springs out above the app (below it if there's no room) with two
  options — *App info* and *Set as favorite / Remove from favorites*. Instant and persistent.
- Long press on the clock opens settings.

### Search
- Swipe **down** on home opens search; swipe **up** in search (when its list is at the bottom)
  returns home. Both follow the finger, hand the release velocity to a bouncy spring, and can
  be caught mid-flight. Opening focuses the field and shows the keyboard; closing hides it.
- The input sits at the very bottom, right on top of the keyboard, following the keyboard's
  own animation frame by frame (`WindowInsetsAnimation`). Hint: `search`.
- Directly above the input: a tiny, dim, uppercase category label (`APPS`, `CONTACTS`,
  `SEARCH`). Above that: results, centred, best match nearest the input, scrollable. The
  stack comes from bottom gravity in a fill-viewport scroll view — no manual offsets.
- Categories are full-width pages swiped horizontally on a spring.
- Empty query: every app, most recently used nearest the bar.
- Enter runs the top result of the focused category. Tap runs a result. Long press on an app
  result opens the same popup as home.
- `CONTACTS` exists only once the contacts permission is granted (settings → permissions).
- `SEARCH` (fallback) offers "search *query* on …": the web first, then only installed apps
  that take a clean query (YouTube, Spotify, Google Maps, Play Store, Amazon, Reddit,
  Wikipedia, X, …). Enable, disable and reorder them in settings → search providers.

### Matching (apps, contacts and settings search share one matcher)
1. Case-insensitive.
2. An exact substring always wins: whole name > start of name > start of a word > anywhere.
   Within a tier, shorter names win.
3. Otherwise exactly one typo (one wrong, missing or extra letter) is forgiven, against the
   start of the name or of any word, including a prefix of it (mid-typing). Never two:
   `chastgpt`/`chtgpt` find ChatGPT, `cshtgpt`/`ctgpt` don't. Typo matches rank below all
   substring matches. Typos need ≥ 3 typed letters.
4. No loose subsequence matching (`yt` does not find YouTube).
5. Results never flicker to empty while a word is being typed.

Note: `gmaps` matches Google Maps — it is one inserted letter before the word "maps".

### Settings
- An overlay over home: fades and rises in on a spring, reverses on close. Back arrow and a
  lowercase title. Category pages slide in from the right.
- Root: `display`, `customisation` (opens the studio), `search providers`, `animations`,
  `permissions`, plus a search field over every setting.
- Data-driven: every setting is one entry in `SettingsRegistry`. The view, settings search
  and the adb CLI all read that list.

### Customisation (token studio)
- Direct manipulation, no sliders. Where the finger lands on the specimen card decides what
  it edits; a tip trails the finger with the name and value, a highlight marks the spot.
  - card corners → card radius · card surface → ↕ light/dark, ↔ grey tint, pinch → scale
  - button corners → button radius · left/right edges → horizontal padding · top/bottom
    edges → vertical padding (density)
  - button body → hold and glide: ↔ hue, ↕ vividness; tap cycles the button type
    (solid, soft, outline, ghost, gradient)
  - icon → tap cycles style, drag for stroke · "Aa" → tap next family, drag for weight
  - strip under the card → elevation
  - date / time → ↕ size, ↔ rearrange (date·time, time·date, stacked), tap → edit pattern
- Toolbar: back, undo, redo, shuffle, reset, copy tokens as JSON.
- Tokens drive the launcher visibly: primary → home favourites, accent → search results,
  neutral → the popup, surface → page/card colours (dark ⇄ light), plus radius, padding,
  type, icons, elevation and scale everywhere.

### Clock patterns (KWGT-style)
`d 2 · dd 02` day · `D Fri · DD Friday` · `m 5 · mm 05` month · `M Jan · MM January` ·
`y 8 · yy 08 · yyyy 2008` · `h`/`hh` hour (system 12/24) · `H`/`HH` 24-hour · `i`/`ii`
minutes · `s`/`ss` seconds · `a pm` / `A PM` (empty in 24-hour) · `'text'` literal.

### Launch feel and lifecycle
- Returning to the launcher (from an app, the lock screen, screen-on) it comes into focus:
  blur and a slight zoom settle on a spring. Launching an app does the reverse.
- On stop the launcher resets to a clean home: search closed, query cleared, popup gone,
  overlays closed. The home button while already home closes whatever is open.

### adb control
Exported receiver guarded by `android.permission.DUMP` (adb shell has it, apps can't get it).
One string extra `cmd`; the answer is the broadcast's result data; runs synchronously on the
main thread; errors are answers (`error: …`), never crashes.

```
adb shell am broadcast -n com.bhavyam.min/.cli.CliReceiver -a com.bhavyam.min.CLI --es cmd "'add_favorite Google Maps'"
scripts/min.sh add_favorite Google Maps          # same thing, prints only the answer
```
The double quoting matters: adb re-joins arguments on the device, so without the inner
single quotes a command is cut at its first space.

Commands: `help`, `status`, `search <q>`, `open_app <name|package>`, `list_favorites`,
`add_favorite <name|package>`, `remove_favorite <name|package>`, `open_settings`,
`close_settings`, `open_search [query]`, `close_search`, `get_setting [key]`,
`set_setting <key> <value>`.

### Platform
- minSdk 33, target 35. Edge to edge; every layer honours system bars and display cutouts.
- Tablets and landscape: content columns cap their width and type scales up on large
  screens instead of stretching a phone layout.
- System "remove animations" makes every spring jump.

## Architecture

One activity, one view tree, no fragments, no AndroidX, no runtime dependencies.

```
App (process state) ── Prefs · Motion · ThemeStore · AppIndex · Favorites · providers · SearchEngine
 └─ MainActivity (system jobs) ── HomeRoot (window content)
       ├─ HomeLayer   clock + favourites
       ├─ SearchLayer pages of ResultsPage + input
       ├─ SettingsView ┐ OverlayLayer
       ├─ StudioView   ┘
       └─ ContextPopup
 CliReceiver ── Commands ── App.ui (LauncherUi = MainActivity)
```

| File | Its one job |
|---|---|
| `App.kt` | Owns process-wide stores so the activity and the adb receiver share them; wires data changes to search. |
| `MainActivity.kt` | System-facing work: launching, permissions, home role, back, status bar, lifecycle. |
| `Signal.kt` | The single observer primitive (+ view-lifetime subscription). |
| `apps/AppEntry.kt` | One launchable activity (label, component, user, recency). |
| `apps/AppIndex.kt` | All launchable apps, loaded off the main thread, refreshed on package changes. |
| `apps/LaunchHistory.kt` | Last-launch times, for recency sorting. |
| `apps/Favorites.kt` | The pinned list: seeding, add/remove, persistence. |
| `search/Fuzzy.kt` | The matching rules and an allocation-free ranker. |
| `search/SearchProvider.kt` | The plug-in contract for a category (+ `Launcher`, `SearchItem`, `Query`). |
| `search/AppsProvider.kt`, `ContactsProvider.kt`, `FallbackProvider.kt` | The three categories. |
| `search/QueryTargets.kt` | Where a fallback search can go and the intent for each. |
| `search/SearchEngine.kt` | Runs a query through every provider into reusable pages. |
| `settings/Setting.kt` | Setting and entry types; the `SettingsHost` contract. |
| `settings/SettingsRegistry.kt` | Every setting, as data. |
| `settings/Prefs.kt` | Typed storage keyed by registry entries; change observation. |
| `settings/SettingsView.kt` | The settings overlay, built from the registry. |
| `settings/SettingRows.kt` | One row type per setting type. |
| `settings/ProviderOrderView.kt` | Drag-to-reorder list of fallback destinations. |
| `studio/StudioView.kt` | The token studio: gestures, history, toolbar. |
| `studio/Zones.kt` | What each spot edits and how a drag maps to tokens. |
| `studio/SpecimenCard.kt` | The miniature launcher being edited. |
| `studio/Tip.kt`, `Highlight.kt`, `Ambient.kt` | Finger tip, spot marker, coloured backdrop. |
| `studio/FormatEditor.kt` | Clock pattern editing with live token chips. |
| `studio/History.kt` | Undo/redo stack. |
| `theme/Globals.kt` | Every UI variable: default tokens, ranges, metrics, type sizes, springs. |
| `theme/Tokens.kt` | The editable design tokens (immutable). |
| `theme/Theme.kt` | Tokens → colours, typefaces (token-studio's derive). |
| `theme/Oklch.kt`, `Fonts.kt` | Colour space conversion; variable-font instances. |
| `theme/ThemeStore.kt` | Live tokens, once-per-frame publishing, persistence. |
| `motion/Spring.kt` | The damped spring every animation uses. |
| `motion/Motion.kt` | Spring configs from settings × global speed. |
| `home/HomeRoot.kt` | Stacks the layers; home⇄search gesture; blur-to-focus; back. |
| `home/HomeLayer.kt`, `ClockView.kt`, `ClockFormat.kt`, `ContextPopup.kt` | Home screen pieces. |
| `ui/*` | Reusable themed views (text, buttons, icons, cards, controls), pager, IME follower, list animation, overlay base. |
| `cli/*` | adb receiver, command table, the UI contract the CLI drives. |
