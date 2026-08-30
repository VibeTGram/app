"""Typed, non-sensitive, publisher-bound addon IPC.

IPC is an explicitly declared data channel.  It is not a capability transport,
and resolution is keyed by the concrete host install identity and account, never
by display name or addon priority.
"""
from __future__ import annotations

import copy
import math
import re
from dataclasses import dataclass
from typing import Any, Callable, Iterable, Mapping

from .identity import HostIdentity
from .ui import UiNode, validate_tree


class IpcError(RuntimeError):
    """Base class for IPC resolution and dispatch failures."""


class IpcValidationError(IpcError, ValueError):
    """Payload or interface data is outside the v1 IPC contract."""


class IpcResolutionError(IpcError):
    """No unique publisher-bound provider can satisfy a dependency."""


_SHA256 = re.compile(r"^[0-9a-f]{64}$")
_PUBLISHER_OR_DEVELOPMENT_ID = re.compile(r"^(?:sha256:[0-9a-f]{64}|dev_[0-9a-f]{32})$")
_SYMBOL = re.compile(r"^[A-Za-z][A-Za-z0-9_.:-]{0,159}$")
_SEMVER = re.compile(r"^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z.-]+))?$")
_SENSITIVE_KEYS = frozenset({
    "account_id", "account_handle", "chat_id", "chat_ref", "user_id", "user_ref",
    "message_id", "message_ref", "telegram_id", "auth_key", "access_token", "refresh_token",
    "cookie", "cookies", "credential", "credentials", "media_handle", "file_handle",
    "message", "media", "metadata", "username", "phone_number", "telegram",
})
_HANDLE_KINDS = frozenset({"FileHandle", "MediaHandle", "AccountHandle", "ChatRef", "MessageRef", "UserRef"})


def _version(value: str) -> tuple[int, int, int, tuple[str, ...]]:
    match = _SEMVER.fullmatch(value) if isinstance(value, str) else None
    if match is None:
        raise IpcValidationError(f"invalid IPC version: {value!r}")
    prerelease = tuple((match.group(4) or "").split(".")) if match.group(4) else ()
    if any(item.isdigit() and len(item) > 1 and item.startswith("0") for item in prerelease):
        raise IpcValidationError(f"invalid IPC version: {value!r}")
    return int(match.group(1)), int(match.group(2)), int(match.group(3)), prerelease


def _semver_cmp(left: tuple[int, int, int, tuple[str, ...]], right: tuple[int, int, int, tuple[str, ...]]) -> int:
    """Return -1/0/1 using SemVer precedence (release > prerelease)."""
    if left[:3] != right[:3]:
        return -1 if left[:3] < right[:3] else 1
    if not left[3] or not right[3]:
        if left[3] == right[3]:
            return 0
        return 1 if not left[3] else -1
    for a, b in zip(left[3], right[3]):
        if a == b:
            continue
        if a.isdigit() and b.isdigit():
            return -1 if int(a) < int(b) else 1
        if a.isdigit() != b.isdigit():
            return -1 if a.isdigit() else 1
        return -1 if a < b else 1
    return 0 if len(left[3]) == len(right[3]) else (-1 if len(left[3]) < len(right[3]) else 1)


def _in_range(version: str, range_value: str | Mapping[str, str]) -> bool:
    actual = _version(version)
    if isinstance(range_value, str):
        return actual == _version(range_value)
    if not isinstance(range_value, Mapping) or not range_value:
        raise IpcValidationError("dependency version range must be exact or bounded")
    if "exact" in range_value:
        return actual == _version(range_value["exact"])
    if set(range_value) - {"minimum_inclusive", "maximum_exclusive"}:
        raise IpcValidationError("unknown dependency version range field")
    if "minimum_inclusive" in range_value and _semver_cmp(actual, _version(range_value["minimum_inclusive"])) < 0:
        return False
    if "maximum_exclusive" in range_value and _semver_cmp(actual, _version(range_value["maximum_exclusive"])) >= 0:
        return False
    return True


