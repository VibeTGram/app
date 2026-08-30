from __future__ import annotations

import time

import pytest

from mods.runtime import (
    AsyncQueueFull,
    ExecutionCancelled,
    ExecutionLimitExceeded,
    HostLimits,
    LuaState,
    LuauHost,
    MemoryLimitExceeded,
    SourceRejected,
)


def test_instances_are_isolated_by_addon_and_account() -> None:
    host = LuauHost()
    host.set_modification_mode(True)
    first = host.create_instance("org.example.one", "account-a", "return 1")
    second = host.create_instance("org.example.one", "account-b", "return 1")
    third = host.create_instance("org.example.two", "account-a", "return 1")

    assert len({first.state_id, second.state_id, third.state_id}) == 3
    assert first.identity == ("org.example.one", "account-a")
    assert second.identity == ("org.example.one", "account-b")
    assert third.identity == ("org.example.two", "account-a")
    assert host.instance("org.example.one", "account-a") is first


def test_source_only_loading_rejects_bytes_and_bytecode() -> None:
    host = LuauHost()
    host.set_modification_mode(True)

    with pytest.raises(SourceRejected, match="source text"):
        host.create_instance("org.example.mod", "account", b"return 1")
    with pytest.raises(SourceRejected, match="bytecode"):
        host.create_instance("org.example.mod", "account", "\x1bLuau\x00compiled")


def test_allocator_enforces_hard_memory_quota() -> None:
    host = LuauHost(limits=HostLimits(memory_bytes=8))
    host.set_modification_mode(True)
    instance = host.create_instance("org.example.mod", "account", "return 1")

    allocation = instance.allocator.allocate(8)
    assert instance.allocator.used_bytes == 8
    with pytest.raises(MemoryLimitExceeded):
        instance.allocator.allocate(1)
    instance.allocator.free(allocation)
    assert instance.allocator.used_bytes == 0

    allocation = instance.allocator.allocate(4)
    allocation = instance.allocator.reallocate(allocation, 8)
    assert allocation.size == 8
    assert instance.allocator.used_bytes == 8
    instance.allocator.free(allocation)


def test_watchdog_stops_instruction_exhaustion_and_deadline() -> None:
    host = LuauHost(limits=HostLimits(instruction_limit=3, wall_time_seconds=0.01))
    host.set_modification_mode(True)
    instance = host.create_instance("org.example.mod", "account", "return 1")

    with pytest.raises(ExecutionLimitExceeded, match="instruction"):
        host.execute(instance, lambda watchdog: watchdog.tick(4))

    with pytest.raises(ExecutionLimitExceeded, match="deadline"):
        host.execute(instance, lambda watchdog: (time.sleep(0.02), watchdog.tick())[1])


def test_async_queue_is_fifo_bounded_and_cancellable() -> None:
    host = LuauHost(limits=HostLimits(async_queue_items=2, async_queue_bytes=1000, instruction_limit=3))
    host.set_modification_mode(True)
    instance = host.create_instance("org.example.mod", "account", "return 1")
    seen: list[int] = []

    instance.enqueue(lambda _watchdog: seen.append(1), event_kind="message.created")
    instance.enqueue(lambda _watchdog: seen.append(2), event_kind="message.created")
    with pytest.raises(AsyncQueueFull):
        instance.enqueue(lambda _watchdog: seen.append(3), event_kind="message.created")
    instance.drain()
    assert seen == [1, 2]

    instance.enqueue(lambda _watchdog: seen.append(4), event_kind="state", coalesce_key="connection")
    instance.enqueue(lambda _watchdog: seen.append(5), event_kind="state", coalesce_key="connection")
    instance.drain()
    assert seen == [1, 2, 5]

    instance.enqueue(lambda watchdog: watchdog.tick(4), event_kind="state")
    with pytest.raises(ExecutionLimitExceeded, match="instruction"):
        instance.drain()

    instance.enqueue(lambda _watchdog: seen.append(6), event_kind="message.created")
    instance.cancel("logout")
    assert instance.queue_size == 0
    with pytest.raises(ExecutionCancelled):
        instance.drain()


def test_queue_measures_payload_and_rejects_message_coalescing() -> None:
    host = LuauHost(limits=HostLimits(async_queue_items=2, async_queue_bytes=16))
    host.set_modification_mode(True)
    instance = host.create_instance("org.example.mod", "account", "return 1")

    with pytest.raises(AsyncQueueFull, match="byte"):
        instance.enqueue(lambda _watchdog: None, event_kind="state", payload={"value": "too-large"})
    with pytest.raises(ValueError, match="state-like"):
        instance.enqueue(lambda _watchdog: None, event_kind="message.updated", coalesce_key="message")


def test_cancellation_releases_state_allocations() -> None:
    host = LuauHost()
    host.set_modification_mode(True)
    instance = host.create_instance("org.example.mod", "account", "return 1")
    instance.allocator.allocate(10)

    instance.cancel("logout")

    assert instance.state is None
    assert instance.allocator.used_bytes == 0


def test_limits_and_state_configuration_cannot_enable_native_execution() -> None:
    with pytest.raises(ValueError):
        HostLimits(memory_bytes=True)
    with pytest.raises(ValueError):
        HostLimits(wall_time_seconds=float("nan"))
    with pytest.raises(ValueError, match="disabled"):
        LuaState("state", jit_enabled=True)


def test_disabling_modification_mode_stops_every_instance_immediately() -> None:
    host = LuauHost()
    host.set_modification_mode(True)
    first = host.create_instance("org.example.one", "account-a", "return 1")
    second = host.create_instance("org.example.two", "account-a", "return 1")
    first.enqueue(lambda _watchdog: None, event_kind="message.created")
    second.enqueue(lambda _watchdog: None, event_kind="message.created")

    host.set_modification_mode(False)

    assert not first.running
    assert not second.running
    assert first.queue_size == 0
    assert second.queue_size == 0
    with pytest.raises(ExecutionCancelled, match="Modification Mode"):
        host.execute(first, lambda watchdog: None)
    with pytest.raises(ExecutionCancelled, match="Modification Mode"):
        second.drain()


def test_restarting_an_instance_creates_a_fresh_state_and_retains_source() -> None:
    host = LuauHost()
    host.set_modification_mode(True)
    old = host.create_instance("org.example.mod", "account", "return 1")
    old_state_id = old.state_id
    host.set_modification_mode(False)
    host.set_modification_mode(True)

    new = host.start_instance("org.example.mod", "account")

    assert new.state_id != old_state_id
    assert new.source == "return 1"
    assert new.running
