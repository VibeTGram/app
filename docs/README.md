# VibeTGram repository documentation

Canonical repository: https://github.com/VibeTGram/app

This index is retained in the composition root so the repository boundaries and
ownership table remain explicit after the repository split.

| Document | Owning repository |
| --- | --- |
| System architecture, build BOM and release BOM | `app` |
| Semantic/raw API contracts | `core`, with Mod facades in `mods` |
| Core interface and TDLib policy | `core` |
| Mod API, capabilities and package formats | `mods` |
| GUI contract and extension slots | `gui` |
| Reviewed addon records | `addons-market` |
| Executable tutorials | `mods-example` |

## Canonical repositories

- [`app`](https://github.com/VibeTGram/app) — composition and releases
- [`core`](https://github.com/VibeTGram/core) — Telegram engine and Core API
- [`mods`](https://github.com/VibeTGram/mods) — runtime and Mod SDK
- [`gui`](https://github.com/VibeTGram/gui) — replaceable presentation
- [`mods-example`](https://github.com/VibeTGram/mods-example) — examples
- [`addons-market`](https://github.com/VibeTGram/addons-market) — registry data

The source of truth for each normative document lives in its owning repository;
this table and links are updated when a contract moves.
