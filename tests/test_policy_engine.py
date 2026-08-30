from __future__ import annotations

import time

import pytest

from mods.policy import (
    Capability,
    CompatibilityError,
    GrantLifetime,
    Manifest,
    PackageIdentity,
    PermissionClass,
    PolicyEngine,
    PolicyError,
    PolicyRequest,
    QuotaExceeded,
    RawSurface,
    TrustError,
    UnknownDeclaration,
)


KEY = "sha256:" + "a" * 64


def manifest(*capabilities: str, **extra: object) -> Manifest:
    document = {
        "schema_version": 1,
        "type": "vibemod",
        "id": "org.example.policy",
        "version": "1.0.0",
        "name": {"en": "Policy"},
        "description": {"en": "Policy test"},
        "publisher": {"key_id": KEY},
        "entrypoint": "main.luau",
        "api": {"semantic": {"exact": "1.0.0"}},
        "capabilities": list(capabilities),
        "licenses": ["Apache-2.0"],
    }
    if any(capability.startswith("tdlib.raw.") for capability in capabilities):
        document["api"]["raw"] = {
            "tdlib_commit": "c" * 40,
            "schema_hash": "b" * 64,
            "generator_version": "1.0.0",
        }
    if "network.https" in capabilities:
        document["network"] = {"domains": ["api.example.com"]}
    document.update(extra)
    return Manifest.from_dict(document)


def identity() -> PackageIdentity:
    return PackageIdentity("org.example.policy", KEY, "1.0.0")


def test_manifest_rejects_unknown_capabilities_and_raw_declarations() -> None:
    with pytest.raises(UnknownDeclaration):
        manifest("telegram.not_a_real_capability")

    surface = RawSurface(functions={"known"}, fields={"message.content"}, enums={"known.value"})
    raw = {
        "functions": ["sendMessage"],
        "fields": ["message.content"],
        "enums": ["messageSendingState.sent"],
    }
    with pytest.raises(UnknownDeclaration):
        Manifest.from_dict({**manifest("tdlib.raw.invoke", raw=raw).to_dict(), "raw": raw}, raw_surface=surface)


def test_manifest_requires_exact_raw_surface_and_validates_compatibility() -> None:
    surface = RawSurface(
        functions={"sendMessage"},
        constructors={"updateNewMessage"},
        fields={"message.content"},
        enums={"messageSendingState.sent"},
        schema_hash="b" * 64,
        tdlib_commit="c" * 40,
        generator_version="1.0.0",
    )
    declared = manifest(
        "tdlib.raw.invoke",
        raw={"functions": ["sendMessage"], "fields": ["message.content"]},
    )
    engine = PolicyEngine(
        declared,
        identity(),
        account_handle="account-a",
        raw_surface=surface,
        semantic_api_version="1.0.0",
    )
    assert engine.authorize(PolicyRequest("tdlib.raw.invoke", function="sendMessage")).allowed is False
    assert isinstance(engine.authorize(PolicyRequest("tdlib.raw.invoke", function="missing")).error, UnknownDeclaration)
    undeclared = engine.authorize(PolicyRequest("ui.extend"))
    assert not undeclared.allowed and isinstance(undeclared.error, UnknownDeclaration)


def test_permission_classes_grants_prompts_and_critical_are_scoped() -> None:
    declared = manifest("ui.extend", "telegram.messages.read_content", "telegram.history.delete_all")
    engine = PolicyEngine(declared, identity(), account_handle="account-a")

    assert Capability.spec("ui.extend").permission_class is PermissionClass.SAFE
    assert engine.authorize(PolicyRequest("ui.extend")).allowed

    sensitive = engine.authorize(PolicyRequest("telegram.messages.read_content"))
    assert not sensitive.allowed
    assert sensitive.requires_prompt
    assert sensitive.prompt is not None

    engine.grants.grant(
        identity(), "account-a", "telegram.messages.read_content", GrantLifetime.PERSISTENT,
        surface_digest=declared.surface_digest,
    )
    assert engine.authorize(PolicyRequest("telegram.messages.read_content")).allowed

    critical = engine.authorize(PolicyRequest("telegram.history.delete_all", target="chat:1"))
    assert critical.effective_class is PermissionClass.CRITICAL
    assert not critical.allowed and critical.requires_confirmation
    confirmed = engine.authorize(
        PolicyRequest("telegram.history.delete_all", target="chat:1", confirmed=True)
    )
    assert confirmed.allowed
    assert not engine.authorize(
        PolicyRequest("telegram.history.delete_all", target="chat:1", confirmed=True)
    ).allowed


