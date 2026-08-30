# Architecture decision records

All records are accepted for bootstrap. A change requires a superseding ADR;
history is not rewritten.

| ADR | Decision |
| --- | --- |
| [0001](0001-multi-repository-composition.md) | Six repositories composed by exact Git submodule pins |
| [0002](0002-tdlib-only-mtproto-engine.md) | TDLib is the sole MTProto engine |
| [0003](0003-two-level-telegram-interface.md) | Stable semantic and pinned raw TDLib interfaces |
| [0004](0004-in-process-luau-sandbox.md) | Luau runs in-process under per-addon/account isolation |
| [0005](0005-replaceable-gui.md) | GUI is replaceable; addon UI stays declarative |
| [0006](0006-addon-packages-and-registry.md) | Source-only packages and commit-pinned signed registry |
| [0007](0007-product-modes-and-tos-sensitive-addons.md) | Addons are opt-in and ToS-sensitive capabilities are explicitly gated |
| [0008](0008-release-and-key-trust.md) | GitHub-only releases with separated channels and offline trust roots |
| [0009](0009-account-and-data-isolation.md) | Per-account engines, keys, addon states and data |

Use the vocabulary **module**, **interface**, **implementation**, **seam**, and
**adapter** consistently in future records.
