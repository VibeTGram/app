"""Small, fail-closed model of the VibeTGram Luau host boundary.

The production Android adapter supplies the Luau C API at this boundary.  This
module deliberately keeps the host policy independent of that adapter so quota,
lifecycle, and isolation behavior can be tested without a device or native
library.  Source is retained for restart; compiled bytecode is never part of
this API or an instance's persisted state.
"""

from __future__ import annotations

import json
import math
import re
import threading
import time
from collections import deque
from collections.abc import Callable
from dataclasses import dataclass
from hashlib import sha256
from pathlib import Path
from typing import TypeVar
from uuid import uuid4

# The Luau source pin from the repository's bootstrap BOM.
LUAU_COMMIT = "6dafc0dd9909efe534c825d1b1184644e1f7a4e4"
LUAU_JIT_ENABLED = False
LUAU_NATIVE_CODEGEN_ENABLED = False


class HostError(RuntimeError):
    """Base class for constrained-host failures."""


class SourceRejected(HostError, ValueError):
    """The package did not provide valid Luau source text."""


class MemoryLimitExceeded(HostError):
    """The instance allocator would exceed its hard quota."""


class ExecutionCancelled(HostError):
    """Execution was cancelled by lifecycle or policy state."""


class ExecutionLimitExceeded(HostError):
    """The instruction or wall-clock watchdog fired."""


class AsyncQueueFull(HostError):
    """The per-instance asynchronous FIFO is at capacity."""


@dataclass(frozen=True)
class HostLimits:
    """Resource limits applied independently to every addon/account state."""

    memory_bytes: int = 4 * 1024 * 1024
    instruction_limit: int = 250_000
    wall_time_seconds: float = 0.050
    async_queue_items: int = 128
    async_queue_bytes: int = 256 * 1024

    def __post_init__(self) -> None:
        integer_limits = {
            "memory_bytes": self.memory_bytes,
            "instruction_limit": self.instruction_limit,
            "async_queue_items": self.async_queue_items,
            "async_queue_bytes": self.async_queue_bytes,
        }
        if any(isinstance(value, bool) or not isinstance(value, int) for value in integer_limits.values()):
            raise ValueError("integer resource limits must be integers")
        if (
            isinstance(self.wall_time_seconds, bool)
            or not isinstance(self.wall_time_seconds, (int, float))
            or not math.isfinite(self.wall_time_seconds)
        ):
            raise ValueError("wall_time_seconds must be a finite number")
        if self.memory_bytes <= 0:
            raise ValueError("memory_bytes must be positive")
        if self.instruction_limit <= 0:
            raise ValueError("instruction_limit must be positive")
        if self.wall_time_seconds <= 0:
            raise ValueError("wall_time_seconds must be positive")
        if self.async_queue_items <= 0:
            raise ValueError("async_queue_items must be positive")
        if self.async_queue_bytes <= 0:
            raise ValueError("async_queue_bytes must be positive")


@dataclass(frozen=True)
class Allocation:
    """An opaque allocation token returned by the custom allocator."""

    token: str
    size: int


class QuotaAllocator:
    """Thread-safe hard-limit allocator used by one VM state."""

    def __init__(self, limit_bytes: int) -> None:
        if isinstance(limit_bytes, bool) or not isinstance(limit_bytes, int) or limit_bytes <= 0:
            raise ValueError("limit_bytes must be a positive integer")
        self.limit_bytes = limit_bytes
        self._used_bytes = 0
        self._allocations: dict[str, int] = {}
        self._closed = False
        self._lock = threading.Lock()

    @property
    def used_bytes(self) -> int:
        with self._lock:
            return self._used_bytes

    def allocate(self, size: int) -> Allocation:
        self._validate_size(size)
        with self._lock:
            self._ensure_open()
            self._reserve(size)
            token = uuid4().hex
            self._allocations[token] = size
            return Allocation(token, size)

    def reallocate(self, allocation: Allocation, size: int) -> Allocation:
        self._validate_size(size)
        with self._lock:
            self._ensure_open()
            current = self._allocations.get(allocation.token)
            if current is None or current != allocation.size:
                raise ValueError("unknown or stale allocation")
            delta = size - current
            if delta > 0 and self._used_bytes + delta > self.limit_bytes:
                raise MemoryLimitExceeded(
                    f"memory quota exceeded: {self._used_bytes + delta} > {self.limit_bytes} bytes"
                )
            self._used_bytes += delta
            self._allocations[allocation.token] = size
            return Allocation(allocation.token, size)

    def free(self, allocation: Allocation) -> None:
        with self._lock:
            self._ensure_open()
            current = self._allocations.pop(allocation.token, None)
            if current is None or current != allocation.size:
                raise ValueError("unknown or stale allocation")
            self._used_bytes -= current

    def clear(self) -> None:
        """Release every allocation when its VM state is destroyed."""
        with self._lock:
            self._closed = True
            self._allocations.clear()
            self._used_bytes = 0

    def _reserve(self, size: int) -> None:
        if self._used_bytes + size > self.limit_bytes:
            raise MemoryLimitExceeded(
                f"memory quota exceeded: {self._used_bytes + size} > {self.limit_bytes} bytes"
            )
        self._used_bytes += size

    @staticmethod
    def _validate_size(size: int) -> None:
        if isinstance(size, bool) or not isinstance(size, int) or size < 0:
            raise ValueError("allocation size must be a non-negative integer")

    def _ensure_open(self) -> None:
        if self._closed:
            raise MemoryLimitExceeded("allocator is closed")


