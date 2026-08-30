"""Cross-seam regression tests for the R4 safe-runtime release gate.

These tests exercise the seams together, not in isolation: the Modification
Mode UI gate (mods/developer.py), the host execution gate (mods/runtime.py),
and the policy engine (mods/policy.py) must agree that a disabled mode stops
every addon immediately and that only the host can re-enable execution.
"""
from __future__ import annotations

import pytest

from mods.developer import DeveloperModeManager, ModificationModeError
from mods.policy import PolicyEngine, PolicyError, PolicyRequest
from mods.runtime import ExecutionCancelled, LuauHost


KEY = "sha256:" + "a" * 64


def _manifest_dict() -> dict:
    return {
        "schema_version": 1,
        "type": "vibemod",
        "id": "org.example.cross",
        "version": "1.0.0",
        "name": {"en": "Cross"},
        "description": {"en": "Cross-seam"},
        "publisher": {"key_id": KEY},
        "entrypoint": "main.luau",
        "api": {"semantic": {"exact": "1.0.0"}},
        "capabilities": ["ui.extend"],
        "licenses": ["Apache-2.0"],
    }


def test_mode_gate_blocks_runtime_and_policy_together() -> None:
    manager = DeveloperModeManager()
    host = LuauHost()

    # Host UI enables both through the gated transition.
    manager.set_modification_mode(True, warning_elapsed_seconds=15, risk_table_shown=True)
    host.set_modification_mode(True)
    instance = host.create_instance("org.example.cross", "account-a", "return 1")
    assert instance.running

    # Toggling off is immediate at every seam: gate, runtime, and policy.
    manager.set_modification_mode(False)
    host.set_modification_mode(False)
    assert not instance.running
    with pytest.raises(ExecutionCancelled, match="Modification Mode"):
        instance.drain()

    from mods.policy import Manifest, PackageIdentity

    engine = PolicyEngine(
        Manifest.from_dict(_manifest_dict()),
        PackageIdentity("org.example.cross", KEY, "1.0.0"),
        account_handle="account-a",
        modification_mode=True,
    )
    assert engine.authorize(PolicyRequest("ui.extend")).allowed
    engine.set_modification_mode(False)
    decision = engine.authorize(PolicyRequest("ui.extend"))
    assert not decision.allowed and "Modification Mode" in decision.reason

    # Re-enabling requires the full gate again — and policy refuses hosts
    # that were not constructed with an authority token.
    with pytest.raises(ModificationModeError, match="risk table"):
        manager.set_modification_mode(True)
    with pytest.raises(PolicyError, match="host"):
        engine.set_modification_mode(True)
    assert not manager.modification_mode_enabled
    assert not host.modification_mode_enabled
    assert not engine.modification_mode


def test_runtime_instances_cannot_outlive_their_mode_window() -> None:
    manager = DeveloperModeManager()
    host = LuauHost()
    manager.set_modification_mode(True, warning_elapsed_seconds=15, risk_table_shown=True)
    host.set_modification_mode(True)
    first = host.create_instance("org.example.cross", "account-a", "return 1")
    old_state_id = first.state_id

    manager.set_modification_mode(False)
    host.set_modification_mode(False)
    assert first.state_id is None

    # A fresh gated cycle creates a new state, never resurrects the old one.
    manager.set_modification_mode(True, warning_elapsed_seconds=15, risk_table_shown=True)
    host.set_modification_mode(True)
    second = host.start_instance("org.example.cross", "account-a")
    assert second.state_id is not None
    assert second.state_id != old_state_id
