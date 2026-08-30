# ADR 0003: Two-level Telegram interface

- Status: Accepted
- Date: 2026-08-15

## Context

GUI and most addons need a stable, understandable interface, while advanced
addons need access to TDLib features before a semantic wrapper is designed.
Direct `ClientManager` access would bypass validation and tie every caller to a
specific schema.

## Decision

Expose two levels behind the same Policy Engine:

1. A stable semantic interface with independent SemVer, opaque handles,
   immutable models and typed errors.
2. A generated raw TDLib interface pinned to exact TDLib commit, reported
   version, schema hash and generator version.

Raw objects are immutable typed proxies with validated copy-on-write patches.
The host binds addon/account identity. No caller receives TDLib `Client` or
`ClientManager`.

## Consequences

- Semantic addons survive routine TDLib constructor changes.
- Raw addons trade immediacy for strict compatibility pins.
- Every TDLib function and sensitive field needs policy metadata.
- CI must fail when schema generation finds an unclassified surface.

## Rejected alternatives

- Semantic interface only: blocks advanced addon experimentation.
- Raw interface only: shallow wrapper, high coupling and poor permission UX.
- Free-form Lua tables: loses types, enables constructor spoofing and copies
  large update graphs.

## Verification

Contract tests run the same scenarios against fake and TDLib adapters. Schema
tests mutate every generated field and assert validation and redaction.
