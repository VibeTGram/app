import pytest

from mods.developer import (
    DeveloperModeError,
    DeveloperModeManager,
    HotReloadManager,
    ModificationModeError,
    RISK_TABLE,
    validate_bridge_names,
)
from mods.identity import HostIdentity, HostIdentityFactory, IdentityError, IdentityStore


def enable_modification_mode(manager: DeveloperModeManager) -> None:
    manager.set_modification_mode(True, warning_elapsed_seconds=manager.warning_seconds,
                                  risk_table_shown=True)


def test_developer_mode_requires_modification_gate_and_seven_taps() -> None:
    manager = DeveloperModeManager()
    with pytest.raises(DeveloperModeError, match="Modification Mode"):
        manager.enable_developer_mode(7, warning_elapsed_seconds=15, confirmed=True)

    enable_modification_mode(manager)
    with pytest.raises(DeveloperModeError, match="seven"):
        manager.enable_developer_mode(6, warning_elapsed_seconds=15, confirmed=True)
    with pytest.raises(DeveloperModeError, match="15"):
        manager.enable_developer_mode(7, warning_elapsed_seconds=14.9, confirmed=True)

    manager.enable_developer_mode(7, warning_elapsed_seconds=15, confirmed=True)
    assert manager.developer_mode_enabled
    manager.set_modification_mode(False)
    assert not manager.developer_mode_enabled


def test_modification_mode_opt_in_requires_risk_table_and_full_timer() -> None:
    manager = DeveloperModeManager()

    with pytest.raises(ModificationModeError, match="risk table"):
        manager.set_modification_mode(True, warning_elapsed_seconds=15)
    with pytest.raises(ModificationModeError, match="15"):
        manager.set_modification_mode(True, risk_table_shown=True)
    with pytest.raises(ModificationModeError, match="15"):
        manager.set_modification_mode(True, warning_elapsed_seconds=14.9, risk_table_shown=True)
    with pytest.raises(TypeError):
        manager.set_modification_mode("yes")  # type: ignore[arg-type]

    assert not manager.modification_mode_enabled
    enable_modification_mode(manager)
    assert manager.modification_mode_enabled

    # Disabling stays immediate: no timer, no confirmation, no risk table.
    manager.set_modification_mode(False)
    assert not manager.modification_mode_enabled
    assert RISK_TABLE and all(line.strip() for line in RISK_TABLE)


def test_modification_mode_disabling_resets_developer_mode_immediately() -> None:
    manager = DeveloperModeManager()
    enable_modification_mode(manager)
    manager.enable_developer_mode(7, warning_elapsed_seconds=15, confirmed=True)
    assert manager.developer_mode_enabled

    manager.set_modification_mode(False)
    assert not manager.developer_mode_enabled


def test_unsigned_identity_is_host_generated_and_explicit_reconnect_only() -> None:
    store = IdentityStore()
    first = store.import_project("project-a", "org.example.mod")
    second = store.import_project("project-a", "org.example.mod")
    assert first.identity.development_install_id != second.identity.development_install_id
    assert store.reconnect(first.record_id).identity == first.identity
    with pytest.raises(IdentityError):
        HostIdentityFactory.unsigned(
            "org.example.mod", "1.0.0", development_install_id="dev_" + "a" * 32
        )
    with pytest.raises(IdentityError, match="host"):
        HostIdentity("org.example.mod", "1.0.0", False, development_install_id="dev_" + "a" * 32)


def test_hot_reload_is_atomic_source_only_and_rolls_back() -> None:
    manager = DeveloperModeManager()
    enable_modification_mode(manager)
    manager.enable_developer_mode(7, warning_elapsed_seconds=15, confirmed=True)
    loaded: list[str] = []
    host = HotReloadManager(manager, loader=lambda source: loaded.append(source) or source.upper())

    assert host.load("print('one')") == "PRINT('ONE')"
    manager.disable_developer_mode()
    assert host.source is None
    manager.enable_developer_mode(7, warning_elapsed_seconds=15, confirmed=True)
    assert host.load("print('one')") == "PRINT('ONE')"
    with pytest.raises(DeveloperModeError, match="reload failed"):
        host.reload("print('two')", loader=lambda _: (_ for _ in ()).throw(RuntimeError("bad")))
    assert host.source == "print('one')"
    with pytest.raises(DeveloperModeError, match="bytecode"):
        host.reload("\x1bLuaQ")


def test_bridge_surface_has_no_platform_escape_hatches() -> None:
    assert validate_bridge_names({"ui", "ipc", "diagnostics"}) == {"ui", "ipc", "diagnostics"}
    with pytest.raises(DeveloperModeError, match="forbidden"):
        validate_bridge_names({"ui", "jni.handle"})
