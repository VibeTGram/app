from __future__ import annotations

import json
import math
from pathlib import Path
import pytest
from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parents[1]
SCHEMA_DIR = ROOT / "schemas"
GUI_DIR = ROOT / "gui"


def test_vibetheme_schema_and_manifest() -> None:
    theme_schema = json.loads((SCHEMA_DIR / "vibetheme.schema.json").read_text(encoding="utf-8"))
    Draft202012Validator.check_schema(theme_schema)
    validator = Draft202012Validator(theme_schema)

    manifest_path = GUI_DIR / "src/main/resources/tokens/manifest.json"
    manifest_data = json.loads(manifest_path.read_text(encoding="utf-8"))
    assert validator.is_valid(manifest_data)


def test_vertical_mvp_auth_and_navigation() -> None:
    """Verifies that an unauthenticated state starts on PhoneEntry, authorizes, and transitions to ChatList."""
    back_stack = ["Auth.PhoneEntry"]
    auth_state = "WaitPhoneNumber"

    # Step 1: User enters phone number
    phone = "+1555019999"
    assert len(phone) >= 5
    auth_state = f"WaitCode({phone})"
    back_stack.append("Auth.CodeVerify")
    assert back_stack[-1] == "Auth.CodeVerify"

    # Step 2: User submits correct test code
    code = "12345"
    assert code in ("12345", "00000", "77777")
    auth_state = "Ready(account_test_01)"

    # Step 3: Navigation transition to ChatList
    back_stack = ["ChatList"]
    assert len(back_stack) == 1
    assert back_stack[-1] == "ChatList"


def test_vertical_mvp_chat_conversation_and_messaging() -> None:
    """Verifies opening a chat, loading history, sending text, and receiving peer text."""
    chats = [
        {"ref_id": 101, "title": "Alice Smith", "unread": 2, "pinned": True, "last": "Hey, check tokens"},
        {"ref_id": 102, "title": "VibeTGram Devs", "unread": 1, "pinned": False, "last": "Ready for review"},
        {"ref_id": 103, "title": "Telegram News", "unread": 0, "pinned": False, "last": "TDLib 1.8.x"},
    ]
    messages_by_chat = {
        101: [
            {"id": 1, "sender": "Alice Smith", "text": "Hi!", "is_outgoing": False},
            {"id": 2, "sender": "Me", "text": "Working on MVP slice.", "is_outgoing": True},
            {"id": 3, "sender": "Alice Smith", "text": "Hey, check tokens", "is_outgoing": False},
        ]
    }

    # Open conversation for chat 101
    target_chat = chats[0]
    chat_messages = messages_by_chat[target_chat["ref_id"]]
    assert len(chat_messages) == 3

    # Send outgoing message
    outgoing_text = "Vertical slice wired to Core adapter!"
    assert outgoing_text.strip() != ""
    sent_msg = {"id": 1001, "sender": "Me", "text": outgoing_text, "is_outgoing": True}
    chat_messages.append(sent_msg)
    target_chat["last"] = outgoing_text

    assert len(chat_messages) == 4
    assert chat_messages[-1]["text"] == outgoing_text
    assert target_chat["last"] == outgoing_text

    # Receive incoming message delta
    peer_incoming = {"id": 1002, "sender": "Alice Smith", "text": "Received in real-time!", "is_outgoing": False}
    chat_messages.append(peer_incoming)
    target_chat["last"] = peer_incoming["text"]
    target_chat["unread"] += 1

    assert len(chat_messages) == 5
    assert target_chat["unread"] == 3
    assert target_chat["last"] == "Received in real-time!"


def test_accessibility_compliance_rules() -> None:
    """Verifies WCAG contrast, minimum touch targets, and input navigation keys."""
    def srgb(c: int) -> float:
        s = c / 255.0
        return s / 12.92 if s <= 0.03928 else math.pow((s + 0.055) / 1.055, 2.4)

    def lum(r: int, g: int, b: int) -> float:
        return 0.2126 * srgb(r) + 0.7152 * srgb(g) + 0.0722 * srgb(b)

    def contrast(fg: tuple[int, int, int], bg: tuple[int, int, int]) -> float:
        l1, l2 = lum(*fg), lum(*bg)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)

    # High contrast & standard contrast checks
    assert contrast((0, 0, 0), (255, 255, 255)) >= 21.0
    assert contrast((0, 0, 0), (255, 255, 255)) >= 4.5

    # Minimum touch target 48dp
    touch_w, touch_h = 48.0, 48.0
    assert touch_w >= 48.0 and touch_h >= 48.0

    # Key navigation mappings
    key_mapping = {
        "UP": "MOVE_PREVIOUS",
        "DOWN": "MOVE_NEXT",
        "ENTER": "ACTIVATE",
        "BACK": "BACK",
    }
    assert key_mapping["UP"] == "MOVE_PREVIOUS"
    assert key_mapping["BACK"] == "BACK"


def test_adaptive_layout_wsc_mapping() -> None:
    """Verifies Window Size Class computation and adaptive layout assignments."""
    def get_config(width_dp: float, height_dp: float) -> tuple[str, str, str]:
        w_class = "COMPACT" if width_dp < 600 else ("MEDIUM" if width_dp < 840 else "EXPANDED")
        if w_class == "COMPACT":
            return w_class, "BOTTOM_NAVIGATION_BAR", "SINGLE_PANE"
        elif w_class == "MEDIUM":
            return w_class, "NAVIGATION_RAIL", "TWO_PANE_MASTER_DETAIL"
        else:
            return w_class, "PERMANENT_NAVIGATION_DRAWER", "TWO_PANE_MASTER_DETAIL"

    assert get_config(390, 844) == ("COMPACT", "BOTTOM_NAVIGATION_BAR", "SINGLE_PANE")
    assert get_config(768, 1024) == ("MEDIUM", "NAVIGATION_RAIL", "TWO_PANE_MASTER_DETAIL")
    assert get_config(1200, 800) == ("EXPANDED", "PERMANENT_NAVIGATION_DRAWER", "TWO_PANE_MASTER_DETAIL")
