# VibeTGram app

Android composition root, exact-source composition, reproducible builds,
release BOMs, update channels, and updater.

Canonical repository: https://github.com/VibeTGram/app

`app` composes the lower-level repositories through HTTPS Git submodules pinned
to exact commits. Gradle includes those checked-out repositories as composite
builds; it never consumes floating branch or tag dependencies.

| Repository | Role | Build relationship |
| --- | --- | --- |
| `core` | Telegram engine and Core API | HTTPS submodule + Gradle build |
| `mods` | Luau runtime and Mod SDK | HTTPS submodule + Gradle build |
| `gui` | Compose presentation | HTTPS submodule + Gradle build |
| `mods-example` | Executable SDK examples | Separate repository; CI target |
| `addons-market` | Signed addon registry data | Runtime HTTPS source, never a compile dependency |

License: GPL-3.0-or-later. See [LICENSE](LICENSE).
