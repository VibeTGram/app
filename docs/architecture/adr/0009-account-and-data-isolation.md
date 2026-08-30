# ADR 0009: Account and data isolation

- Status: Accepted
- Date: 2026-08-15

## Context

TDLib, addons, reliable events, Mini Apps and permissions all process sensitive
data for multiple Telegram accounts. A global addon state with a caller-supplied
account ID is easy to misuse and difficult to revoke safely.

## Decision

Each account owns a TDLib client, directories, random data key, database,
queues, WebView profile/data-directory suffix and lifecycle context. Public
callers use opaque `AccountHandle` values. When AndroidX WebKit
`MULTI_PROFILE` is unavailable, switching the WebView account requires a
process restart so `WebView.setDataDirectorySuffix` is set before any WebView;
the startup broker runs before any `android.webkit`/AndroidX WebKit access.
Cookie deletion alone is not an isolation boundary.

Each `(addon, account)` owns a separate Luau state, grants, storage, reliable
journal and cookie namespace. The host binds identity and does not accept an
account ID asserted by Lua. Cross-account access is absent from the initial
interface.

Keys are wrapped by Android Keystore and data is unavailable until first device
unlock after reboot. Logout deletes the account's key and all account-specific
Telegram/addon/WebView data after confirmation. Mini Apps remain disabled while
WebView cleanup is incomplete.

## Consequences

- Account faults and addon revocation are local.
- Multi-account execution costs additional memory, managed through warm/cold
  account contexts rather than a fixed account limit.
- Cross-account addons require a future explicit ADR and capability.
- Android Backup cannot restore active sessions or addon grants.
- No global Lua state exists; host-rendered safe settings do not create a
  cross-account execution principal.

## Rejected alternatives

- One global addon state with `accountId` parameters: identity confusion and
  accidental data crossover.
- One shared account encryption key: broadens compromise and prevents targeted
  deletion.

## Verification

Property tests generate multiple accounts and assert that handles, grants,
storage keys, journals, cookies, WebView suffixes and updates never cross
contexts. Process-death and account-switch tests assert that a suffix mismatch
fails closed before WebView initialization.
