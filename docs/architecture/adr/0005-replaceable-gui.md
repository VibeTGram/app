# ADR 0005: Replaceable GUI and declarative addon UI

- Status: Accepted
- Date: 2026-08-15

## Context

The default GUI uses Material 3 Expressive, but users and forks must be able to
replace its design without forking Telegram logic. Runtime addons also need UI
extension points without gaining Android object access.

## Decision

`gui` is a replaceable Compose-first implementation behind a versioned
`GuiEntryPoint` and typed route contract. Core owns use cases; GUI owns screen
state holders; app owns composition.

Every compatible GUI implements the minimum Mod UI Contract. Addons return a
validated declarative tree for approved slots and may define their own routes.
They cannot receive Compose lambdas, `View`, `Context`, Activity or navigation
controller objects, and cannot replace complete first-party screens at runtime.

## Consequences

- A fork swaps `gui` without altering core or Mod Runtime.
- The Mod UI schema must evolve compatibly and expose feature flags for optional
  slots.
- Complex custom runtime rendering is intentionally limited.
- Accessibility validation is centralized in the GUI renderer.

## Rejected alternatives

- UI inside core: destroys replaceability and test locality.
- Direct Compose access for Lua: cannot sandbox arbitrary JVM calls.
- Runtime replacement of first-party screens: unpredictable navigation,
  performance and recovery.

## Verification

Build the application against the standard GUI and a minimal test GUI adapter.
Render every Mod UI node under font scaling, RTL, TalkBack and reduced motion.
