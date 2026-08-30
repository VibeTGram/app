from __future__ import annotations

import pytest

from mods.ui import (
    UiHost,
    UiLimits,
    UiNode,
    UiValidationError,
    validate_tree,
)


def test_validates_accessible_declarative_tree_and_declared_slot() -> None:
    host = UiHost(addon_id="org.example.mod", declared_slots={"chat_list.badge"}, declared_routes={"settings"})
    tree = UiNode.column(
        UiNode.text("Hello"),
        UiNode.button("Open", "open", content_description="Open settings"),
    )

    host.register_slot("chat_list.badge", tree)
    host.register_route("settings", tree)

    assert validate_tree(tree).node_count == 3
    assert host.slot("chat_list.badge").children[1].action_id == "open"
    assert host.route("org.example.mod/settings") is tree


def test_rejects_inaccessible_icon_and_unknown_slot() -> None:
    bad = UiNode.icon("star", content_description=None)
    with pytest.raises(UiValidationError, match="content description"):
        validate_tree(bad)

    host = UiHost(addon_id="org.example.mod", declared_slots=set(), declared_routes=set())
    with pytest.raises(UiValidationError, match="declared slot"):
        host.register_slot("chat_list.badge", UiNode.text("x"))


def test_limits_and_no_executable_callbacks() -> None:
    tree = UiNode.column(*(UiNode.text(str(i)) for i in range(4)))
    with pytest.raises(UiValidationError, match="node quota"):
        validate_tree(tree, UiLimits(max_nodes=3))

    with pytest.raises(TypeError):
        UiNode.button("Run", lambda: None)  # type: ignore[arg-type]
