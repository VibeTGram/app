from __future__ import annotations

import pytest

from mods.identity import HostIdentityFactory
from mods.ipc import (
    Dependency,
    IpcError,
    IpcInterface,
    IpcRegistry,
    IpcValidationError,
)


def test_ipc_routes_by_publisher_install_identity_and_account() -> None:
    provider = HostIdentityFactory.signed("org.provider", "sha256:" + "a" * 64, "1.0.0")
    consumer = HostIdentityFactory.signed("org.consumer", "sha256:" + "b" * 64, "1.0.0")
    interface = IpcInterface("settings.local", "1.0.0", "c" * 64)
    registry = IpcRegistry()
    registry.register_provider(provider, "account-a", interface, lambda payload: {"ok": payload["ok"]})
    channel = registry.resolve(
        consumer,
        "account-a",
        interface,
        dependencies=(Dependency(provider.package_id, provider.publisher_key_id, "1.0.0"),),
    )

    assert channel.call({"ok": True}) == {"ok": True}
    with pytest.raises(IpcError, match="account"):
        channel.for_account("account-b")


def test_ipc_rejects_sensitive_values_and_provider_confusion() -> None:
    provider = HostIdentityFactory.signed("org.provider", "sha256:" + "a" * 64, "1.0.0")
    consumer = HostIdentityFactory.signed("org.consumer", "sha256:" + "b" * 64, "1.0.0")
    interface = IpcInterface("local", "1.0.0", "c" * 64)
    registry = IpcRegistry()
    registry.register_provider(provider, "a", interface, lambda payload: payload)
    registry.register_provider(provider, "a", interface, lambda payload: payload)
    with pytest.raises(IpcError, match="ambiguous"):
        registry.resolve(consumer, "a", interface, dependencies=(Dependency(provider.package_id, provider.publisher_key_id, "1.0.0"),))

    with pytest.raises(IpcValidationError, match="sensitive"):
        registry.validate_payload({"message_id": 42})
    with pytest.raises(IpcValidationError, match="host handle"):
        registry.validate_payload({"handle": {"kind": "FileHandle"}})
