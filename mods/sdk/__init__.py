"""Deterministic public Luau type/facade generation and host-bound context."""
from __future__ import annotations

import hashlib
import json
import re
from dataclasses import dataclass
from types import MappingProxyType
from typing import Any, Mapping


class SdkError(ValueError):
    """Malformed SDK description."""


class SdkPermissionError(PermissionError):
    """A facade was not granted to the addon instance."""


_NAME = re.compile(r"^[A-Za-z][A-Za-z0-9_]*$")
_SUPPORTED_TYPES = frozenset({"string", "number", "boolean", "nil", "UiNode"})
_FORBIDDEN_CAPABILITIES = frozenset({
    "android.context", "java.reflect", "jni.handle", "native.load", "filesystem.raw",
    "process.exec", "network.socket", "tdlib.client", "mtproto.raw", "webview.javascript_interface",
})
_ALLOWED_CAPABILITY_PREFIXES = ("ui.", "ipc.", "storage.", "events.", "telegram.", "tdlib.raw.")
_HOST_CONTEXT_TOKEN = object()


def _name(value: str, label: str) -> str:
    if not isinstance(value, str) or not _NAME.fullmatch(value):
        raise SdkError(f"invalid {label}: {value!r}")
    return value


@dataclass(frozen=True)
class TypeSpec:
    name: str
    fields: tuple[tuple[str, str], ...]

    def __post_init__(self) -> None:
        _name(self.name, "type name")
        fields = tuple(self.fields)
        if len({field[0] for field in fields}) != len(fields):
            raise SdkError("type fields must be unique")
        for field_name, type_name in fields:
            _name(field_name, "field name")
            if type_name not in _SUPPORTED_TYPES and not _NAME.fullmatch(type_name):
                raise SdkError(f"unsupported Luau type: {type_name!r}")


@dataclass(frozen=True)
class MethodSpec:
    name: str
    parameters: tuple[str, ...]
    result: str

    def __post_init__(self) -> None:
        _name(self.name, "method name")
        for item in self.parameters:
            if item not in _SUPPORTED_TYPES and not _NAME.fullmatch(item):
                raise SdkError(f"unsupported method parameter type: {item!r}")
        if self.result not in _SUPPORTED_TYPES and not _NAME.fullmatch(self.result):
            raise SdkError(f"unsupported method result type: {self.result!r}")


@dataclass(frozen=True)
class FacadeSpec:
    name: str
    capability: str
    methods: tuple[MethodSpec, ...]

    def __post_init__(self) -> None:
        _name(self.name, "facade name")
        if (not isinstance(self.capability, str) or not self.capability
                or self.capability in _FORBIDDEN_CAPABILITIES
                or not (self.capability.startswith(_ALLOWED_CAPABILITY_PREFIXES)
                        or self.capability in {"settings.global_safe", "diagnostics.own"})):
            raise SdkError("invalid or forbidden facade capability")
        if len({method.name for method in self.methods}) != len(self.methods):
            raise SdkError("facade methods must be unique")


@dataclass(frozen=True)
class SdkSpec:
    version: str
    types: tuple[TypeSpec, ...] = ()
    facades: tuple[FacadeSpec, ...] = ()

    def __post_init__(self) -> None:
        if not isinstance(self.version, str) or not re.fullmatch(r"\d+\.\d+\.\d+", self.version):
            raise SdkError("SDK version must be canonical SemVer")
        if len({item.name for item in self.types}) != len(self.types):
            raise SdkError("SDK types must be unique")
        if len({item.name for item in self.facades}) != len(self.facades):
            raise SdkError("SDK facades must be unique")


@dataclass(frozen=True)
class GeneratedSdk:
    types_luau: str
    facades_luau: str
    source_sha256: str


