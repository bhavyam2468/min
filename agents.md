# Agent playbook

This repository is a dependency-free Android launcher. The product contract in the user's prompt is the source of truth; preserve its deliberate black-and-white minimalism and do not add UI, dependencies, telemetry, or permissions beyond a launcher feature's direct need.

## Shared rules

- Work only on the current Arena branch. Before any change, inspect `git status --short --branch`, the user prompt, and the latest current-turn test log.
- Use Java and Android framework APIs only. Do not add AndroidX, Material, Compose, third-party libraries, generated UI frameworks, analytics, or network features.
- `UiTokens.java` is the one place for visual tokens and animation constants. Do not scatter literal UI colours, dimensions, radii, typography, or timings through views.
- Keep ownership clear: index data in `AppIndex`, matching in `FuzzyMatcher`, provider lookup in providers, persistence in `SettingsStore`, declarations in `SettingsRegistry`, and rendering in their dedicated views.
- Every command and interaction must fail safely. The exported command receiver must return a readable error rather than throw.
- Maintain immersive/inset behavior in portrait, landscape, tablets, IME transitions, and display cutouts. Test without assuming a phone-shaped screen.
- Do not commit build directories, APKs, keystores, local SDK files, downloaded tools, screenshots, or logs. `.agent-logs/` is intentionally ignored.

## Programming agent

1. Read the newest `.agent-logs/current-test.md` before coding. Treat unresolved failures as first priority.
2. Translate the user's requested behavior into the smallest focused change. Avoid unrelated refactors and avoid speculative improvements.
3. Update code and documentation together when a command, setting, supported provider, or user-visible behavior changes.
4. Run cheap static checks available locally (imports, source search, manifest checks) before handing off. If a full Android build is unavailable, say exactly why in the handoff note; never claim it passed.
5. Review `git diff --check` and `git diff` at the end. Keep production files free of debugging logs and dead code.

## Testing agent

1. Start clean: `git fetch origin`, check out the assigned/current branch without changing its name, and record the exact commit SHA. Delete stale files inside `.agent-logs/` before each run; never delete source files or Git metadata.
2. Create `.agent-logs/current-test.md` containing UTC timestamp, branch, SHA, environment (JDK/Gradle/SDK/emulator), commands, complete outcomes, and concise failure excerpts. Keep previous-turn logs out of the working tree; logs are ephemeral and ignored.
3. Download dependencies/tools only through normal Gradle/SDK mechanisms outside this repository. Never vendor caches, APKs, or credentials into the checkout.
4. Run `./gradlew --no-daemon clean assembleDebug` (or an explicitly recorded equivalent) and then static checks. On an emulator/device, install the debug APK and test: launcher HOME intent, rotation and a tablet/landscape window, IME search, back ordering, favorite persistence, settings persistence, contacts permission denial/grant behavior, package-index refresh, and every documented ADB command.
5. For ADB command tests use `adb shell am broadcast -a dev.min.launcher.COMMAND --es cmd "..."`; record returned broadcast data. Quote commands with spaces exactly as documented.
6. Debug only enough to make an observed failure reproducible and actionable. Fix only obvious test harness/environment faults unless assigned as a programming task. Report product bugs with reproduction steps and logs; do not silently redesign the product.
7. Finish with `git status --short`, log it, and leave no emulator artifacts or logs tracked.
