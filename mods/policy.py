"""Fail-closed capability and permission policy for VibeTGram addons.

This module is deliberately host-side: a manifest is untrusted input, package
identity and account scope are supplied by the host, and every operation is
checked again immediately before it is dispatched.  It is usable without the
Android runtime so the security contract can be exercised by unit tests.
"""
from __future__ import annotations

import json
import math
import re
import threading
import time
from dataclasses import dataclass, field
from datetime import datetime, timezone
from enum import Enum, IntEnum
from hashlib import sha256
from types import MappingProxyType
from typing import Any, Iterable, Mapping
from uuid import uuid4


class PolicyError(RuntimeError):
    """Base class for policy failures."""


class ManifestError(PolicyError, ValueError):
    """Malformed or internally inconsistent manifest."""


class UnknownDeclaration(ManifestError):
    """Unknown capability, raw declaration, field, or enum value."""


class CompatibilityError(PolicyError, ValueError):
    """Package API or pinned raw schema is incompatible."""


class TrustError(PolicyError, ValueError):
    """Package trust/developer-mode requirements were not met."""


class AccountBindingError(PolicyError, ValueError):
    """An operation attempted to cross its host-bound account."""


class QuotaExceeded(PolicyError):
    """An operation would exceed a declared quota."""


class PermissionClass(IntEnum):
    SAFE = 0
    SENSITIVE_READ = 1
    SENSITIVE_WRITE = 2
    CRITICAL = 3
    TOS_SENSITIVE = 4
    FORBIDDEN = 5

    @property
    def requires_prompt(self) -> bool:
        return self not in (PermissionClass.SAFE, PermissionClass.FORBIDDEN)


class GrantLifetime(str, Enum):
    ONCE = "once"
    MODE = "while_mode_enabled"
    PERSISTENT = "persistent"
    OPERATION = "one_operation"


@dataclass(frozen=True)
class CapabilitySpec:
    name: str
    permission_class: PermissionClass
    tos_sensitive: bool = False
    description: str = ""


# Explicit registry.  Unknown strings never get a default class.
_CAPABILITY_SPECS: dict[str, CapabilitySpec] = {}

def _cap(name: str, cls: PermissionClass, *, tos: bool = False, description: str = "") -> None:
    _CAPABILITY_SPECS[name] = CapabilitySpec(name, cls, tos, description)


for _name in (
    "ui.extend", "ui.screen", "ui.navigate", "ui.prompt", "storage.kv",
    "storage.documents", "events.best_effort", "ipc.provide", "ipc.consume",
    "settings.global_safe", "diagnostics.own",
):
    _cap(_name, PermissionClass.SAFE)
for _name in (
    "events.reliable", "telegram.account.read_basic", "telegram.chats.read",
    "telegram.messages.read_metadata", "telegram.messages.read_content",
    "telegram.messages.observe", "telegram.contacts.read", "telegram.files.download",
    "telegram.calls.observe", "telegram.secret_chats.read", "telegram.secret_chats.observe",
    "telegram.ephemeral.read", "tdlib.raw.objects", "tdlib.raw.observe_incoming",
):
    _cap(_name, PermissionClass.SENSITIVE_READ)
for _name in (
    "network.https", "telegram.messages.send", "telegram.messages.edit",
    "telegram.messages.delete", "telegram.messages.forward", "telegram.reactions.write",
    "telegram.drafts.write", "telegram.contacts.write", "telegram.profile.write",
    "telegram.chat.manage", "telegram.files.upload", "telegram.calls.control",
    "tdlib.raw.invoke", "tdlib.raw.mutate_incoming", "tdlib.raw.suppress_incoming",
):
    _cap(_name, PermissionClass.SENSITIVE_WRITE)
for _name in (
    "telegram.files.import_user", "telegram.files.export_user",
    "telegram.calls.capture_audio", "telegram.calls.capture_video",
    "telegram.calls.capture_screen", "telegram.location.read", "telegram.location.send",
):
    _cap(_name, PermissionClass.CRITICAL)
_cap("telegram.messages.delete", PermissionClass.SENSITIVE_WRITE)
# Irreversible variants are distinct, exact capabilities in the host API.
for _name in (
    "telegram.account.delete", "telegram.account.logout", "telegram.account.transfer_ownership",
    "telegram.payment.confirm", "telegram.history.delete_all", "telegram.calls.capture_audio",
):
    _cap(_name, PermissionClass.CRITICAL)
for _name in (
    "telegram.secret_chats.preserve_deleted", "telegram.tos.read_receipts.modify",
    "telegram.tos.presence.modify", "telegram.tos.typing.modify",
):
    _cap(_name, PermissionClass.TOS_SENSITIVE, tos=True)
_cap("telegram.ephemeral.persist", PermissionClass.CRITICAL, tos=True)

_FORBIDDEN_CAPABILITIES = frozenset({
    "android.context", "java.reflect", "jni.handle", "native.load", "dex.load", "jar.load",
    "filesystem.raw", "process.exec", "network.socket", "tdlib.client", "mtproto.raw",
    "telegram.auth_key", "firebase.token", "webview.javascript_interface",
    "clipboard.background", "cross_account",
})


class Capability:
    """Namespace for the explicit capability registry."""

    @classmethod
    def spec(cls, name: str) -> CapabilitySpec:
        if not isinstance(name, str) or name not in _CAPABILITY_SPECS:
            raise UnknownDeclaration(f"unknown capability: {name!r}")
        return _CAPABILITY_SPECS[name]

    @classmethod
    def all(cls) -> Mapping[str, CapabilitySpec]:
        return MappingProxyType(_CAPABILITY_SPECS)

    @classmethod
    def is_known(cls, name: str) -> bool:
        return isinstance(name, str) and name in _CAPABILITY_SPECS