class CancellationScope:
    """A monotonic cancellation token shared by sync and async work."""

    def __init__(self) -> None:
        self._event = threading.Event()
        self._reason = "cancelled"
        self._lock = threading.Lock()

    @property
    def cancelled(self) -> bool:
        return self._event.is_set()

    @property
    def reason(self) -> str:
        with self._lock:
            return self._reason

    def cancel(self, reason: str) -> None:
        with self._lock:
            if not self._event.is_set():
                self._reason = reason
                self._event.set()

    def check(self) -> None:
        if self.cancelled:
            raise ExecutionCancelled(self.reason)


class Watchdog:
    """Cooperative Luau interrupt state checked at VM safepoints."""

    def __init__(self, scope: CancellationScope, limit: HostLimits) -> None:
        self._scope = scope
        self._limit = limit
        self._started_at = time.monotonic()
        self.instructions = 0

    def tick(self, instructions: int = 1) -> None:
        if isinstance(instructions, bool) or not isinstance(instructions, int) or instructions < 0:
            raise ValueError("instruction count must be a non-negative integer")
        self.instructions += instructions
        self.check()

    def check(self) -> None:
        self._scope.check()
        if self.instructions > self._limit.instruction_limit:
            raise ExecutionLimitExceeded("instruction watchdog exceeded")
        if time.monotonic() - self._started_at > self._limit.wall_time_seconds:
            raise ExecutionLimitExceeded("wall-clock deadline exceeded")


@dataclass
class _QueuedCall:
    callback: Callable[[Watchdog], object]
    event_kind: str
    coalesce_key: str | None
    size_bytes: int


T = TypeVar("T")


class AsyncQueue:
    """Bounded FIFO; only explicitly keyed state events are coalesced."""

    _STATE_EVENT_KINDS = frozenset({"state", "connection", "chat_list", "presence"})

    def __init__(self, limits: HostLimits, scope: CancellationScope) -> None:
        self._limits = limits
        self._scope = scope
        self._items: deque[_QueuedCall] = deque()
        self._bytes = 0
        self._cancelled = False
        self._lock = threading.Lock()

    @property
    def size(self) -> int:
        with self._lock:
            return len(self._items)

    @property
    def bytes(self) -> int:
        with self._lock:
            return self._bytes

    def put(
        self,
        callback: Callable[[Watchdog], object],
        *,
        event_kind: str,
        coalesce_key: str | None = None,
        payload: object = None,
    ) -> None:
        if not callable(callback):
            raise TypeError("queued callback must be callable")
        if not isinstance(event_kind, str) or not event_kind:
            raise ValueError("event_kind must be a non-empty string")
        try:
            payload_bytes = json.dumps(
                payload,
                ensure_ascii=False,
                sort_keys=True,
                separators=(",", ":"),
                allow_nan=False,
            ).encode("utf-8")
        except (RecursionError, TypeError, ValueError) as error:
            raise ValueError("event payload must be finite JSON data") from error
        size_bytes = len(event_kind.encode("utf-8")) + len(payload_bytes)
        item = _QueuedCall(callback, event_kind, coalesce_key, size_bytes)
        with self._lock:
            if self._cancelled:
                raise ExecutionCancelled(self._scope.reason)
            self._scope.check()
            if coalesce_key is not None and event_kind not in self._STATE_EVENT_KINDS:
                raise ValueError("coalescing is only allowed for state-like events")
            existing = next(
                (
                    queued
                    for queued in self._items
                    if coalesce_key is not None
                    and queued.event_kind == event_kind
                    and queued.coalesce_key == coalesce_key
                ),
                None,
            )
            resulting_items = len(self._items) if existing is not None else len(self._items) + 1
            resulting_bytes = self._bytes - (existing.size_bytes if existing else 0) + size_bytes
            if resulting_items > self._limits.async_queue_items:
                raise AsyncQueueFull("async queue item quota exceeded")
            if resulting_bytes > self._limits.async_queue_bytes:
                raise AsyncQueueFull("async queue byte quota exceeded")
            if existing is not None:
                self._bytes -= existing.size_bytes
                self._items = deque(
                    item if queued is existing else queued for queued in self._items
                )
            else:
                self._items.append(item)
            self._bytes += size_bytes

    def pop(self) -> _QueuedCall | None:
        with self._lock:
            if not self._items:
                return None
            item = self._items.popleft()
            self._bytes -= item.size_bytes
            return item

    def clear(self) -> None:
        with self._lock:
            self._items.clear()
            self._bytes = 0

    def cancel(self) -> None:
        """Atomically prevent enqueue-after-cancel and clear pending work."""
        with self._lock:
            self._cancelled = True
            self._items.clear()
            self._bytes = 0