def test_dangerous_combination_requires_fresh_confirmation() -> None:
    declared = manifest("telegram.messages.read_content", "network.https")
    engine = PolicyEngine(declared, identity(), account_handle="account-a")
    engine.grants.grant(
        identity(), "account-a", "telegram.messages.read_content", GrantLifetime.PERSISTENT,
        surface_digest=declared.surface_digest,
    )
    engine.grants.grant(
        identity(), "account-a", "network.https", GrantLifetime.PERSISTENT,
        surface_digest=declared.surface_digest,
    )

    decision = engine.authorize(PolicyRequest("network.https", output_bytes=12))
    assert not decision.allowed
    assert decision.dangerous_combinations == ("telegram-content-to-network",)
    assert decision.requires_confirmation
    assert engine.authorize(PolicyRequest("network.https", confirmed=True)).allowed


def test_tos_mode_account_binding_redaction_and_immediate_revoke() -> None:
    declared = manifest("telegram.secret_chats.preserve_deleted", "telegram.messages.read_content")
    identity_value = identity()
    authority = object()
    engine = PolicyEngine(declared, identity_value, account_handle="account-a", modification_mode=False,
                          mode_authority=authority)
    tos = engine.authorize(PolicyRequest("telegram.secret_chats.preserve_deleted"))
    assert not tos.allowed and "Modification Mode" in tos.reason

    engine.set_modification_mode(True, authority=authority)
    engine.grants.grant(identity_value, "account-a", "telegram.messages.read_content", GrantLifetime.MODE,
                        surface_digest=declared.surface_digest)
    wrong_account = engine.authorize(PolicyRequest("telegram.messages.read_content", account_handle="account-b"))
    assert not wrong_account.allowed and "account" in wrong_account.reason

    redacted = engine.redact(
        {"text": "secret", "id": 7},
        {"text": PermissionClass.SENSITIVE_WRITE, "id": PermissionClass.SENSITIVE_READ},
        granted_class=PermissionClass.SENSITIVE_READ,
    )
    assert redacted == {"id": 7}
    engine.grants.revoke(identity_value, "account-a", "telegram.messages.read_content")
    assert not engine.authorize(PolicyRequest("telegram.messages.read_content")).allowed


def test_quota_is_hard_and_unknown_enum_fails_closed() -> None:
    declared = manifest("network.https", quotas={"network_bytes_per_day": 10})
    engine = PolicyEngine(declared, identity(), account_handle="account-a")
    engine.grants.grant(identity(), "account-a", "network.https", GrantLifetime.PERSISTENT,
                        surface_digest=declared.surface_digest)
    assert engine.authorize(PolicyRequest("network.https", enum_values=("not-known",))).error is not None
    assert engine.authorize(PolicyRequest("network.https", output_bytes=11)).error is not None
    assert isinstance(engine.authorize(PolicyRequest("network.https", output_bytes=10)).error, QuotaExceeded) is False


def test_unsigned_identity_requires_developer_mode_and_raw_override_keeps_commit_bound() -> None:
    declared = manifest("ui.extend")
    with pytest.raises(TrustError, match="Developer Mode"):
        PolicyEngine(declared, PackageIdentity.development(declared.package_id, declared.version), account_handle="account-a")

    raw_surface = RawSurface(functions={"sendMessage"}, fields={"message.content"}, schema_hash="d" * 64, tdlib_commit="d" * 40, generator_version="1.0.0")
    raw_manifest = manifest(
        "tdlib.raw.invoke", raw={"functions": ["sendMessage"], "fields": ["message.content"]}
    )
    with pytest.raises(CompatibilityError):
        PolicyEngine(raw_manifest, identity(), account_handle="account-a", raw_surface=raw_surface,
                     developer_mode=True)


def test_policy_mode_transitions_fail_closed_without_host_authority() -> None:
    """Cross-seam: only the host authority may re-enable the engine after disable."""
    authority = object()
    declared = manifest("ui.extend", "telegram.messages.read_content")
    engine = PolicyEngine(
        declared, identity(), account_handle="account-a",
        modification_mode=True, mode_authority=authority,
    )
    assert engine.authorize(PolicyRequest("ui.extend")).allowed

    # Disabling is always allowed (fail closed direction).
    engine.set_modification_mode(False)
    assert not engine.authorize(PolicyRequest("ui.extend")).allowed

    # Unprivileged re-enable attempts are rejected, not honored.
    with pytest.raises(PolicyError, match="host"):
        engine.set_modification_mode(True)
    assert not engine.authorize(PolicyRequest("ui.extend")).allowed

    # The real host authority can re-enable after the gated UI transition.
    engine.set_modification_mode(True, authority=authority)
    assert engine.authorize(PolicyRequest("ui.extend")).allowed
