# VibeTGram

Repository: https://github.com/VibeTGram/app

VibeTGram is an unofficial Android Telegram client built on TDLib with a
replaceable Material 3 Expressive GUI and an opt-in, sandboxed Luau addon
platform.

The project is currently in the architecture and bootstrap phase. Stable
releases are intended to match the user-facing feature set of a pinned Telegram
Android revision, subject to features that are not available through the pinned
TDLib revision.

## Repository family

The project is split across the [`VibeTGram`](https://github.com/VibeTGram)
organization:

| Repository | Responsibility |
| --- | --- |
| `app` | Android composition root, submodule pins, release build and updater |
| `core` | TDLib, semantic/raw interfaces, policy, persistence and Android adapters |
| `mods` | Luau runtime, Mod API/SDK, package verification and documentation |
| `gui` | Replaceable Compose/Material 3 Expressive GUI and Mod UI renderer |
| `mods-example` | Buildable examples targeting the public Mod SDK |
| `addons-market` | Signed registry of reviewed addon source commits |

The local repository contains the initial cross-repository specification. The
`app` repository will ultimately pin `core`, `mods`, and `gui` as Git submodules
at exact commit IDs and compose them with Gradle.

## Start baseline

The following upstream revisions were read from their public branches on
2026-08-15. They are discovery pins for the initial parity audit, not automatic
floating dependencies.

| Upstream | Branch | Commit |
| --- | --- | --- |
| Telegram Android | `master` | `45ab8f4308496e1f01026a97fcdb0d58a5274474` |
| TDLib | `master` | `022d60202e446ad1287b9fb68e687c8a0760788b` |
| tgcalls | `development` | `2faee3b5524f54d56c91c2058c00e11c656a74b3` |
| Luau | `master` | `6dafc0dd9909efe534c825d1b1184644e1f7a4e4` |

Every dependency update must arrive through a reviewed pull request and update
the build BOM; offline signing later binds it into the release BOM. Branch names
above must never be used as build inputs.

## Documentation

- [System architecture](docs/architecture/system-architecture.md)
- [Two-level Core/Mod API](docs/api/two-level-api.md)
- [Architecture decision records](docs/architecture/adr/README.md)
- [Mod capability matrix](docs/modding/capability-matrix.md)
- [Addon package formats](docs/modding/package-formats.md)
- [Addon registry](docs/modding/addons-market.md)
- [Feature parity matrix](docs/feature-parity.md)
- [Implementation roadmap](docs/roadmap.md)
- [Key and signing runbook](docs/security/key-management-runbook.md)
- [Primary-source research](docs/research/primary-sources.md)

Machine-readable contracts live under [`schemas/`](schemas/).

## Product modes

VibeTGram starts as a normal Telegram client. The addon runtime, addon manager,
resource packs, manual installation, and addon UI are absent until the user
enables **Modification Mode** and accepts a 15-second risk warning. Turning the
mode off stops all addon execution immediately without deleting installed
packages or their data.

Some addon capabilities can conflict with the Telegram API Terms of Service.
They are disabled by default, labeled explicitly, and require both the global
Modification Mode gate and per-addon permission. A warning does not make those
features compliant or eliminate the risk of account restrictions or revocation
of the project's Telegram API credentials.

## Distribution and licenses

Initial distribution is through GitHub Releases only. Stable, Preview, and
Nightly use distinct application IDs, storage, FCM projects, and signing keys.

The application, core, GUI, and runtime are planned as GPL-3.0-or-later. Public
Mod SDK interfaces and examples are Apache-2.0. Documentation is CC-BY-4.0,
catalog data is CC0-1.0, and every third-party addon must declare SPDX license
identifiers for code and assets.

VibeTGram is an unofficial client and is not affiliated with Telegram. It must
use its own `api_id`, name, branding, and release credentials.