def generate_luau_sdk(spec: SdkSpec) -> GeneratedSdk:
    """Generate stable source from a typed description, without executable host code."""
    if not isinstance(spec, SdkSpec):
        raise TypeError("spec must be SdkSpec")
    types: list[str] = [f"-- Generated VibeTGram Mod SDK {spec.version}", ""]
    for item in sorted(spec.types, key=lambda value: value.name):
        types.append(f"export type {item.name} = {{")
        for field_name, type_name in sorted(item.fields):
            types.append(f"    {field_name}: {type_name},")
        types.extend(["}", ""])
    types_luau = "\n".join(types).rstrip() + "\n"

    facades: list[str] = [f"-- Generated VibeTGram Mod SDK facades {spec.version}", ""]
    for facade in sorted(spec.facades, key=lambda value: value.name):
        facades.append(f"local {facade.name} = {{}}")
        for method in sorted(facade.methods, key=lambda value: value.name):
            params = ", ".join(f"arg{i + 1}: {type_name}" for i, type_name in enumerate(method.parameters))
            args = ", ".join(f"arg{i + 1}" for i in range(len(method.parameters)))
            facades.append(f"function {facade.name}.{method.name}({params}): {method.result}")
            facades.append(f"    return __host_invoke({json.dumps(facade.name)}, {json.dumps(method.name)}, {{{args}}})")
            facades.append("end")
        facades.append("")
    facades.append("return {" + ", ".join(f"{item.name} = {item.name}" for item in sorted(spec.facades, key=lambda value: value.name)) + "}")
    facades_luau = "\n".join(facades).rstrip() + "\n"
    digest = hashlib.sha256((types_luau + "\n" + facades_luau).encode("utf-8")).hexdigest()
    return GeneratedSdk(types_luau, facades_luau, digest)


class CapabilityFacade:
    """Minimal runtime facade; host dispatch supplies the actual implementation."""

    def __init__(self, name: str, capability: str, dispatcher: Any = None) -> None:
        self.name = _name(name, "facade name")
        self.capability = capability
        self._dispatcher = dispatcher

    def invoke(self, method: str, payload: Mapping[str, Any] | None = None) -> None:
        _name(method, "method name")
        if payload is not None and not isinstance(payload, Mapping):
            raise TypeError("facade payload must be a mapping")
        if self._dispatcher is None:
            return None
        return self._dispatcher(self.name, method, payload)


class ModContext:
    """Host-created capability view for one addon/account state."""

    def __init__(self, capabilities: frozenset[str], *, _host_token: object, dispatcher: Any = None) -> None:
        if _host_token is not _HOST_CONTEXT_TOKEN:
            raise SdkPermissionError("ModContext can only be created by the host")
        if any(not isinstance(item, str) or item in _FORBIDDEN_CAPABILITIES for item in capabilities):
            raise SdkPermissionError("invalid or forbidden capability")
        self._capabilities = frozenset(capabilities)
        self._dispatcher = dispatcher

    @property
    def capabilities(self) -> frozenset[str]:
        return self._capabilities

    def facade(self, name: str, *, capability: str) -> CapabilityFacade:
        if capability not in self._capabilities:
            raise SdkPermissionError(f"capability not granted: {capability}")
        if capability in _FORBIDDEN_CAPABILITIES:
            raise SdkPermissionError("forbidden capability")
        return CapabilityFacade(name, capability, self._dispatcher)


class HostContextFactory:
    """Host-only constructor for a capability-scoped public context."""

    @staticmethod
    def create(*, capabilities: set[str] | frozenset[str], dispatcher: Any = None) -> ModContext:
        return ModContext(frozenset(capabilities), _host_token=_HOST_CONTEXT_TOKEN, dispatcher=dispatcher)


__all__ = [
    "CapabilityFacade", "FacadeSpec", "GeneratedSdk", "HostContextFactory", "MethodSpec", "ModContext", "SdkError",
    "SdkPermissionError", "SdkSpec", "TypeSpec", "generate_luau_sdk",
]
