"""Modification/Developer Mode gates and source-only hot reload boundary."""
from __future__ import annotations

import hashlib
import re
from collections.abc import Callable
from dataclasses import dataclass
from threading import RLock
from typing import Any


class DeveloperModeError(RuntimeError):
    """A development-only operation is not permitted or failed safely."""


class ModificationModeError(RuntimeError):
    """The off -> on Modification Mode gate was not satisfied."""


@dataclass(frozen=True)
class ModeState:
    modification_mode: bool
    developer_mode: bool


RISK_TABLE = (
    "Addons run Telegram-connected code inside this app.",
    "Addons may read message content and send data to declared external servers.",
    "Features that modify read receipts, presence, typing, or ephemeral "
    "content may conflict with the Telegram API Terms of Service and can "
    "restrict the project's api_id or your account.",
)


class DeveloperModeManager:
    """Owns the global gates; turning Modification Mode off is synchronous."""

    def __init__(self, *, warning_seconds: float = 15.0) -> None:
        if isinstance(warning_seconds, bool) or not isinstance(warning_seconds, (int, float)) or warning_seconds <= 0:
            raise ValueError("warning_seconds must be positive")
        self.warning_seconds = float(warning_seconds)
        self._modification = False
        self._developer = False
        self._listeners: list[Callable[[], None]] = []
        self._developer_listeners: list[Callable[[], None]] = []
        self._lock = RLock()

    @property
    def modification_mode_enabled(self) -> bool:
        with self._lock:
            return self._modification

    @property
    def developer_mode_enabled(self) -> bool:
        with self._lock:
            return self._developer

    @property
    def state(self) -> ModeState:
        with self._lock:
            return ModeState(self._modification, self._developer)

    def add_disable_listener(self, listener: Callable[[], None]) -> None:
        if not callable(listener):
            raise TypeError("disable listener must be callable")
        with self._lock:
            self._listeners.append(listener)

    def add_developer_disable_listener(self, listener: Callable[[], None]) -> None:
        if not callable(listener):
            raise TypeError("developer disable listener must be callable")
        with self._lock:
            self._developer_listeners.append(listener)

    def set_modification_mode(
        self,
        enabled: bool,
        *,
        warning_elapsed_seconds: float | None = None,
        risk_table_shown: bool = False,
    ) -> None:
        """Enable or disable Modification Mode.

        Every explicit off -> on transition requires the complete risk table
        and a full warning timer (docs/architecture/system-architecture.md
        §10). Turning the mode off stays immediate: no timer, no confirmation.
        """
        if not isinstance(enabled, bool):
            raise TypeError("Modification Mode must be a boolean")
        if enabled:
            if risk_table_shown is not True:
                raise ModificationModeError("Modification Mode requires the complete risk table")
            elapsed = warning_elapsed_seconds
            if (isinstance(elapsed, bool) or not isinstance(elapsed, (int, float))
                    or elapsed < self.warning_seconds):
                raise ModificationModeError(
                    f"Modification Mode requires a {self.warning_seconds:g}-second warning"
                )
        listeners: tuple[Callable[[], None], ...] = ()
        with self._lock:
            self._modification = enabled
            if not enabled:
                self._developer = False
                listeners = tuple(self._listeners)
        # Callbacks run outside the lock so a host can close its own instances.
        for listener in listeners:
            listener()

    def enable_developer_mode(self, taps: int, *, warning_elapsed_seconds: float, confirmed: bool) -> None:
        with self._lock:
            if not self._modification:
                raise DeveloperModeError("Developer Mode requires Modification Mode")
            if isinstance(taps, bool) or not isinstance(taps, int) or taps < 7:
                raise DeveloperModeError("Developer Mode requires seven taps")
            if (isinstance(warning_elapsed_seconds, bool) or not isinstance(warning_elapsed_seconds, (int, float))
                    or warning_elapsed_seconds < self.warning_seconds):
                raise DeveloperModeError(f"Developer Mode requires a {self.warning_seconds:g}-second warning")
            if confirmed is not True:
                raise DeveloperModeError("Developer Mode requires explicit confirmation")
            self._developer = True

    def disable_developer_mode(self) -> None:
        listeners: tuple[Callable[[], None], ...] = ()
        with self._lock:
            self._developer = False
            listeners = tuple(self._developer_listeners)
        for listener in listeners:
            listener()