@dataclass(frozen=True)
class IpcInterface:
    name: str
    version: str
    schema_sha256: str
    data_class: str = "addon-local-nonsensitive"

    def __post_init__(self) -> None:
        if not isinstance(self.name, str) or not _SYMBOL.fullmatch(self.name):
            raise IpcValidationError("invalid IPC interface name")
        _version(self.version)
        if not isinstance(self.schema_sha256, str) or not _SHA256.fullmatch(self.schema_sha256):
            raise IpcValidationError("invalid IPC schema digest")
        if self.data_class != "addon-local-nonsensitive":
            raise IpcValidationError("v1 IPC only supports addon-local-nonsensitive data")

    @property
    def key(self) -> tuple[str, str, str]:
        return self.name, self.version, self.schema_sha256


@dataclass(frozen=True)
class Dependency:
    package_id: str
    publisher_key_id: str
    version_range: str | Mapping[str, str]

    def __post_init__(self) -> None:
        if not isinstance(self.package_id, str) or not self.package_id:
            raise IpcValidationError("dependency package ID is required")
        if not isinstance(self.publisher_key_id, str) or not _PUBLISHER_OR_DEVELOPMENT_ID.fullmatch(self.publisher_key_id):
            raise IpcValidationError("dependency publisher identity is required")
        _in_range("1.0.0", self.version_range)  # validates range shape

    @property
    def binding(self) -> tuple[str, str]:
        return self.package_id, self.publisher_key_id


@dataclass(frozen=True)
class _Provider:
    identity: HostIdentity
    account: str
    interface: IpcInterface
    handler: Callable[[Mapping[str, Any]], Mapping[str, Any]]


class IpcChannel:
    """Resolved channel with immutable provider and account bindings."""

    def __init__(self, provider: _Provider, consumer: HostIdentity, account: str, max_payload_bytes: int) -> None:
        self._provider = provider
        self._consumer = consumer
        self._account = account
        self._max_payload_bytes = max_payload_bytes

    @property
    def key(self) -> tuple[str, tuple[str, str], str, str, str]:
        return (self._account, self._provider.identity.binding, *self._provider.interface.key)

    @property
    def account(self) -> str:
        return self._account

    def for_account(self, account: str) -> "IpcChannel":
        if account != self._account:
            raise IpcError("IPC channel is bound to one account")
        return self

    def call(self, payload: Mapping[str, Any]) -> Mapping[str, Any]:
        IpcRegistry.validate_payload(payload, max_bytes=self._max_payload_bytes)
        try:
            result = self._provider.handler(copy.deepcopy(dict(payload)))
        except IpcError:
            raise
        except Exception as error:  # provider failures never leak arbitrary host exceptions
            raise IpcError("IPC provider failed") from error
        IpcRegistry.validate_payload(result, max_bytes=self._max_payload_bytes)
        return copy.deepcopy(dict(result))


