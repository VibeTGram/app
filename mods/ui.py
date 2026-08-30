"""Fail-closed declarative UI contract for addon-owned surfaces.

The renderer consumes this data model; addons never provide Compose/View
objects or callbacks.  Actions are opaque, addon-local string identifiers and
are dispatched by the host after capability checks.
"""
from __future__ import annotations

import json
import math
import re
import time
from collections import deque
from dataclasses import dataclass, field
from types import MappingProxyType
from typing import Any, Iterable, Mapping


class UiValidationError(ValueError):
    """A UI tree, route, slot, or update violates the host contract."""


@dataclass(frozen=True)
class UiLimits:
    max_nodes: int = 100
    max_depth: int = 8
    max_text_length: int = 2048
    max_updates_per_second: int = 30

    def __post_init__(self) -> None:
        for name in ("max_nodes", "max_depth", "max_text_length", "max_updates_per_second"):
            value = getattr(self, name)
            if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
                raise ValueError(f"{name} must be a positive integer")


@dataclass(frozen=True)
class UiNode:
    """Immutable, JSON-compatible node in the addon UI tree."""

    kind: str
    text_value: str | None = None
    action_id: str | None = None
    content_description: str | None = None
    icon_name: str | None = None
    children: tuple["UiNode", ...] = ()
    props: Mapping[str, Any] = field(default_factory=dict, repr=False)

    def __post_init__(self) -> None:
        allowed_kinds = {"text", "button", "icon", "badge", "input", "card", "column", "row"}
        if self.kind not in allowed_kinds:
            raise UiValidationError(f"unknown UI node kind: {self.kind!r}")
        if not isinstance(self.children, tuple) or any(not isinstance(item, UiNode) for item in self.children):
            raise TypeError("children must be UiNode values")
        if not isinstance(self.props, Mapping):
            raise TypeError("node props must be a mapping")
        allowed_props = {
            "text": {"style"}, "button": {"variant"}, "badge": {"variant"},
            "input": {"initial_value"}, "column": {"spacing_dp"}, "row": {"spacing_dp"},
            "icon": set(), "card": set(),
        }[self.kind]
        unknown = set(self.props) - allowed_props
        if unknown:
            raise UiValidationError(f"unknown properties for {self.kind}: {', '.join(sorted(unknown))}")
        object.__setattr__(self, "props", MappingProxyType(dict(self.props)))

    @classmethod
    def text(cls, value: str, *, style: str = "body", content_description: str | None = None) -> "UiNode":
        return cls("text", text_value=value, content_description=content_description, props={"style": style})

    @classmethod
    def button(cls, label: str, action_id: str, *, variant: str = "filled", content_description: str | None = None) -> "UiNode":
        if not isinstance(action_id, str):
            raise TypeError("action_id must be a string; executable callbacks are not allowed")
        return cls("button", text_value=label, action_id=action_id,
                   content_description=content_description, props={"variant": variant})

    @classmethod
    def icon(cls, name: str, *, content_description: str | None = None) -> "UiNode":
        return cls("icon", icon_name=name, content_description=content_description)

    @classmethod
    def badge(cls, value: str, *, variant: str = "default") -> "UiNode":
        return cls("badge", text_value=value, props={"variant": variant})

    @classmethod
    def input(cls, placeholder: str, action_id: str, *, initial_value: str = "", content_description: str | None = None) -> "UiNode":
        if not isinstance(action_id, str):
            raise TypeError("action_id must be a string; executable callbacks are not allowed")
        return cls("input", text_value=placeholder, action_id=action_id,
                   content_description=content_description, props={"initial_value": initial_value})

    @classmethod
    def card(cls, *children: "UiNode", content_description: str | None = None) -> "UiNode":
        return cls("card", children=tuple(children), content_description=content_description)

    @classmethod
    def column(cls, *children: "UiNode", spacing_dp: int = 8) -> "UiNode":
        return cls("column", children=tuple(children), props={"spacing_dp": spacing_dp})

    @classmethod
    def row(cls, *children: "UiNode", spacing_dp: int = 8) -> "UiNode":
        return cls("row", children=tuple(children), props={"spacing_dp": spacing_dp})

    def to_dict(self) -> dict[str, Any]:
        result: dict[str, Any] = {"kind": self.kind}
        if self.text_value is not None:
            result["text"] = self.text_value
        if self.action_id is not None:
            result["action_id"] = self.action_id
        if self.content_description is not None:
            result["content_description"] = self.content_description
        if self.icon_name is not None:
            result["icon"] = self.icon_name
        result.update(self.props)
        if self.children:
            result["children"] = [child.to_dict() for child in self.children]
        return result


@dataclass(frozen=True)
class UiValidation:
    node_count: int
    max_depth: int

    @property
    def is_valid(self) -> bool:
        return True


class ModUiValidator:
    """Compatibility facade for hosts that prefer a validator namespace."""

    @staticmethod
    def validate_tree(root: UiNode, limits: UiLimits | None = None) -> UiValidation:
        return validate_tree(root, limits)


def _check_string(value: Any, name: str, limits: UiLimits, *, required: bool = False) -> None:
    if value is None and not required:
        return
    if not isinstance(value, str) or (required and not value.strip()):
        raise UiValidationError(f"{name} must be a non-blank string")
    if len(value) > limits.max_text_length:
        raise UiValidationError(f"{name} exceeds maximum length {limits.max_text_length}")


