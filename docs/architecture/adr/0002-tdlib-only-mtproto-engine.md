# ADR 0002: TDLib is the sole MTProto engine

- Status: Accepted
- Date: 2026-08-15

## Context

Full feature parity and low-level addon access create pressure to invoke raw
Telegram methods. Official Android contains an internal `tgnet` implementation,
but it is not a standalone supported Android SDK. Running another MTProto stack
beside TDLib would create a second session, update stream and state store.

## Decision

TDLib exclusively owns MTProto, authorization, data-center sessions, update
recovery and Telegram storage. There is no second MTProto client and no generic
raw-MTProto addon function.

When a required Telegram RPC is absent, first pursue upstream TDLib. If the
project cannot wait, carry a minimal TDLib patch that exposes a concrete typed
function through `td_api.tl`. Generated raw interfaces, policy classification
and `schemaHash` then cover the extension.

## Consequences

- Telegram state has one owner and one ordered update stream.
- Generic MTProto experiments cannot be shipped as addons.
- Some parity work may depend on upstream or a maintained TDLib patch.
- `tgnet` remains a reference only and is not copied into the project.

## Rejected alternatives

- `tgnet` instead of TDLib: requires building the complete cache/state engine.
- `tgnet` or a third-party client beside TDLib: duplicate authorization and
  state races.
- `invokeRawMtproto`: cannot preserve TDLib state or exhaustively enforce policy.

## Verification

The dependency graph contains exactly one Telegram transport implementation.
Static checks forbid auth-key and transport handles in public interfaces.