@dataclass(frozen=True)
class RawSurface:
    """Installed generated TDLib names against which raw manifests are checked."""

    functions: frozenset[str] = field(default_factory=frozenset)
    constructors: frozenset[str] = field(default_factory=frozenset)
    fields: frozenset[str] = field(default_factory=frozenset)
    enums: frozenset[str] = field(default_factory=frozenset)
    schema_hash: str | None = None
    tdlib_commit: str | None = None
    generator_version: str | None = None

    def __init__(self, functions: Iterable[str] = (), constructors: Iterable[str] = (),
                 fields: Iterable[str] = (), enums: Iterable[str] = (),
                 schema_hash: str | None = None, tdlib_commit: str | None = None,
                 generator_version: str | None = None, **kwargs: Any) -> None:
        # Accept generated-schema naming without weakening validation.
        if "updates" in kwargs:
            constructors = kwargs.pop("updates")
        if kwargs:
            raise TypeError(f"unknown RawSurface fields: {', '.join(kwargs)}")
        object.__setattr__(self, "functions", frozenset(_names(functions, "function")))
        object.__setattr__(self, "constructors", frozenset(_names(constructors, "constructor")))
        object.__setattr__(self, "fields", frozenset(_names(fields, "field")))
        object.__setattr__(self, "enums", frozenset(_names(enums, "enum")))
        object.__setattr__(self, "schema_hash", schema_hash)
        object.__setattr__(self, "tdlib_commit", tdlib_commit)
        object.__setattr__(self, "generator_version", generator_version)

    @classmethod
    def from_schema(cls, schema: Any) -> "RawSurface":
        """Build a surface from the generated ``core.raw.RawSchema`` shape."""
        constructors = getattr(schema, "constructors", None)
        functions = getattr(schema, "functions", None)
        if not isinstance(constructors, Mapping) or not isinstance(functions, Mapping):
            raise TypeError("schema must expose constructor and function mappings")
        fields = {
            f"{constructor_name}.{field_name}"
            for constructor_name, constructor in constructors.items()
            for field_name in getattr(constructor, "fields", {}).keys()
        }
        return cls(functions.keys(), constructors.keys(), fields,
                   schema_hash=getattr(schema, "schema_hash", None),
                   tdlib_commit=getattr(schema, "tdlib_commit", None),
                   generator_version=getattr(schema, "generator_version", None))


def _names(values: Iterable[str] | Mapping[str, Any], kind: str) -> tuple[str, ...]:
    if isinstance(values, Mapping):
        values = values.keys()
    result = tuple(values)
    if any(not isinstance(value, str) or not value for value in result):
        raise ValueError(f"{kind} names must be non-empty strings")
    return result


_SEMVER = re.compile(r"^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z.-]+))?$")
_PACKAGE_ID = re.compile(r"^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)+$")
_KEY_ID = re.compile(r"^sha256:[0-9a-f]{64}$")
_SHA256 = re.compile(r"^[0-9a-f]{64}$")
_DOMAIN = re.compile(r"^(?:\*\.)?(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$")


def _version(value: str) -> tuple[int, int, int, tuple[str, ...]]:
    if not isinstance(value, str) or not (match := _SEMVER.fullmatch(value)):
        raise ManifestError(f"invalid semantic version: {value!r}")
    return int(match[1]), int(match[2]), int(match[3]), tuple((match[4] or "").split(".")) if match[4] else ()


def _version_in_range(version: str, declared: Mapping[str, str]) -> bool:
    actual = _version(version)
    if "exact" in declared:
        return actual == _version(declared["exact"])
    if not any(key in declared for key in ("minimum_inclusive", "maximum_exclusive")):
        return False
    if "minimum_inclusive" in declared and actual < _version(declared["minimum_inclusive"]):
        return False
    if "maximum_exclusive" in declared and actual >= _version(declared["maximum_exclusive"]):
        return False
    return True