def validate_tree(root: UiNode, limits: UiLimits | None = None) -> UiValidation:
    """Validate quotas and accessibility before a tree reaches the renderer."""
    if not isinstance(root, UiNode):
        raise TypeError("root must be a UiNode")
    limits = limits or UiLimits()
    count = 0
    deepest = 0
    active: set[int] = set()

    def visit(node: UiNode, depth: int) -> None:
        nonlocal count, deepest
        marker = id(node)
        if marker in active:
            raise UiValidationError("UI tree contains a cycle")
        active.add(marker)
        count += 1
        deepest = max(deepest, depth)
        if count > limits.max_nodes:
            raise UiValidationError(f"node quota exceeded: {count} > {limits.max_nodes}")
        if depth > limits.max_depth:
            raise UiValidationError(f"tree depth exceeded: {depth} > {limits.max_depth}")
        _check_string(node.text_value, "text", limits, required=node.kind in {"text", "button", "badge"})
        _check_string(node.content_description, "content description", limits)
        if node.kind == "text" and node.props.get("style") not in {"headline", "title", "body", "label", "caption"}:
            raise UiValidationError("text style is not supported")
        if node.kind in {"button", "badge"} and node.props.get("variant") not in {
            "filled", "tonal", "outlined", "text", "default", "primary", "success", "warning", "error",
        }:
            raise UiValidationError("UI variant is not supported")
        if node.kind in {"column", "row"}:
            spacing = node.props.get("spacing_dp")
            if isinstance(spacing, bool) or not isinstance(spacing, int) or not 0 <= spacing <= 512:
                raise UiValidationError("spacing_dp must be an integer from 0 to 512")
        if node.kind == "input":
            _check_string(node.props.get("initial_value"), "input initial_value", limits)
        if node.kind == "icon":
            _check_string(node.icon_name, "icon name", limits, required=True)
            if not node.content_description or not node.content_description.strip():
                raise UiValidationError("icon requires a non-blank content description")
        if node.kind == "button":
            _check_string(node.action_id, "button action_id", limits, required=True)
            if not node.content_description or not node.content_description.strip():
                raise UiValidationError("button requires a non-blank content description")
        if node.kind == "input":
            _check_string(node.text_value, "input placeholder", limits, required=True)
            _check_string(node.action_id, "input action_id", limits, required=True)
            if not node.content_description or not node.content_description.strip():
                raise UiValidationError("input requires a non-blank content description")
        for key, value in node.props.items():
            if not isinstance(key, str) or not key:
                raise UiValidationError("node property names must be non-empty strings")
            try:
                json.dumps(value, allow_nan=False, separators=(",", ":"))
            except (TypeError, ValueError, RecursionError) as error:
                raise UiValidationError("node properties must be finite JSON data") from error
            if isinstance(value, (int, float)) and (isinstance(value, bool) or not math.isfinite(value)):
                raise UiValidationError("node numeric properties must be finite")
        for child in node.children:
            visit(child, depth + 1)
        active.remove(marker)

    visit(root, 1)
    return UiValidation(count, deepest)


_PACKAGE_ID = re.compile(r"^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)+$")
_ROUTE = re.compile(r"^[a-z][a-z0-9_.:-]{0,159}$")


class UiHost:
    """Host-owned registry for an addon's slots and routes."""

    def __init__(self, *, addon_id: str, declared_slots: Iterable[str], declared_routes: Iterable[str], limits: UiLimits | None = None) -> None:
        if not isinstance(addon_id, str) or not _PACKAGE_ID.fullmatch(addon_id):
            raise ValueError("invalid addon ID")
        self.addon_id = addon_id
        self.limits = limits or UiLimits()
        self.declared_slots = frozenset(self._validate_names(declared_slots, "slot"))
        self.declared_routes = frozenset(self._validate_names(declared_routes, "route"))
        self._slots: dict[str, UiNode] = {}
        self._routes: dict[str, UiNode] = {}
        self._updates: deque[float] = deque()

    @staticmethod
    def _validate_names(names: Iterable[str], kind: str) -> tuple[str, ...]:
        values = tuple(names)
        if len(set(values)) != len(values) or any(not isinstance(name, str) or not _ROUTE.fullmatch(name) for name in values):
            raise ValueError(f"{kind} names must be unique valid symbols")
        return values

    def _allow_update(self) -> None:
        now = time.monotonic()
        while self._updates and now - self._updates[0] >= 1.0:
            self._updates.popleft()
        if len(self._updates) >= self.limits.max_updates_per_second:
            raise UiValidationError("UI update-rate quota exceeded")
        self._updates.append(now)

    def register_slot(self, slot: str, tree: UiNode) -> None:
        if slot not in self.declared_slots:
            raise UiValidationError(f"slot is not a declared slot: {slot}")
        validate_tree(tree, self.limits)
        self._allow_update()
        self._slots[slot] = tree

    def register_route(self, route: str, tree: UiNode) -> str:
        if route not in self.declared_routes:
            raise UiValidationError(f"route is not declared: {route}")
        validate_tree(tree, self.limits)
        self._allow_update()
        public_route = f"{self.addon_id}/{route}"
        if public_route in self._routes:
            raise UiValidationError(f"route already registered: {public_route}")
        self._routes[public_route] = tree
        return public_route

    register_extension = register_slot

    def slot(self, slot: str) -> UiNode:
        try:
            return self._slots[slot]
        except KeyError as error:
            raise KeyError(f"unknown registered slot: {slot}") from error

    def route(self, route: str) -> UiNode:
        if not isinstance(route, str) or "://" in route or route.startswith(("/", "intent:")):
            raise UiValidationError("routes are typed addon routes, not URI or intent values")
        try:
            return self._routes[route]
        except KeyError as error:
            raise KeyError(f"unknown registered route: {route}") from error

    navigate = route


ModUiNode = UiNode


__all__ = [
    "ModUiNode", "ModUiValidator", "UiHost", "UiLimits", "UiNode", "UiValidation",
    "UiValidationError", "validate_tree",
]
