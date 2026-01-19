# Repository Guidelines

## Project Structure & Module Organization
- Multi-module Gradle (Kotlin) project. Top-level modules are declared in `settings.gradle.kts`.
- Core libraries live under `jetwhale-protocol`, `jetwhale-agent-sdk`, `jetwhale-agent-runtime`, and `jetwhale-host-sdk`.
- Host application code is under `jetwhale-host/` (desktop Compose app with features in `jetwhale-host/feature/*` and shared layers in `jetwhale-host/core/*`).
- Example plugins are in `jetwhale-plugins/example/*`.
- Demo apps live in `demo/` (`demo/desktop`, `demo/android`, `demo/shared`).
- Tests appear alongside modules (e.g., `jetwhale-host/app/src/test` or `jetwhale-agent-runtime/src/commonTest`).

## Build, Test, and Development Commands
- `./gradlew build` — builds all modules.
- `./gradlew test` — runs JVM/unit tests across modules.
- `./gradlew :jetwhale-host:app:run` — runs the desktop host app.
- `./gradlew :demo:desktop:run` — runs the desktop demo.
- `./gradlew :demo:android:assembleDebug` — builds the Android demo APK.
- Use `./gradlew tasks` for module-specific tasks.

## Coding Style & Naming Conventions
- Kotlin/JVM toolchain is set to Java 17 (see `gradle-conventions`).
- `.editorconfig` sets `insert_final_newline = true` and disables max line length checks for `*.kt`/`*.kts`.
- Follow Kotlin idioms and keep naming consistent with surrounding code. Modules and packages use `com.kitakkun.jetwhale.*`.

## Testing Guidelines
- Tests use `kotlin.test` (see module dependencies).
- Place JVM tests under `src/test/kotlin` and multiplatform tests under `src/commonTest/kotlin`.
- Name tests after the unit under test (e.g., `JetWhaleWebSocketServerTest`).

## Commit & Pull Request Guidelines
- Commit messages follow a Conventional Commits style (e.g., `feat:`, `fix:`, `chore:`, `refactor:`) with optional scopes like `fix(demo):`.
- Keep PRs focused, include a short description of changes, and note how they were tested.
- For UI changes in the host or demo apps, include screenshots or a short recording.

## Configuration Tips
- Gradle configuration cache is enabled in `gradle.properties`; avoid introducing tasks that break caching without justification.
