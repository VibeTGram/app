# ADR 0004: In-process Luau sandbox

- Status: Accepted
- Date: 2026-08-15

## Context

Addons need fast synchronous update hooks and declarative GUI callbacks. A
separate Android process improves crash isolation but adds IPC latency, object
serialization and lifecycle complexity. In-process execution increases the
impact of runtime bugs and resource abuse.

## Decision

Embed the Luau interpreter in the main Android process without JIT or native
code generation. Packages contain source only; bytecode is neither accepted nor
persisted.

Each `(addon, account)` receives a distinct `lua_State`, allocator quota,
interrupt watchdog, async FIFO and cancellation scope. The host exposes only the
VibeTGram bridge. Java, JNI, native loading, raw files, process execution,
sockets and direct Android handles are absent.

Sync hooks cannot yield or perform I/O. Async hooks use cancellable coroutines
off the ordered update pipeline. Crash-loop metadata identifies and disables
only the offending addon instance on next launch.

## Consequences

- Synchronous mutation can meet a tight latency budget.
- A Luau host/runtime memory-safety defect can still crash the process.
- Strict allocator, watchdog, bridge validation and safe-mode recovery are
  mandatory rather than optional hardening.
- Process isolation remains a possible superseding design after profiling.

## Rejected alternatives

- Lua 5.4: weaker fit for the chosen sandbox/tooling model.
- JIT: less predictable interruption and executable memory behavior.
- One global Lua state: cross-addon/account contamination.
- Separate process for the first version: rejected by product decision.

## Verification

Fuzz the bridge and package parser; stress allocator exhaustion, infinite loops,
deep recursion, cancellation, queue overflow and process restart recovery.