_SOURCE_BYTECODE = (b"\x1bLua", b"\x1bLuau")


def validate_source(source: str) -> bytes:
    if not isinstance(source, str):
        raise DeveloperModeError("source text is required; bytecode is not accepted")
    try:
        encoded = source.encode("utf-8", errors="strict")
    except UnicodeEncodeError as error:
        raise DeveloperModeError("source must be valid UTF-8") from error
    if encoded.startswith(_SOURCE_BYTECODE):
        raise DeveloperModeError("compiled bytecode is not accepted")
    if b"\x00" in encoded:
        raise DeveloperModeError("source cannot contain NUL")
    return encoded


@dataclass(frozen=True)
class ReloadRecord:
    source_sha256: str
    source_size: int
    generation: int


class HotReloadManager:
    """Atomically swaps source after a successful host load; no bytecode cache."""

    def __init__(self, mode: DeveloperModeManager, *, loader: Callable[[str], Any] | None = None) -> None:
        self._mode = mode
        self._loader = loader or (lambda source: source)
        self._source: str | None = None
        self._loaded: Any = None
        self._generation = 0
        self._lock = RLock()
        mode.add_disable_listener(self.stop)
        mode.add_developer_disable_listener(self.stop)

    @property
    def source(self) -> str | None:
        with self._lock:
            return self._source

    @property
    def loaded(self) -> Any:
        with self._lock:
            return self._loaded

    @property
    def record(self) -> ReloadRecord | None:
        with self._lock:
            if self._source is None:
                return None
            data = validate_source(self._source)
            return ReloadRecord(hashlib.sha256(data).hexdigest(), len(data), self._generation)

    def load(self, source: str) -> Any:
        return self._replace(source, self._loader, initial=True)

    def reload(self, source: str, *, loader: Callable[[str], Any] | None = None) -> Any:
        return self._replace(source, loader or self._loader, initial=False)

    def stop(self) -> None:
        with self._lock:
            self._source = None
            self._loaded = None

    def _replace(self, source: str, loader: Callable[[str], Any], *, initial: bool) -> Any:
        if not self._mode.developer_mode_enabled:
            raise DeveloperModeError("hot reload requires Developer Mode")
        encoded = validate_source(source)
        if not callable(loader):
            raise TypeError("loader must be callable")
        with self._lock:
            previous = (self._source, self._loaded, self._generation)
            try:
                loaded = loader(source)
            except Exception as error:
                # Keep the last working source and object observable, including
                # when a failed first load has no previous state.
                self._source, self._loaded, self._generation = previous
                raise DeveloperModeError("reload failed; previous version retained") from error
            self._source = source
            self._loaded = loaded
            self._generation += 1
            return loaded


ALLOWED_BRIDGES = frozenset({
    "ui", "ipc", "storage", "settings", "diagnostics", "events.best_effort",
})
FORBIDDEN_BRIDGES = frozenset({
    "android.context", "java.reflect", "jni.handle", "native.load", "dex.load",
    "jar.load", "filesystem.raw", "process.exec", "network.socket", "tdlib.client",
    "mtproto.raw", "telegram.auth_key", "firebase.token", "webview.javascript_interface",
})


def validate_bridge_names(names: set[str] | frozenset[str]) -> frozenset[str]:
    """Validate a host bridge export list against the no-escape-hatch contract."""
    if not isinstance(names, (set, frozenset)):
        raise TypeError("bridge names must be a set")
    if any(not isinstance(name, str) or not re.fullmatch(r"[a-z][a-z0-9_.]*", name) for name in names):
        raise DeveloperModeError("bridge names must be stable symbols")
    forbidden = names & FORBIDDEN_BRIDGES
    if forbidden:
        raise DeveloperModeError(f"forbidden bridge exports: {', '.join(sorted(forbidden))}")
    if not names <= ALLOWED_BRIDGES:
        raise DeveloperModeError("bridge export is outside the public Mod SDK")
    return frozenset(names)


__all__ = [
    "ALLOWED_BRIDGES", "DeveloperModeError", "DeveloperModeManager", "FORBIDDEN_BRIDGES",
    "HotReloadManager", "ModeState", "ModificationModeError", "RISK_TABLE", "ReloadRecord",
    "validate_bridge_names", "validate_source",
]
