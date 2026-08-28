# ADR 0007: Product modes and ToS-sensitive addons

- Status: Accepted
- Date: 2026-08-15

## Context

The application should behave as an ordinary Telegram client by default. The
addon system can enable behaviors that conflict with Telegram API Terms. The
product decision is to allow those behaviors after prominent informed consent,
despite the remaining project and account risk.

## Decision

Modification Mode is a master gate. Every explicit `off -> on` transition shows
a full risk table for 15 seconds. Off stops all addon code, hooks and journal
capture immediately while retaining packages and data. Catalog and eligible
addon updates may continue in the background.

ToS-sensitive addons are allowed in the official registry with a dedicated
label and hidden section. They still require per-addon capabilities and, when
critical, per-operation confirmation. `verified` never means ToS-compliant.

Developer Mode is a second temporary gate unlocked by seven taps and another
15-second warning. It permits unsigned/local packages, hot reload and forced raw
compatibility, but no sandbox escape. It resets when Modification Mode turns
off.

## Consequences

- The standard experience remains free of addon behavior.
- Warnings and consent do not eliminate Telegram's ability to restrict accounts
  or revoke the shared `api_id`.
- Store moderation must distinguish safety review from ToS compliance.
- UI copy and issue templates must not imply that the gate is a legal bypass.

## Rejected alternatives

- Permanently forbid ToS-sensitive operations: safer recommendation rejected by
  product decision.
- Enable addons by default: violates the ordinary-client requirement.
- One global permission for every addon: unacceptable privilege aggregation.

## Verification

Mode-transition tests assert that no Lua state or reliable journal activity
exists while off and that every off/on transition shows the full timer.
