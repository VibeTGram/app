"""Host-owned package identities and isolated unsigned-install records."""
from __future__ import annotations

import re
import secrets
from dataclasses import dataclass
from hashlib import sha256
from types import MappingProxyType
from typing import Mapping


class IdentityError(ValueError):
    """An identity was malformed or attempted to cross a trust boundary."""


_PACKAGE_ID = re.compile(r"^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)+$")
_KEY_ID = re.compile(r"^sha256:[0-9a-f]{64}$")
_VERSION = re.compile(r"^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-[0-9A-Za-z.-]+)?$")
_DEV_ID = re.compile(r"^dev_[0-9a-f]{32}$")
_HOST_IDENTITY_TOKEN = object()


def _check_package(package_id: str) -> None:
    if not isinstance(package_id, str) or not _PACKAGE_ID.fullmatch(package_id):
        raise IdentityError("invalid package ID")


def _check_version(version: str) -> None:
    if not isinstance(version, str) or not _VERSION.fullmatch(version):
        raise IdentityError("invalid package version")
    prerelease = version.partition("-")[2]
    if any(part.isdigit() and len(part) > 1 and part.startswith("0") for part in prerelease.split(".")):
        raise IdentityError("invalid package version")


@dataclass(frozen=True)
class HostIdentity:
    """Immutable principal identity assembled by the host, never by Luau."""

    package_id: str
    package_version: str
    signed: bool
    publisher_key_id: str | None = None
    development_install_id: str | None = None
    _host_token: object | None = None

    def __post_init__(self) -> None:
        if self._host_token is not _HOST_IDENTITY_TOKEN:
            raise IdentityError("identities can only be issued by the host")
        _check_package(self.package_id)
        _check_version(self.package_version)
        if self.signed:
            if self.publisher_key_id is None or not _KEY_ID.fullmatch(self.publisher_key_id):
                raise IdentityError("signed identity requires publisher key ID")
            if self.development_install_id is not None:
                raise IdentityError("signed identity cannot carry development ID")
        else:
            if self.publisher_key_id is not None:
                raise IdentityError("unsigned identity cannot carry publisher key ID")
            if self.development_install_id is None or not _DEV_ID.fullmatch(self.development_install_id):
                raise IdentityError("unsigned identity requires host-generated development ID")

    @property
    def binding(self) -> tuple[str, str]:
        """Stable install routing key; display metadata is intentionally absent."""
        if self.signed:
            assert self.publisher_key_id is not None
            return self.package_id, self.publisher_key_id
        assert self.development_install_id is not None
        return self.package_id, self.development_install_id

    @property
    def install_identity(self) -> str:
        return self.publisher_key_id if self.signed else self.development_install_id  # type: ignore[return-value]

    @property
    def namespaces(self) -> Mapping[str, str]:
        identity = self.install_identity
        return MappingProxyType({
            "version": f"{self.package_id}:{identity}",
            "priority": f"{self.package_id}:{identity}",
            "storage": f"{self.package_id}:{identity}",
            "grants": f"{self.package_id}:{identity}",
            "journal": f"{self.package_id}:{identity}",
            "cookies": f"{self.package_id}:{identity}",
            "ipc": f"{self.package_id}:{identity}",
        })

    def namespaces_for_account(self, account_handle: str) -> Mapping[str, str]:
        if not isinstance(account_handle, str) or not account_handle:
            raise IdentityError("account handle must be a non-empty host-bound value")
        account_digest = sha256(account_handle.encode("utf-8", errors="strict")).hexdigest()
        prefix = f"{self.package_id}:{self.install_identity}:account:{account_digest}"
        return MappingProxyType({name: f"{prefix}:{name}" for name in (
            "storage", "grants", "journal", "cookies", "ipc",
        )})


@dataclass(frozen=True)
class DevelopmentInstall:
    """A retained local project record used only for explicit reconnect."""

    record_id: str
    identity: HostIdentity
    project_key: str


class HostIdentityFactory:
    """Factory with no API for caller-supplied unsigned identity values."""

    @staticmethod
    def signed(package_id: str, publisher_key_id: str, package_version: str) -> HostIdentity:
        return HostIdentity(package_id, package_version, True, publisher_key_id=publisher_key_id, _host_token=_HOST_IDENTITY_TOKEN)

    @staticmethod
    def unsigned(package_id: str, package_version: str = "0.0.0", *, development_install_id: str | None = None) -> HostIdentity:
        if development_install_id is not None:
            raise IdentityError("unsigned identity is generated only by the host")
        return HostIdentity(
            package_id,
            package_version,
            False,
            development_install_id="dev_" + secrets.token_hex(16),
            _host_token=_HOST_IDENTITY_TOKEN,
        )

    generate_development_identity = unsigned


class IdentityStore:
    """In-memory model of host persistence; reconnect is explicit and auditable."""

    def __init__(self) -> None:
        self._records: dict[str, DevelopmentInstall] = {}

    def import_project(self, project_key: str, package_id: str, package_version: str = "0.0.0") -> DevelopmentInstall:
        if not isinstance(project_key, str) or not project_key:
            raise IdentityError("project key must be non-empty host-local data")
        identity = HostIdentityFactory.unsigned(package_id, package_version)
        record = DevelopmentInstall("record_" + secrets.token_hex(16), identity, project_key)
        self._records[record.record_id] = record
        return record

    def reconnect(self, record_id: str) -> DevelopmentInstall:
        try:
            return self._records[record_id]
        except KeyError as error:
            raise IdentityError("unknown development install record") from error

    def get(self, record_id: str) -> DevelopmentInstall | None:
        return self._records.get(record_id)

    @property
    def records(self) -> Mapping[str, DevelopmentInstall]:
        return MappingProxyType(dict(self._records))


__all__ = ["DevelopmentInstall", "HostIdentity", "HostIdentityFactory", "IdentityError", "IdentityStore"]