class IpcRegistry:
    """Registry and resolver for currently installed provider endpoints."""

    def __init__(self, *, max_payload_bytes: int = 64 * 1024) -> None:
        if isinstance(max_payload_bytes, bool) or not isinstance(max_payload_bytes, int) or max_payload_bytes <= 0:
            raise ValueError("max_payload_bytes must be positive")
        self.max_payload_bytes = max_payload_bytes
        self._providers: list[_Provider] = []

    def register_provider(self, identity: HostIdentity, account: str, interface: IpcInterface, handler: Callable[[Mapping[str, Any]], Mapping[str, Any]]) -> None:
        if not isinstance(identity, HostIdentity) or not isinstance(account, str) or not account:
            raise IpcValidationError("provider identity and account are host-bound")
        if not isinstance(interface, IpcInterface) or not callable(handler):
            raise TypeError("invalid provider endpoint")
        self._providers.append(_Provider(identity, account, interface, handler))

    def resolve(self, consumer: HostIdentity, account: str, interface: IpcInterface, *, dependencies: Iterable[Dependency], capabilities: Iterable[str] = ("ipc.consume",)) -> IpcChannel:
        if not isinstance(consumer, HostIdentity) or not isinstance(interface, IpcInterface) or not isinstance(account, str) or not account:
            raise IpcValidationError("consumer identity, interface, and account are required")
        active = set(capabilities)
        if active - {"ipc.consume", "ipc.provide", "ui.extend", "ui.screen", "ui.navigate", "ui.prompt", "storage.kv", "storage.documents", "settings.global_safe", "diagnostics.own"}:
            raise IpcResolutionError("IPC cannot be combined with Telegram, events, network, or raw capabilities")
        dependency_list = tuple(dependencies)
        # Interface names need not embed package IDs.  The provider is selected
        # only by an exact publisher-bound dependency edge and its version.
        candidates = []
        for provider in self._providers:
            matching_edges = [
                dependency for dependency in dependency_list
                if dependency.binding == provider.identity.binding
                and _in_range(provider.identity.package_version, dependency.version_range)
            ]
            if provider.account == account and provider.interface.key == interface.key and len(matching_edges) == 1:
                candidates.append(provider)
        if len(candidates) != 1:
            raise IpcResolutionError("IPC provider is missing or ambiguous")
        return IpcChannel(candidates[0], consumer, account, self.max_payload_bytes)

    def invalidate(self, identity: HostIdentity, account: str) -> None:
        self._providers = [p for p in self._providers if not (p.identity.binding == identity.binding and p.account == account)]

    def resolve_dependency(self, consumer: HostIdentity, account: str, dependency: Dependency, interface: IpcInterface) -> IpcChannel:
        """Resolve one manifest edge without permitting an implicit provider."""
        return self.resolve(consumer, account, interface, dependencies=(dependency,))

    @staticmethod
    def validate_payload(payload: Any, *, max_bytes: int = 64 * 1024) -> None:
        if not isinstance(payload, Mapping):
            raise IpcValidationError("IPC payload must be an object")
        def json_value(value: Any) -> Any:
            if isinstance(value, UiNode):
                validate_tree(value)
                return value.to_dict()
            if isinstance(value, Mapping):
                return {key: json_value(item) for key, item in value.items()}
            if isinstance(value, list):
                return [json_value(item) for item in value]
            return value
        normalized = json_value(payload)
        try:
            encoded = __import__("json").dumps(normalized, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False).encode("utf-8")
        except (TypeError, ValueError, RecursionError) as error:
            raise IpcValidationError("IPC payload must be finite JSON-compatible data") from error
        # A static walk catches semantic handles and identifiers which JSON
        # shape alone cannot make safe.
        def visit(value: Any, depth: int = 0) -> None:
            if depth > 8:
                raise IpcValidationError("IPC payload nesting limit exceeded")
            if isinstance(value, UiNode):
                return
            if isinstance(value, (str, int, float, bool)) or value is None:
                if isinstance(value, str) and len(value) > 4096:
                    raise IpcValidationError("IPC string exceeds size limit")
                if isinstance(value, float) and not math.isfinite(value):
                    raise IpcValidationError("IPC numbers must be finite")
                return
            if isinstance(value, Mapping):
                for key, item in value.items():
                    normalized_key = key.lower().replace("-", "_") if isinstance(key, str) else ""
                    if (not isinstance(key, str) or normalized_key in _SENSITIVE_KEYS
                            or normalized_key.startswith("telegram_")
                            or normalized_key.endswith(("_message", "_media", "_metadata", "_chat_id", "_user_id"))):
                        raise IpcValidationError("IPC payload contains sensitive identifier")
                    if key.lower() in {"kind", "type"} and item in _HANDLE_KINDS:
                        raise IpcValidationError("IPC payload contains a host handle")
                    visit(item, depth + 1)
                return
            if isinstance(value, list):
                if len(value) > 256:
                    raise IpcValidationError("IPC array is too large")
                for item in value:
                    visit(item, depth + 1)
                return
            raise IpcValidationError("IPC payload contains unsupported or host-owned value")
        visit(normalized)
        if isinstance(max_bytes, bool) or not isinstance(max_bytes, int) or max_bytes <= 0:
            raise ValueError("max_bytes must be positive")
        if len(encoded) > max_bytes:
            raise IpcValidationError("IPC payload exceeds size limit")


IpcRouter = IpcRegistry


__all__ = [
    "Dependency", "IpcChannel", "IpcError", "IpcInterface", "IpcRegistry", "IpcResolutionError",
    "IpcRouter", "IpcValidationError",
]
