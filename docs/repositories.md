# Repository composition

The production repository family is hosted under the `VibeTGram` organization:

| Repository | Canonical URL | Responsibility |
| --- | --- | --- |
| `app` | https://github.com/VibeTGram/app | Android composition root, BOMs and releases |
| `core` | https://github.com/VibeTGram/core | TDLib engine, interfaces and policy |
| `mods` | https://github.com/VibeTGram/mods | Luau runtime, SDK and package verification |
| `gui` | https://github.com/VibeTGram/gui | Replaceable Compose GUI |
| `mods-example` | https://github.com/VibeTGram/mods-example | Executable SDK examples |
| `addons-market` | https://github.com/VibeTGram/addons-market | Signed reviewed-addon records |

## Composition rules

- `core`, `mods`, and `gui` are HTTPS Git submodules of `app`.
- Each submodule is checked out at the gitlink commit recorded by the `app`
  superproject; `.gitmodules` contains no branch or floating revision.
- `gradle/upstreams.properties` records the exact commit expected for each
  submodule and is checked by the local composition validator.
- `settings.gradle.kts` includes the three checked-out repositories as Gradle
  composite builds.
- `mods-example` targets the public Mod SDK but is not embedded as a build
  submodule.
- `addons-market` is fetched as signed runtime data and is never a compile
  dependency.

The build BOM produced by CI records all six repository commits and every
external/toolchain pin for reproducibility.