@dataclass(frozen=True)
class LuaState:
    """Opaque identity for one native lua_State allocation."""

    state_id: str
    jit_enabled: bool = LUAU_JIT_ENABLED
    native_codegen_enabled: bool = LUAU_NATIVE_CODEGEN_ENABLED

    def __post_init__(self) -> None:
        if self.jit_enabled or self.native_codegen_enabled:
            raise ValueError("Luau JIT and native code generation are disabled")


@dataclass(frozen=True)
class CompiledSource:
    """Ephemeral source compilation result; intentionally contains no bytecode."""

    source_sha256: str
    source_size: int


class AddonInstance:
    """The host-owned `(addon, account)` runtime scope."""

    def __init__(self, host: LuauHost, addon_id: str, account_handle: str, source: str) -> None:
        self._host = host
        self.addon_id = addon_id
        self.account_handle = account_handle
        self.source = source
        self._state: LuaState | None = LuaState(uuid4().hex)
        self.allocator = QuotaAllocator(host.limits.memory_bytes)
        self.scope = CancellationScope()
        self.queue = AsyncQueue(host.limits, self.scope)
        self._running = True
        self._lock = threading.Lock()
        self._execution_lock = threading.RLock()

    @property
    def state(self) -> LuaState | None:
        with self._lock:
            return self._state

    @property
    def state_id(self) -> str | None:
        current = self.state
        return current.state_id if current is not None else None

    @property
    def identity(self) -> tuple[str, str]:
        return self.addon_id, self.account_handle

    @property
    def running(self) -> bool:
        with self._lock:
            return self._running and not self.scope.cancelled

    @property
    def queue_size(self) -> int:
        return self.queue.size

    def cancel(self, reason: str) -> None:
        self.scope.cancel(reason)
        self.queue.cancel()
        self.allocator.clear()
        with self._lock:
            self._running = False
            self._state = None

    def enqueue(
        self,
        callback: Callable[[Watchdog], object],
        *,
        event_kind: str,
        coalesce_key: str | None = None,
        payload: object = None,
    ) -> None:
        self._ensure_running()
        self.queue.put(
            callback,
            event_kind=event_kind,
            coalesce_key=coalesce_key,
            payload=payload,
        )

    def drain(self) -> list[object]:
        self._ensure_running()
        results: list[object] = []
        while True:
            self._ensure_running()
            item = self.queue.pop()
            if item is None:
                return results
            results.append(self._host.execute(self, item.callback))

    def _ensure_running(self) -> None:
        self._host._ensure_active(self)


