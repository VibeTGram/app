# Modification runtime

This directory owns the constrained Luau host seam for VibeTGram. The pinned
Luau revision is recorded in `luau.lock.json`; JIT, native code generation,
package bytecode input, and persisted bytecode are disabled by contract.

`runtime.py` contains the host lifecycle/resource policy model used by the
interface tests. The Android/native adapter must bind the same policy to one
real `lua_State` for each `(addon, account)` pair:

- `QuotaAllocator` is the custom allocator and hard memory boundary.
- `Watchdog` is the cooperative interrupt state for instruction and wall-clock
  limits.
- `AsyncQueue` is a per-instance bounded FIFO. Queued callbacks receive their
  watchdog and must reach safepoints cooperatively. Only explicitly keyed
  state-like events can coalesce; message create/edit/delete events never
  coalesce.
- `CancellationScope` is destroyed on disable/logout and clears pending work.
- `LuauHost.set_modification_mode(False)` synchronously cancels every instance,
  queue, and in-flight operation at the next VM safepoint.

The host keeps source and source digests only. `CompiledSource` intentionally
has no bytecode field, so package bytecode cannot become persisted runtime
state. Callers provide host-owned addon/account identity; addon code cannot
select or replace it.
