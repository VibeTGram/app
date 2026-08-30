#!/usr/bin/env python3
"""Fail-closed validation for the unsigned VibeTGram internal APK.

The validator intentionally uses only the Python standard library.  A normal
Android APK has a binary AndroidManifest.xml, which is inspected with a pinned
SDK ``aapt2`` binary.  Small XML-manifest fixtures are accepted without an SDK
so archive and contract tests remain runnable on developer machines.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path, PurePosixPath
import xml.etree.ElementTree as ET
from typing import Any, NoReturn


APPLICATION_ID = "org.vibetgram.client.nightly"
CHANNEL = "nightly"
CHANNEL_METADATA_NAME = "org.vibetgram.channel"
ANDROID_NS = "http://schemas.android.com/apk/res/android"
APK_NAME_RE = re.compile(r"^[A-Za-z0-9._-]+-unsigned\.apk$")
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
SIGNATURE_SUFFIXES = (".SF", ".RSA", ".DSA", ".EC", ".SIG")


class ArtifactValidationError(ValueError):
    """Raised when an internal artifact is absent or fails a release gate."""


def _fail(message: str) -> NoReturn:
    raise ArtifactValidationError(message)


def _validate_zip_path(name: str) -> None:
    if not name or "\x00" in name or "\\" in name:
        _fail(f"unsafe ZIP path: {name!r}")
    path = PurePosixPath(name)
    if path.is_absolute() or ".." in path.parts:
        _fail(f"unsafe ZIP path: {name!r}")


def _is_signature_metadata(name: str) -> bool:
    upper = name.upper()
    if not upper.startswith("META-INF/"):
        return False
    basename = PurePosixPath(upper).name
    return basename == "MANIFEST.MF" or basename.endswith(SIGNATURE_SUFFIXES)


def _read_archive(apk: Path) -> tuple[dict[str, bytes], str, int]:
    if not apk.is_file():
        _fail(f"APK does not exist or is not a regular file: {apk}")
    if not APK_NAME_RE.fullmatch(apk.name):
        _fail(f"internal APK must use an -unsigned.apk filename: {apk.name!r}")
    try:
        size = apk.stat().st_size
    except OSError as error:
        _fail(f"cannot stat APK: {error}")
    if size < 1:
        _fail("internal APK must not be empty")

    entries: dict[str, bytes] = {}
    folded: dict[str, str] = {}
    try:
        with zipfile.ZipFile(apk, "r") as archive:
            infos = archive.infolist()
            for info in infos:
                _validate_zip_path(info.filename)
                folded_name = info.filename.casefold()
                if folded_name in folded:
                    _fail(
                        "duplicate ZIP path after case folding: "
                        f"{folded[folded_name]!r} and {info.filename!r}"
                    )
                folded[folded_name] = info.filename
                if _is_signature_metadata(info.filename):
                    _fail(f"internal APK contains signing metadata: {info.filename}")
                # ZIP symlinks can escape the archive's intended file model.
                if (info.external_attr >> 16) & 0o170000 == 0o120000:
                    _fail(f"internal APK contains a symlink: {info.filename}")
                if info.is_dir():
                    continue
                entries[info.filename] = archive.read(info)
            if archive.testzip() is not None:
                _fail("internal APK has a CRC error")
    except zipfile.BadZipFile as error:
        _fail(f"internal APK is not a valid ZIP archive: {error}")
    except OSError as error:
        _fail(f"cannot read internal APK: {error}")

    if "AndroidManifest.xml" not in entries:
        _fail("internal APK has no AndroidManifest.xml")
    if not any(re.fullmatch(r"classes(?:[2-9]|[1-9][0-9]+)?\.dex", name) for name in entries):
        _fail("internal APK has no classes*.dex entry")
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    return entries, digest, size


def _xml_manifest_details(payload: bytes) -> tuple[str, str] | None:
    if not payload.lstrip().startswith(b"<"):
        return None
    try:
        root = ET.fromstring(payload)
    except ET.ParseError as error:
        _fail(f"XML AndroidManifest.xml is malformed: {error}")
    package = root.attrib.get("package")
    if not package:
        _fail("AndroidManifest.xml has no package/application ID")
    applications = [child for child in root if child.tag.rsplit("}", 1)[-1] == "application"]
    if len(applications) != 1:
        _fail("AndroidManifest.xml must contain exactly one application")
    metadata = []
    for node in applications[0]:
        if node.tag.rsplit("}", 1)[-1] != "meta-data":
            continue
        name = node.attrib.get(f"{{{ANDROID_NS}}}name")
        if name == CHANNEL_METADATA_NAME:
            metadata.append(node.attrib.get(f"{{{ANDROID_NS}}}value"))
    if len(metadata) != 1 or metadata[0] is None:
        _fail(f"AndroidManifest.xml must declare {CHANNEL_METADATA_NAME}")
    return package, metadata[0]


def _find_aapt2(sdk_root: Path | None) -> str:
    if sdk_root is not None:
        version = os.environ.get("ANDROID_BUILD_TOOLS_VERSION", "36.0.0")
        candidate = sdk_root / "build-tools" / version / "aapt2"
        if candidate.is_file() and os.access(candidate, os.X_OK):
            return str(candidate)
        _fail(f"Android SDK/build-tools unavailable: missing executable {candidate}")
    path = shutil.which("aapt2")
    if path:
        return path
    _fail(
        "Android SDK/build-tools unavailable: binary AndroidManifest.xml "
        "requires aapt2 (set ANDROID_SDK_ROOT or put aapt2 on PATH)"
    )


def _aapt2_manifest_details(apk: Path, sdk_root: Path | None) -> tuple[str, str]:
    aapt2 = _find_aapt2(sdk_root)
    try:
        badging = subprocess.run(
            [aapt2, "dump", "badging", str(apk)],
            check=True,
            capture_output=True,
            text=True,
        ).stdout
        xmltree = subprocess.run(
            [aapt2, "dump", "xmltree", str(apk), "--file", "AndroidManifest.xml"],
            check=True,
            capture_output=True,
            text=True,
        ).stdout
    except (OSError, subprocess.CalledProcessError) as error:
        _fail(f"aapt2 could not inspect AndroidManifest.xml: {error}")
    package_match = re.search(r"^package: name='([^']+)'", badging, re.MULTILINE)
    if not package_match:
        _fail("aapt2 output has no package/application ID")
    metadata_blocks = re.findall(r"E: meta-data\b(.*?)(?=\n\s*E:|\Z)", xmltree, re.DOTALL)
    channel_values = []
    for block in metadata_blocks:
        name_match = re.search(r"android:name[^=]*=\"([^\"]+)\"", block)
        value_match = re.search(r"android:value[^=]*=\"([^\"]+)\"", block)
        if name_match and name_match.group(1) == CHANNEL_METADATA_NAME and value_match:
            channel_values.append(value_match.group(1))
    if len(channel_values) != 1:
        _fail(f"aapt2 output must contain exactly one {CHANNEL_METADATA_NAME} metadata entry")
    return package_match.group(1), channel_values[0]


def _check_bom(bom_path: Path, apk: Path, digest: str, size: int) -> None:
    if not bom_path.is_file():
        _fail(f"build BOM does not exist: {bom_path}")
    try:
        value: Any = json.loads(bom_path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as error:
        _fail(f"build BOM is not valid UTF-8 JSON: {error}")
    if not isinstance(value, dict):
        _fail("build BOM root must be an object")
    if value.get("channel") != CHANNEL:
        _fail(f"build BOM channel must be {CHANNEL!r}")
    if value.get("application_id") != APPLICATION_ID:
        _fail(f"build BOM application ID must be {APPLICATION_ID!r}")
    artifact = value.get("unsigned_artifact")
    if not isinstance(artifact, dict):
        _fail("build BOM has no unsigned_artifact object")
    if artifact.get("filename") != apk.name:
        _fail("BOM artifact filename does not match internal APK")
    if artifact.get("sha256") != digest:
        _fail("BOM artifact sha256 does not match internal APK")
    if artifact.get("size_bytes") != size:
        _fail("BOM artifact size_bytes does not match internal APK")
    if not SHA256_RE.fullmatch(str(artifact.get("sha256", ""))):
        _fail("BOM artifact sha256 is not a lowercase SHA-256")
    if isinstance(artifact.get("size_bytes"), bool) or not isinstance(artifact.get("size_bytes"), int):
        _fail("BOM artifact size_bytes must be an integer")


def validate_internal_apk(
    apk_path: Path | str,
    *,
    bom_path: Path | str,
    sdk_root: Path | str | None = None,
) -> dict[str, Any]:
    """Validate one internal APK and its build-BOM artifact linkage."""
    apk = Path(apk_path)
    entries, digest, size = _read_archive(apk)
    bom = Path(bom_path)
    _check_bom(bom, apk, digest, size)
    sdk = Path(sdk_root) if sdk_root is not None else None
    if sdk is None:
        ambient_sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
        if ambient_sdk:
            sdk = Path(ambient_sdk)
    details = _xml_manifest_details(entries["AndroidManifest.xml"])
    if details is None:
        details = _aapt2_manifest_details(apk, sdk)
    application_id, channel = details
    if application_id != APPLICATION_ID:
        _fail(f"manifest application ID {application_id!r} does not match {APPLICATION_ID!r}")
    if channel != CHANNEL:
        _fail(f"manifest channel {channel!r} does not match {CHANNEL!r}")
    return {
        "path": str(apk),
        "filename": apk.name,
        "application_id": application_id,
        "channel": channel,
        "sha256": digest,
        "size_bytes": size,
        "entry_count": len(entries),
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path, help="unsigned internal APK")
    parser.add_argument("--bom", required=True, type=Path, help="matching build-bom.json")
    parser.add_argument("--sdk-root", type=Path, help="Android SDK root for pinned build-tools/aapt2")
    args = parser.parse_args(argv)
    try:
        result = validate_internal_apk(args.apk, bom_path=args.bom, sdk_root=args.sdk_root)
    except ArtifactValidationError as error:
        print(f"internal artifact validation failed: {error}", file=sys.stderr)
        return 2
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