class LuauHost:
    """Owns isolated source-only instances and the global mode gate."""

    def __init__(self, *, limits: HostLimits | None = None) -> None:
        self._verify_luau_lock()
        self.limits = limits or HostLimits()
        self._mode_enabled = False
        self._instances: dict[tuple[str, str], AddonInstance] = {}
        self._lock = threading.RLock()

    @property
    def modification_mode_enabled(self) -> bool:
        with self._lock:
            return self._mode_enabled

    def set_modification_mode(self, enabled: bool) -> None:
        if not isinstance(enabled, bool):
            raise TypeError("Modification Mode must be a boolean")
        with self._lock:
            self._mode_enabled = enabled
            if not enabled:
                for instance in self._instances.values():
                    instance.cancel("Modification Mode disabled")

    def create_instance(self, addon_id: str, account_handle: str, source: str) -> AddonInstance:
        self._require_mode()
        self._validate_identity(addon_id, account_handle)
        self.compile_source(source)
        key = (addon_id, account_handle)
        with self._lock:
            # Re-check while holding the same lock as the mode transition.  A
            # disable racing package startup therefore wins deterministically.
            if not self._mode_enabled:
                raise ExecutionCancelled("Modification Mode disabled")
            existing = self._instances.get(key)
            if existing is not None and existing.running:
                raise ValueError("addon/account instance already exists")
            instance = AddonInstance(self, addon_id, account_handle, source)
            self._instances[key] = instance
            return instance

    def start_instance(self, addon_id: str, account_handle: str) -> AddonInstance:
        self._require_mode()
        key = (addon_id, account_handle)
        with self._lock:
            previous = self._instances.get(key)
            if previous is None:
                raise KeyError("unknown addon/account instance")
            source = previous.source
        return self.create_instance(addon_id, account_handle, source)

    def instance(self, addon_id: str, account_handle: str) -> AddonInstance | None:
        with self._lock:
            return self._instances.get((addon_id, account_handle))

    def close_instance(self, addon_id: str, account_handle: str) -> None:
        with self._lock:
            instance = self._instances.pop((addon_id, account_handle), None)
            if instance is not None:
                instance.cancel("instance closed")

    def compile_source(self, source: str) -> CompiledSource:
        """Validate source and return only digest metadata, never bytecode."""
        if not isinstance(source, str):
            raise SourceRejected("source text is required; bytecode is not accepted")
        try:
            encoded = source.encode("utf-8", errors="strict")
        except UnicodeEncodeError as error:
            raise SourceRejected("source text must be valid UTF-8") from error
        if encoded.startswith((b"\x1bLuau", b"\x1bLua")):
            raise SourceRejected("compiled bytecode is not accepted")
        if b"\x00" in encoded:
            raise SourceRejected("source text contains NUL")
        if len(encoded) > self.limits.memory_bytes:
            raise SourceRejected("source exceeds the instance memory quota")
        return CompiledSource(sha256(encoded).hexdigest(), len(encoded))

    def execute(self, instance: AddonInstance, operation: Callable[[Watchdog], T]) -> T:
        """Run one cooperative operation through the host watchdog.

        The native adapter calls this policy seam around `lua_pcall`; the
        callback form keeps tests deterministic without shipping a fake VM.
        """
        with instance._execution_lock:
            self._ensure_active(instance)
            watchdog = Watchdog(instance.scope, self.limits)
            result = operation(watchdog)
            watchdog.check()
            return result

    def _ensure_active(self, instance: AddonInstance) -> None:
        with self._lock:
            if not self._mode_enabled:
                raise ExecutionCancelled("Modification Mode disabled")
            if self._instances.get(instance.identity) is not instance or not instance.running:
                instance.scope.check()
                raise ExecutionCancelled("addon instance is not running")

    def _require_mode(self) -> None:
        if not self.modification_mode_enabled:
            raise ExecutionCancelled("Modification Mode disabled")

    @staticmethod
    def _verify_luau_lock() -> None:
        try:
            lock = json.loads(Path(__file__).with_name("luau.lock.json").read_text(encoding="utf-8"))
            luau = lock["luau"]
            valid = (
                luau["commit"] == LUAU_COMMIT
                and luau["jit"] is False
                and luau["native_codegen"] is False
                and luau["accept_package_bytecode"] is False
                and luau["persist_bytecode"] is False
            )
        except (OSError, KeyError, TypeError, json.JSONDecodeError):
            valid = False
        if not valid:
            raise RuntimeError("invalid Luau lock: refusing to start the host")

    @staticmethod
    def _validate_identity(addon_id: str, account_handle: str) -> None:
        if not isinstance(addon_id, str) or not re.fullmatch(r"[a-z][a-z0-9]*(?:[._-][a-z0-9]+)+", addon_id):
            raise ValueError("addon identity must be a valid host-bound package ID")
        if not isinstance(account_handle, str) or not account_handle:
            raise ValueError("account handle must be a non-empty host-bound handle")


__all__ = [
    "LUAU_COMMIT",
    "LUAU_JIT_ENABLED",
    "LUAU_NATIVE_CODEGEN_ENABLED",
    "AddonInstance",
    "Allocation",
    "AsyncQueueFull",
    "CancellationScope",
    "CompiledSource",
    "ExecutionCancelled",
    "ExecutionLimitExceeded",
    "HostError",
    "HostLimits",
    "LuaState",
    "LuauHost",
    "MemoryLimitExceeded",
    "QuotaAllocator",
    "SourceRejected",
    "Watchdog",
]
