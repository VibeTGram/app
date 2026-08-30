#!/usr/bin/env python3
"""Validate Gradle verification metadata, public keyrings, and lockfiles."""

from __future__ import annotations

import re
import sys
from typing import NoReturn
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
NAMESPACE = "{https://schema.gradle.org/dependency-verification}"
METADATA_PATHS = (
    ROOT / "gradle/verification-metadata.xml",
    ROOT / "core/gradle/verification-metadata.xml",
    ROOT / "mods/gradle/verification-metadata.xml",
    ROOT / "gui/gradle/verification-metadata.xml",
)
KEYRING_PATHS = tuple(path.parent / "verification-keyring.keys" for path in METADATA_PATHS)
LOCK_PATHS = (ROOT / "core/gradle.lockfile", ROOT / "gui/gradle.lockfile")
COORDINATE = re.compile(r"^[^:\s]+:[^:\s]+:[^:\s]+$")
SHA256 = re.compile(r"^[0-9a-f]{64}$")
KEY_ID = re.compile(r"^[0-9A-F]{16}(?:[0-9A-F]{24})?$")
DYNAMIC_MARKERS = ("+", "[", "]", "latest", "snapshot", "release", "*", ",")


def fail(message: str) -> NoReturn:
    raise SystemExit(f"dependency validation failed: {message}")


def local(root: ET.Element, tag: str) -> ET.Element | None:
    return root.find(f"{NAMESPACE}{tag}")


def validate_metadata(path: Path) -> None:
    if not path.is_file():
        fail(f"missing {path.relative_to(ROOT)}")
    try:
        tree = ET.parse(path)
    except ET.ParseError as error:
        fail(f"{path.relative_to(ROOT)} is not valid XML: {error}")
    root = tree.getroot()
    if root.tag != f"{NAMESPACE}verification-metadata":
        fail(f"{path.relative_to(ROOT)} has the wrong root element")

    configuration = local(root, "configuration")
    if configuration is None:
        fail(f"{path.relative_to(ROOT)} has no configuration")
    assert configuration is not None
    values = {child.tag.removeprefix(NAMESPACE): (child.text or "").strip() for child in configuration}
    if values.get("verify-metadata") != "true":
        fail(f"{path.relative_to(ROOT)} does not verify metadata")
    if values.get("verify-signatures") != "true":
        fail(f"{path.relative_to(ROOT)} does not verify signatures")
    if values.get("keyring-format") != "armored":
        fail(f"{path.relative_to(ROOT)} must use the armored keyring format")

    components = local(root, "components")
    if components is None:
        fail(f"{path.relative_to(ROOT)} has no components section")
    assert components is not None
    component_ids: set[tuple[str, str, str]] = set()
    artifact_ids: set[tuple[tuple[str, str, str], str]] = set()
    for component in components.findall(f"{NAMESPACE}component"):
        component_id = (
            component.attrib.get("group", ""),
            component.attrib.get("name", ""),
            component.attrib.get("version", ""),
        )
        if not all(component_id) or component_id in component_ids:
            fail(f"{path.relative_to(ROOT)} has a duplicate or incomplete component")
        component_ids.add(component_id)
        for artifact in component.findall(f"{NAMESPACE}artifact"):
            name = artifact.attrib.get("name", "")
            artifact_id = (component_id, name)
            if not name or artifact_id in artifact_ids:
                fail(f"{path.relative_to(ROOT)} has a duplicate or incomplete artifact")
            artifact_ids.add(artifact_id)
            checksums = artifact.findall(f"{NAMESPACE}sha256")
            if len(checksums) != 1 or not SHA256.fullmatch(checksums[0].attrib.get("value", "")):
                fail(f"{path.relative_to(ROOT)} has an invalid artifact SHA-256")

    for trusted_key in configuration.findall(f"{NAMESPACE}trusted-keys/{NAMESPACE}trusted-key"):
        if not KEY_ID.fullmatch(trusted_key.attrib.get("id", "")):
            fail(f"{path.relative_to(ROOT)} has an invalid trusted key ID")


def validate_keyring(path: Path) -> None:
    if not path.is_file():
        fail(f"missing {path.relative_to(ROOT)}")
    content = path.read_text(encoding="ascii")
    if "-----BEGIN PGP PUBLIC KEY BLOCK-----" not in content:
        fail(f"{path.relative_to(ROOT)} has no armored public key")
    if "PRIVATE KEY BLOCK" in content or "PGP MESSAGE" in content:
        fail(f"{path.relative_to(ROOT)} contains private or encrypted key material")
    if not content.endswith("\n"):
        fail(f"{path.relative_to(ROOT)} must end with a newline")


def validate_lockfile(path: Path) -> set[str]:
    if not path.is_file():
        fail(f"missing {path.relative_to(ROOT)}")
    entries: list[str] = []
    configurations: set[str] = set()
    for number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        coordinate, separator, names = line.partition("=")
        if not separator or not names.strip():
            fail(f"{path.relative_to(ROOT)}:{number} is not a lock entry")
        if coordinate != "empty":
            if not COORDINATE.fullmatch(coordinate):
                fail(f"{path.relative_to(ROOT)}:{number} has an invalid coordinate")
            version = coordinate.rsplit(":", 1)[1].lower()
            if any(marker in version for marker in DYNAMIC_MARKERS):
                fail(f"{path.relative_to(ROOT)}:{number} contains a dynamic version")
            entries.append(coordinate)
        configurations.update(item.strip() for item in names.split(",") if item.strip())
    if entries != sorted(entries):
        fail(f"{path.relative_to(ROOT)} entries are not deterministic (sort order)")
    if len(entries) != len(set(entries)):
        fail(f"{path.relative_to(ROOT)} contains duplicate coordinates")
    if not configurations:
        fail(f"{path.relative_to(ROOT)} has no locked configurations")
    return set(entries)


def validate_direct_dependencies() -> None:
    for project in ("core", "gui"):
        build = (ROOT / project / "build.gradle.kts").read_text(encoding="utf-8")
        coordinates = set(re.findall(r'"([^"\n]+:[^"\n]+:[^"\n]+)"', build))
        locked = validate_lockfile(ROOT / project / "gradle.lockfile")
        missing = sorted(coordinates - locked)
        if missing:
            fail(f"{project}/gradle.lockfile misses declared dependencies: {', '.join(missing)}")


def main() -> None:
    for metadata, keyring in zip(METADATA_PATHS, KEYRING_PATHS):
        validate_metadata(metadata)
        validate_keyring(keyring)
    validate_direct_dependencies()
    print("Gradle verification metadata, armored keyrings, and dependency locks: OK")


if __name__ == "__main__":
    try:
        main()
    except (OSError, UnicodeError) as error:
        fail(str(error))