def _validate_json_object(value: Any, name: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ManifestError(f"{name} must be an object")
    return value


@dataclass(frozen=True)
class PackageIdentity:
    package_id: str
    publisher_key_id: str | None
    package_version: str
    signed: bool = True
    development_install_id: str | None = None

    def __post_init__(self) -> None:
        if not isinstance(self.package_id, str) or not _PACKAGE_ID.fullmatch(self.package_id):
            raise ManifestError("invalid package identity")
        _version(self.package_version)
        if self.signed:
            if not isinstance(self.publisher_key_id, str) or not _KEY_ID.fullmatch(self.publisher_key_id):
                raise TrustError("signed identity requires a valid publisher key ID")
            if self.development_install_id is not None:
                raise TrustError("signed identity cannot have a development install ID")
        elif not isinstance(self.development_install_id, str) or not self.development_install_id:
            raise TrustError("unsigned identity requires a host-generated development install ID")

    @property
    def binding(self) -> tuple[str, str]:
        if self.signed:
            assert self.publisher_key_id is not None
            return self.package_id, self.publisher_key_id
        assert self.development_install_id is not None
        return self.package_id, self.development_install_id

    @classmethod
    def development(cls, package_id: str, package_version: str = "0.0.0") -> "PackageIdentity":
        return cls(package_id, None, package_version, signed=False, development_install_id=uuid4().hex)


@dataclass(frozen=True)
class Manifest:
    schema_version: int
    package_id: str
    package_type: str
    version: str
    publisher_key_id: str
    capabilities: frozenset[str]
    semantic_api: Mapping[str, str]
    raw_functions: frozenset[str] = field(default_factory=frozenset)
    raw_updates: frozenset[str] = field(default_factory=frozenset)
    raw_fields: frozenset[str] = field(default_factory=frozenset)
    raw_enums: frozenset[str] = field(default_factory=frozenset)
    raw_schema_hash: str | None = None
    raw_tdlib_commit: str | None = None
    raw_generator_version: str | None = None
    network_domains: frozenset[str] = field(default_factory=frozenset)
    quotas: Mapping[str, int] = field(default_factory=dict)
    tos_sensitive: bool = False
    _document: Mapping[str, Any] = field(default_factory=dict, repr=False, compare=False)

    @property
    def surface_digest(self) -> str:
        surface = {
            "capabilities": sorted(self.capabilities),
            "raw": {"functions": sorted(self.raw_functions), "updates": sorted(self.raw_updates),
                    "fields": sorted(self.raw_fields), "enums": sorted(self.raw_enums)},
            "network": sorted(self.network_domains), "quotas": dict(sorted(self.quotas.items())),
        }
        return sha256(json.dumps(surface, sort_keys=True, separators=(",", ":")).encode()).hexdigest()

    @classmethod
    def from_json(cls, data: str | bytes, *, raw_surface: RawSurface | None = None) -> "Manifest":
        if isinstance(data, bytes):
            try:
                data = data.decode("utf-8", errors="strict")
            except UnicodeDecodeError as error:
                raise ManifestError("manifest must be UTF-8") from error
        if not isinstance(data, str):
            raise ManifestError("manifest JSON must be text")
        def reject_duplicates(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
            result: dict[str, Any] = {}
            for key, value in pairs:
                if key in result:
                    raise ManifestError(f"duplicate manifest key: {key}")
                result[key] = value
            return result
        try:
            document = json.loads(data, object_pairs_hook=reject_duplicates)
        except (json.JSONDecodeError, ManifestError) as error:
            raise ManifestError("invalid manifest JSON") from error
        return cls.from_dict(document, raw_surface=raw_surface)

    @classmethod
    def from_dict(cls, document: Mapping[str, Any], *, raw_surface: RawSurface | None = None) -> "Manifest":
        doc = _validate_json_object(document, "manifest")
        allowed = {"schema_version", "type", "id", "version", "name", "description", "publisher",
                   "entrypoint", "api", "capabilities", "raw", "network", "quotas", "hooks",
                   "dependencies", "ipc", "ui", "storage", "bundled_themes", "licenses", "issue_tracker"}
        unknown = set(doc) - allowed
        if unknown:
            raise ManifestError(f"unknown manifest fields: {', '.join(sorted(unknown))}")
        required = {"schema_version", "type", "id", "version", "name", "description", "publisher",
                    "entrypoint", "api", "capabilities", "licenses"}
        missing = required - set(doc)
        if missing:
            raise ManifestError(f"missing manifest fields: {', '.join(sorted(missing))}")
        if doc["schema_version"] != 1 or doc["type"] != "vibemod":
            raise ManifestError("unsupported manifest schema or type")
        package_id = doc["id"]
        if not isinstance(package_id, str) or not _PACKAGE_ID.fullmatch(package_id):
            raise ManifestError("invalid package ID")
        version = doc["version"]
        _version(version)
        _validate_localized(doc["name"], "name")
        _validate_localized(doc["description"], "description")
        entrypoint = doc["entrypoint"]
        if (not isinstance(entrypoint, str) or not entrypoint.endswith(".luau") or
                entrypoint.startswith("/") or "//" in entrypoint or ".." in entrypoint):
            raise ManifestError("invalid Luau entrypoint")
        publisher = _validate_json_object(doc["publisher"], "publisher")
        if set(publisher) != {"key_id"} or not _KEY_ID.fullmatch(str(publisher["key_id"])):
            raise ManifestError("invalid publisher key ID")
        api = _validate_json_object(doc["api"], "api")
        if set(api) - {"semantic", "raw", "gui"} or "semantic" not in api:
            raise ManifestError("invalid API declaration")
        semantic = _validate_range(api["semantic"], "semantic API")
        capabilities_value = doc["capabilities"]
        if not isinstance(capabilities_value, list) or len(set(capabilities_value)) != len(capabilities_value):
            raise ManifestError("capabilities must be a unique array")
        if len(capabilities_value) > 128:
            raise ManifestError("too many capabilities")
        capabilities: set[str] = set()
        tos_sensitive = False
        for name in capabilities_value:
            try:
                spec = Capability.spec(name)
            except UnknownDeclaration:
                if name in _FORBIDDEN_CAPABILITIES:
                    raise UnknownDeclaration(f"forbidden capability: {name}")
                raise
            capabilities.add(name)
            tos_sensitive |= spec.tos_sensitive
        raw = doc.get("raw", {})
        raw = _validate_object_keys(raw, "raw", {"functions", "updates", "fields", "enums"})
        raw_functions = _unique_names(raw.get("functions", []), "raw function", pattern=r"^[A-Za-z][A-Za-z0-9_]*$")
        raw_updates = _unique_names(raw.get("updates", []), "raw update", pattern=r"^[A-Za-z][A-Za-z0-9_]*$")
        raw_fields = _unique_names(raw.get("fields", []), "raw field", pattern=r"^[A-Za-z][A-Za-z0-9_]*\.[A-Za-z][A-Za-z0-9_]*$")
        raw_enums = _unique_names(raw.get("enums", []), "raw enum", pattern=r"^[A-Za-z][A-Za-z0-9_]*\.[A-Za-z][A-Za-z0-9_]*$")
        raw_api = api.get("raw", {})
        raw_api = _validate_object_keys(raw_api, "api.raw", {"tdlib_commit", "schema_hash", "generator_version"})
        if any(name.startswith("tdlib.raw.") for name in capabilities):
            if set(raw_api) != {"tdlib_commit", "schema_hash", "generator_version"}:
                raise ManifestError("raw capability requires api.raw compatibility")
            if not re.fullmatch(r"(?:[0-9a-f]{40}|[0-9a-f]{64})", str(raw_api["tdlib_commit"])):
                raise ManifestError("invalid raw TDLib commit")
            if not _SHA256.fullmatch(str(raw_api["schema_hash"])):
                raise ManifestError("invalid raw schema hash")
            _version(raw_api["generator_version"])
            if not raw_updates and any(name in capabilities for name in ("tdlib.raw.observe_incoming", "tdlib.raw.mutate_incoming", "tdlib.raw.suppress_incoming")):
                raise ManifestError("raw update capability requires update declarations")
            if not raw_fields and any(name in capabilities for name in ("tdlib.raw.objects", "tdlib.raw.invoke", "tdlib.raw.observe_incoming", "tdlib.raw.mutate_incoming")):
                raise ManifestError("raw data capability requires field declarations")
            if "tdlib.raw.invoke" in capabilities and not raw_functions:
                raise ManifestError("raw invoke requires function declarations")
        if raw_surface is not None:
            for kind, names, available in (("function", raw_functions, raw_surface.functions),
                                           ("update", raw_updates, raw_surface.constructors),
                                           ("field", raw_fields, raw_surface.fields),
                                           ("enum", raw_enums, raw_surface.enums)):
                unknown_names = set(names) - available
                if unknown_names:
                    raise UnknownDeclaration(f"unknown raw {kind}: {', '.join(sorted(unknown_names))}")
        network = doc.get("network", {})
        network = _validate_object_keys(network, "network", {"domains", "cookies"})
        domains = _unique_names(network.get("domains", []), "network domain")
        if any(not _DOMAIN.fullmatch(domain) for domain in domains):
            raise ManifestError("invalid network domain")
        if "network.https" in capabilities and not domains:
            raise ManifestError("network.https requires declared domains")
        quotas = _validate_quotas(doc.get("quotas", {}))
        if "network.https" not in capabilities and domains:
            raise ManifestError("network domains require network.https")
        if "ipc" in doc:
            ipc = _validate_object_keys(doc["ipc"], "ipc", {"provides", "consumes"})
            if not ipc:
                raise ManifestError("IPC declaration cannot be empty")
            ipc_allowlist = {name for name in capabilities if name.startswith("ipc.") or name.startswith("ui.")
                             or name.startswith("storage.") or name in {"settings.global_safe", "diagnostics.own"}}
            if set(capabilities) - ipc_allowlist:
                raise ManifestError("IPC packages cannot request Telegram, event, or network capabilities")
            if not (set(capabilities) & {"ipc.provide", "ipc.consume"}):
                raise ManifestError("IPC declaration requires ipc.provide or ipc.consume")
        elif set(capabilities) & {"ipc.provide", "ipc.consume"}:
            raise ManifestError("IPC capability requires an IPC declaration")
        return cls(1, package_id, "vibemod", version, publisher["key_id"], frozenset(capabilities),
                   MappingProxyType(dict(semantic)), frozenset(raw_functions), frozenset(raw_updates),
                   frozenset(raw_fields), frozenset(raw_enums), raw_api.get("schema_hash"),
                   raw_api.get("tdlib_commit"), raw_api.get("generator_version"), frozenset(domains),
                   MappingProxyType(quotas), tos_sensitive, MappingProxyType(dict(doc)))

    def to_dict(self) -> dict[str, Any]:
        return dict(self._document)


def _validate_object_keys(value: Any, name: str, allowed: set[str]) -> dict[str, Any]:
    value = _validate_json_object(value, name)
    unknown = set(value) - allowed
    if unknown:
        raise ManifestError(f"unknown {name} fields: {', '.join(sorted(unknown))}")
    return value


def _validate_range(value: Any, name: str) -> dict[str, str]:
    value = _validate_json_object(value, name)
    if set(value) == {"exact"}:
        _version(value["exact"])
        return {"exact": value["exact"]}
    if not set(value) <= {"minimum_inclusive", "maximum_exclusive"} or not value:
        raise ManifestError(f"invalid {name} range")
    if "minimum_inclusive" in value:
        _version(value["minimum_inclusive"])
    if "maximum_exclusive" in value:
        _version(value["maximum_exclusive"])
    if set(value) == {"minimum_inclusive", "maximum_exclusive"} and _version(value["minimum_inclusive"]) >= _version(value["maximum_exclusive"]):
        raise ManifestError(f"empty {name} range")
    return dict(value)


def _validate_localized(value: Any, name: str) -> None:
    value = _validate_json_object(value, name)
    if "en" not in value or any(not isinstance(key, str) or not isinstance(item, str) or not item
                                 for key, item in value.items()):
        raise ManifestError(f"invalid localized {name}")


def _unique_names(value: Any, name: str, *, pattern: str | None = None) -> tuple[str, ...]:
    if not isinstance(value, list) or len(set(value)) != len(value):
        raise ManifestError(f"{name}s must be a unique array")
    if any(not isinstance(item, str) or not item or len(item) > 160 or
           (pattern is not None and not re.fullmatch(pattern, item)) for item in value):
        raise ManifestError(f"invalid {name}")
    return tuple(value)


def _validate_quotas(value: Any) -> dict[str, int]:
    value = _validate_object_keys(value, "quotas", set(QuotaLimits.NAMES))
    result: dict[str, int] = {}
    for name, number in value.items():
        if isinstance(number, bool) or not isinstance(number, int) or number < 0:
            raise ManifestError(f"quota {name} must be a non-negative integer")
        result[name] = number
    return result


@dataclass(frozen=True)
class QuotaLimits:
    calls_per_minute: int = 600
    bytes_per_day: int = 50 * 1024 * 1024
    concurrent_async: int = 32
    memory_bytes: int = 4 * 1024 * 1024
    storage_bytes: int = 16 * 1024 * 1024
    reliable_journal_bytes: int = 16 * 1024 * 1024
    network_bytes_per_day: int = 50 * 1024 * 1024
    async_operations: int = 32
    ui_nodes: int = 10_000

    NAMES = ("calls_per_minute", "bytes_per_day", "concurrent_async", "memory_bytes", "storage_bytes",
             "reliable_journal_bytes", "network_bytes_per_day", "async_operations", "ui_nodes")

    def __post_init__(self) -> None:
        for name in self.NAMES:
            value = getattr(self, name)
            if isinstance(value, bool) or not isinstance(value, int) or value < 0:
                raise ValueError(f"invalid quota limit: {name}")


class _QuotaLedger:
    def __init__(self, limits: QuotaLimits, requested: Mapping[str, int]) -> None:
        self.limits = limits
        self.values: dict[str, int] = {}
        for name in QuotaLimits.NAMES:
            limit = getattr(limits, name)
            if name in requested and requested[name] > limit:
                raise QuotaExceeded(f"requested {name} exceeds host limit")
            self.values[name] = requested.get(name, limit)
        self.usage: dict[str, int] = {name: 0 for name in QuotaLimits.NAMES}
        self._lock = threading.Lock()

    def consume(self, *, calls: int = 1, bytes_count: int = 0, network_bytes: int = 0,
                async_operations: int = 0, ui_nodes: int = 0, storage_bytes: int = 0,
                reliable_journal_bytes: int = 0) -> None:
        increments = {"calls_per_minute": calls, "bytes_per_day": bytes_count,
                      "network_bytes_per_day": network_bytes, "async_operations": async_operations,
                      "ui_nodes": ui_nodes, "storage_bytes": storage_bytes,
                      "reliable_journal_bytes": reliable_journal_bytes}
        if any(isinstance(value, bool) or not isinstance(value, int) or value < 0 for value in increments.values()):
            raise ValueError("quota increments must be non-negative integers")
        with self._lock:
            for name, amount in increments.items():
                if self.usage[name] + amount > self.values[name]:
                    raise QuotaExceeded(f"quota exceeded: {name}")
            for name, amount in increments.items():
                self.usage[name] += amount

    def release_async(self) -> None:
        with self._lock:
            self.usage["async_operations"] = max(0, self.usage["async_operations"] - 1)


class QuotaManager:
    """Public quota seam for bridges that account work outside PolicyEngine."""

    def __init__(self, limits: QuotaLimits | None = None, requested: Mapping[str, int] | None = None) -> None:
        self._ledger = _QuotaLedger(limits or QuotaLimits(), requested or {})

    def consume(self, **increments: int) -> None:
        self._ledger.consume(**increments)

    def release_async(self) -> None:
        self._ledger.release_async()

    @property
    def usage(self) -> Mapping[str, int]:
        return MappingProxyType(dict(self._ledger.usage))


def check_dangerous_combinations(capabilities: Iterable[str]) -> tuple[str, ...]:
    """Return explicit data-flow warnings for a declared capability set."""
    active = frozenset(capabilities)
    result: list[str] = []
    if "network.https" in active and ({"telegram.messages.read_content", "tdlib.raw.observe_incoming"} & active):
        result.append("telegram-content-to-network")
    if "network.https" in active and ({"events.reliable", "telegram.messages.observe"} & active):
        result.append("reliable-telegram-data-to-network")
    return tuple(result)


class Redactor:
    """Small reusable fail-closed redaction adapter for host model bridges."""

    @staticmethod
    def apply(value: Mapping[str, Any], field_classes: Mapping[str, PermissionClass], *,
              granted_class: PermissionClass) -> dict[str, Any]:
        if not isinstance(granted_class, PermissionClass):
            raise UnknownDeclaration("unknown granted permission class")
        result: dict[str, Any] = {}
        for name, item in value.items():
            if name not in field_classes:
                raise UnknownDeclaration(f"unknown field for redaction: {name}")
            cls = field_classes[name]
            if not isinstance(cls, PermissionClass):
                raise UnknownDeclaration(f"unknown permission class for field: {name}")
            if cls != PermissionClass.FORBIDDEN and cls <= granted_class:
                result[name] = item
        return result


@dataclass(frozen=True)
class PromptDescriptor:
    capability: str
    permission_class: PermissionClass
    account_handle: str
    target: str | None
    effects: str
    lifetime_options: tuple[GrantLifetime, ...]
    tos_warning: bool = False


@dataclass(frozen=True)
class Grant:
    identity_binding: tuple[str, str]
    account_handle: str
    capability: str
    lifetime: GrantLifetime
    surface_digest: str
    issued_at: float
    expires_at: float | None = None
    revoked: bool = False
    token: str = field(default_factory=lambda: uuid4().hex)

    def valid(self, *, now: float, identity: PackageIdentity, account: str, surface_digest: str,
              mode_enabled: bool) -> bool:
        return (not self.revoked and self.identity_binding == identity.binding and
                self.account_handle == account and self.surface_digest == surface_digest and
                (self.expires_at is None or now < self.expires_at) and
                (self.lifetime != GrantLifetime.MODE or mode_enabled))


class GrantStore:
    """Thread-safe grants scoped by verified package identity and account."""

    def __init__(self) -> None:
        self._grants: dict[tuple[tuple[str, str], str, str], list[Grant]] = {}
        self._revoked: set[tuple[tuple[str, str], str, str]] = set()
        self._lock = threading.RLock()

    def grant(self, identity: PackageIdentity, account_handle: str, capability: str,
              lifetime: GrantLifetime, *, surface_digest: str, now: float | None = None) -> Grant:
        Capability.spec(capability)
        if not isinstance(lifetime, GrantLifetime):
            raise ValueError("invalid grant lifetime")
        issued = time.time() if now is None else now
        expires = issued + 1.0 if lifetime in (GrantLifetime.ONCE, GrantLifetime.OPERATION) else None
        grant = Grant(identity.binding, account_handle, capability, lifetime, surface_digest, issued, expires)
        key = (identity.binding, account_handle, capability)
        with self._lock:
            self._revoked.discard(key)
            self._grants.setdefault(key, []).append(grant)
        return grant

    def has(self, identity: PackageIdentity, account_handle: str, capability: str, *,
            surface_digest: str, mode_enabled: bool, now: float | None = None,
            consume_operation: bool = False) -> bool:
        current = time.time() if now is None else now
        key = (identity.binding, account_handle, capability)
        with self._lock:
            if key in self._revoked:
                return False
            grants = self._grants.get(key, [])
            for item in reversed(grants):
                if item.valid(now=current, identity=identity, account=account_handle,
                             surface_digest=surface_digest, mode_enabled=mode_enabled):
                    if consume_operation or item.lifetime == GrantLifetime.ONCE:
                        grants.remove(item)
                    return True
            return False

    def revoke(self, identity: PackageIdentity, account_handle: str, capability: str) -> None:
        key = (identity.binding, account_handle, capability)
        with self._lock:
            self._revoked.add(key)
            for item in self._grants.get(key, []):
                # Frozen grants are replaced with revoked copies so readers that hold
                # an old object cannot treat it as current.
                self._grants[key] = [Grant(g.identity_binding, g.account_handle, g.capability, g.lifetime,
                                           g.surface_digest, g.issued_at, g.expires_at, True, g.token)
                                     for g in self._grants[key]]
                break

    def revoke_identity(self, identity: PackageIdentity) -> None:
        with self._lock:
            keys = [key for key in self._grants if key[0] == identity.binding]
            for _, account, capability in keys:
                self.revoke(identity, account, capability)

    def invalidate_surface(self, identity: PackageIdentity, account_handle: str, surface_digest: str) -> None:
        with self._lock:
            for key, grants in list(self._grants.items()):
                if key[0] == identity.binding and key[1] == account_handle:
                    self._grants[key] = [g for g in grants if g.surface_digest == surface_digest]


@dataclass(frozen=True)
class PolicyRequest:
    capability: str
    account_handle: str | None = None
    function: str | None = None
    update_constructor: str | None = None
    fields: tuple[str, ...] = ()
    enum_values: tuple[str, ...] = ()
    target: str | None = None
    input_bytes: int = 0
    output_bytes: int = 0
    async_operation: bool = False
    ui_nodes: int = 0
    storage_bytes: int = 0
    reliable_journal_bytes: int = 0
    confirmed: bool = False


@dataclass(frozen=True)
class PolicyDecision:
    allowed: bool
    effective_class: PermissionClass
    requires_prompt: bool
    requires_confirmation: bool
    tos_sensitive: bool
    reason: str
    prompt: PromptDescriptor | None = None
    dangerous_combinations: tuple[str, ...] = ()
    redacted_fields: tuple[str, ...] = ()
    error: Exception | None = None
    grant: Grant | None = None


def _effective_class(classes: Iterable[PermissionClass], tos: bool) -> PermissionClass:
    values = tuple(classes)
    if PermissionClass.FORBIDDEN in values:
        return PermissionClass.FORBIDDEN
    non_tos = tuple(value for value in values if value != PermissionClass.TOS_SENSITIVE)
    highest = max(non_tos, default=PermissionClass.SAFE)
    return PermissionClass.TOS_SENSITIVE if tos and highest < PermissionClass.CRITICAL else highest


class PolicyEngine:
    """Evaluate one manifest bound to one trusted package and account."""

    def __init__(self, manifest: Manifest, identity: PackageIdentity, *, account_handle: str,
                 trusted: bool = True, developer_mode: bool = False, modification_mode: bool = True,
                 semantic_api_version: str | None = None, raw_surface: RawSurface | None = None,
                 quota_limits: QuotaLimits | None = None, grants: GrantStore | None = None,
                 mode_authority: object = None) -> None:
        if identity.package_id != manifest.package_id or identity.package_version != manifest.version:
            raise TrustError("package identity does not match manifest")
        if identity.signed and identity.publisher_key_id != manifest.publisher_key_id:
            raise TrustError("publisher identity does not match manifest")
        if not identity.signed and not developer_mode:
            raise TrustError("unsigned packages require Developer Mode")
        if identity.signed and not trusted:
            raise TrustError("package publisher is not trusted")
        if not isinstance(account_handle, str) or not account_handle:
            raise AccountBindingError("account handle must be host-bound and non-empty")
        if semantic_api_version is not None and not _version_in_range(semantic_api_version, manifest.semantic_api):
            raise CompatibilityError("semantic Mod API is incompatible")
        if any(name.startswith("tdlib.raw.") for name in manifest.capabilities) and raw_surface is None:
            raise CompatibilityError("raw capabilities require the installed generated TDLib surface")
        if raw_surface is not None:
            self._check_raw_compatibility(manifest, raw_surface, developer_mode)
            # Re-run declaration checks here even if the manifest was parsed before
            # the generated schema became available.
            manifest = Manifest.from_dict(manifest.to_dict(), raw_surface=raw_surface)
        self.manifest = manifest
        self._raw_surface = raw_surface
        self.identity = identity
        self.account_handle = account_handle
        self.trusted = trusted
        self.developer_mode = developer_mode
        self._modification_mode = modification_mode
        self.grants = grants or GrantStore()
        self._mode_authority = mode_authority
        self._quota = _QuotaLedger(quota_limits or QuotaLimits(), manifest.quotas)
        self._lock = threading.RLock()
        self._used_confirmations: set[tuple[Any, ...]] = set()

    @staticmethod
    def _check_raw_compatibility(manifest: Manifest, surface: RawSurface, developer_mode: bool) -> None:
        if not any(name.startswith("tdlib.raw.") for name in manifest.capabilities):
            return
        fields = ((manifest.raw_schema_hash, surface.schema_hash, "schema hash"),
                  (manifest.raw_tdlib_commit, surface.tdlib_commit, "TDLib commit"),
                  (manifest.raw_generator_version, surface.generator_version, "generator version"))
        for requested, installed, label in fields:
            if requested is None or installed is None:
                raise CompatibilityError(f"raw {label} is missing")
            # Developer Mode can explicitly force a generated schema hash, but
            # it cannot run against a different TDLib commit or generator.
            if requested != installed and (label != "schema hash" or not developer_mode):
                raise CompatibilityError(f"raw {label} mismatch")

    @property
    def modification_mode(self) -> bool:
        with self._lock:
            return self._modification_mode

    def set_modification_mode(self, enabled: bool, *, authority: object = None) -> None:
        """Host-authoritative mode transition.

        The host UI drives mode changes through this seam with the mode
        authority token it was constructed with; addon code and bridges can
        only ever disable the engine (fail closed). Re-enabling without the
        host authority is rejected instead of silently accepted.
        """
        if not isinstance(enabled, bool):
            raise TypeError("modification mode must be boolean")
        # Fail closed: an engine without a host authority can never be
        # re-enabled, and one with an authority only accepts its own token.
        if enabled and (self._mode_authority is None or authority is not self._mode_authority):
            raise PolicyError("Modification Mode can only be enabled by the host")
        with self._lock:
            self._modification_mode = enabled

    def authorize(self, request: PolicyRequest, *, now: float | None = None) -> PolicyDecision:
        try:
            return self._authorize(request, now=time.time() if now is None else now)
        except PolicyError as error:
            return PolicyDecision(False, PermissionClass.FORBIDDEN, False, False, False, str(error), error=error)
        except (TypeError, ValueError) as error:
            # Input validation is fail-closed and returned as a denial, not a
            # partially-authorized operation.
            return PolicyDecision(False, PermissionClass.FORBIDDEN, False, False, False, str(error), error=error)

    def _authorize(self, request: PolicyRequest, *, now: float) -> PolicyDecision:
        if not isinstance(request, PolicyRequest):
            raise TypeError("policy request is required")
        spec = Capability.spec(request.capability)
        with self._lock:
            mode_enabled = self._modification_mode
        if not mode_enabled:
            return PolicyDecision(False, PermissionClass.FORBIDDEN, False, False, False,
                                  "Modification Mode disabled")
        if request.capability not in self.manifest.capabilities:
            raise UnknownDeclaration(f"capability not declared by manifest: {request.capability}")
        if request.account_handle is not None and request.account_handle != self.account_handle:
            raise AccountBindingError("account handle does not match bound account")
        self._validate_request_names(request)
        classes = [spec.permission_class]
        tos = spec.tos_sensitive
        if request.function:
            classes.append(PermissionClass.SENSITIVE_WRITE)
        dangerous = self._dangerous_combinations(request.capability)
        effective = _effective_class(classes, tos)
        if spec.permission_class == PermissionClass.FORBIDDEN:
            raise UnknownDeclaration(f"forbidden capability: {request.capability}")
        if dangerous:
            effective = max(effective, PermissionClass.SENSITIVE_WRITE)
        needs_confirmation = effective == PermissionClass.CRITICAL or tos or bool(dangerous)
        needs_prompt = effective in (PermissionClass.SENSITIVE_READ, PermissionClass.SENSITIVE_WRITE,
                                     PermissionClass.CRITICAL, PermissionClass.TOS_SENSITIVE) or bool(dangerous)
        grant = None
        # Critical capabilities are never satisfied by a standing grant.  A
        # fresh confirmation is their one-operation grant.  ToS-sensitive
        # operations still need the explicit addon grant as a second gate.
        grant_required = spec.permission_class not in (PermissionClass.SAFE, PermissionClass.CRITICAL) or tos
        if grant_required:
            if not self.grants.has(self.identity, self.account_handle, request.capability,
                                   surface_digest=self.manifest.surface_digest, mode_enabled=mode_enabled,
                                   now=now, consume_operation=effective == PermissionClass.CRITICAL):
                prompt = self._prompt(spec, request, needs_confirmation)
                return PolicyDecision(False, effective, needs_prompt, needs_confirmation, tos,
                                      "grant missing", prompt=prompt, dangerous_combinations=dangerous)
        if needs_confirmation and not request.confirmed:
            return PolicyDecision(False, effective, needs_prompt, True, tos, "fresh confirmation required",
                                  prompt=self._prompt(spec, request, True), dangerous_combinations=dangerous)
        confirmation_key = (request.capability, request.target, request.function,
                            request.update_constructor, request.fields)
        with self._lock:
            already_confirmed = confirmation_key in self._used_confirmations
        if needs_confirmation and already_confirmed:
            return PolicyDecision(False, effective, needs_prompt, True, tos,
                                  "fresh confirmation required",
                                  prompt=self._prompt(spec, request, True),
                                  dangerous_combinations=dangerous)
        self._quota.consume(calls=1, bytes_count=request.input_bytes + request.output_bytes,
                            network_bytes=request.input_bytes + request.output_bytes if request.capability == "network.https" else 0,
                            async_operations=1 if request.async_operation else 0,
                            ui_nodes=request.ui_nodes, storage_bytes=request.storage_bytes,
                            reliable_journal_bytes=request.reliable_journal_bytes)
        if needs_confirmation and request.confirmed:
            with self._lock:
                self._used_confirmations.add(confirmation_key)
        return PolicyDecision(True, effective, needs_prompt, needs_confirmation, tos, "authorized",
                              dangerous_combinations=dangerous, grant=grant)

    def _validate_request_names(self, request: PolicyRequest) -> None:
        # Raw names are checked against the parsed manifest.  Manifest parsing
        # also checks them against the installed generated surface.
        if request.function is not None and request.function not in self.manifest.raw_functions:
            raise UnknownDeclaration(f"function not declared by manifest: {request.function}")
        if request.update_constructor is not None and request.update_constructor not in self.manifest.raw_updates:
            raise UnknownDeclaration(f"update not declared by manifest: {request.update_constructor}")
        for item in request.fields:
            if item not in self.manifest.raw_fields:
                raise UnknownDeclaration(f"field not declared by manifest: {item}")
        for item in request.enum_values:
            if item not in self.manifest.raw_enums:
                raise UnknownDeclaration(f"enum not declared by manifest: {item}")
        for value in (request.input_bytes, request.output_bytes, request.ui_nodes,
                      request.storage_bytes, request.reliable_journal_bytes):
            if isinstance(value, bool) or not isinstance(value, int) or value < 0:
                raise ValueError("request quota values must be non-negative integers")

    def _dangerous_combinations(self, requested: str) -> tuple[str, ...]:
        active = self.manifest.capabilities
        result: list[str] = []
        if requested == "network.https" and ({"telegram.messages.read_content", "tdlib.raw.observe_incoming"} & active):
            result.append("telegram-content-to-network")
        if requested == "network.https" and ("events.reliable" in active or "telegram.messages.observe" in active):
            result.append("reliable-telegram-data-to-network")
        if requested in {"events.reliable", "telegram.messages.observe"} and "network.https" in active:
            result.append("reliable-telegram-data-to-network")
        return tuple(result)

    def _prompt(self, spec: CapabilitySpec, request: PolicyRequest, critical: bool) -> PromptDescriptor:
        if critical:
            lifetimes = (GrantLifetime.OPERATION,)
        else:
            lifetimes = (GrantLifetime.ONCE, GrantLifetime.MODE, GrantLifetime.PERSISTENT)
        effects = f"{spec.name} for account {self.account_handle}"
        if request.target:
            effects += f"; target {request.target}"
        return PromptDescriptor(spec.name, spec.permission_class, self.account_handle, request.target,
                                effects, lifetimes, spec.tos_sensitive)

    def redact(self, value: Mapping[str, Any], field_classes: Mapping[str, PermissionClass], *,
               granted_class: PermissionClass, immutable: bool = True) -> dict[str, Any]:
        if not isinstance(value, Mapping) or not isinstance(field_classes, Mapping):
            raise TypeError("redaction requires mappings")
        result: dict[str, Any] = {}
        for name, item in value.items():
            if name not in field_classes:
                raise UnknownDeclaration(f"unknown field for redaction: {name}")
            cls = field_classes[name]
            if not isinstance(cls, PermissionClass):
                raise UnknownDeclaration(f"unknown permission class for field: {name}")
            if cls == PermissionClass.FORBIDDEN or cls > granted_class:
                continue
            result[name] = item
        return result

    def revoke(self, capability: str) -> None:
        Capability.spec(capability)
        self.grants.revoke(self.identity, self.account_handle, capability)

    @property
    def quota_usage(self) -> Mapping[str, int]:
        return MappingProxyType(dict(self._quota.usage))


# Compatibility aliases used by early SDK consumers.
PermissionEngine = PolicyEngine
ManifestDeclaration = Manifest
Decision = PolicyDecision

__all__ = [
    "AccountBindingError", "Capability", "CapabilitySpec", "CompatibilityError", "Decision",
    "Grant", "GrantLifetime", "GrantStore", "Manifest", "ManifestDeclaration", "ManifestError",
    "PackageIdentity", "PermissionClass", "PermissionEngine", "PolicyDecision", "PolicyEngine",
    "PolicyError", "PolicyRequest", "PromptDescriptor", "QuotaExceeded", "QuotaLimits", "RawSurface",
    "QuotaManager", "Redactor", "TrustError", "UnknownDeclaration", "check_dangerous_combinations",
]
