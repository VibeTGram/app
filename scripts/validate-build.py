#!/usr/bin/env python3
"""Validate immutable Gradle and Android toolchain inputs."""

from __future__ import annotations

import hashlib
import json
import re
import sys
from pathlib import Path
from urllib.parse import urlparse


ROOT = Path(__file__).resolve().parents[1]
LOCK_PATH = ROOT / "toolchain.lock.json"
WRAPPER_PROPERTIES = ROOT / "gradle/wrapper/gradle-wrapper.properties"
WRAPPER_JAR = ROOT / "gradle/wrapper/gradle-wrapper.jar"
VERSION_CATALOG = ROOT / "gradle/libs.versions.toml"
EXACT_VERSION = re.compile(r"^[0-9]+(?:\.[0-9]+){0,2}(?:\+[0-9]+)?$")
FLOATING_MARKERS = ("latest", "snapshot", "master", "main", "develop", "development", "^")


def fail(message: str) -> None:
    raise SystemExit(f"toolchain validation failed: {message}")


def load_properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        key, separator, value = line.partition("=")
        if not separator:
            fail(f"invalid wrapper property: {line}")
        values[key.strip()] = value.strip().replace("\\:", ":")
    return values


def assert_version(label: str, value: str) -> None:
    if not isinstance(value, str) or not EXACT_VERSION.fullmatch(value):
        fail(f"{label} is not an exact numeric version: {value!r}")
    if any(marker in value.lower() for marker in FLOATING_MARKERS):
        fail(f"{label} contains a floating marker: {value!r}")


def assert_url(label: str, value: str) -> None:
    parsed = urlparse(value)
    if parsed.scheme != "https" or not parsed.netloc:
        fail(f"{label} must be an HTTPS URL: {value!r}")
    if any(marker in value.lower() for marker in FLOATING_MARKERS):
        fail(f"{label} contains a floating marker: {value!r}")


def catalog_versions() -> dict[str, str]:
    versions: dict[str, str] = {}
    in_versions = False
    for line in VERSION_CATALOG.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if stripped == "[versions]":
            in_versions = True
            continue
        if stripped.startswith("["):
            in_versions = False
        if not in_versions or not stripped or stripped.startswith("#"):
            continue
        key, separator, value = stripped.partition("=")
        if separator:
            versions[key.strip()] = value.strip().strip('"')
    return versions


def main() -> None:
    lock = json.loads(LOCK_PATH.read_text(encoding="utf-8"))
    if lock.get("schema_version") != 1:
        fail("unsupported lock schema")

    jdk = lock["jdk"]
    assert_version("jdk.version", jdk["version"].split("+", 1)[0])
    assert_url("jdk.distribution", jdk["distribution"])

    gradle = lock["gradle"]
    assert_version("gradle.version", gradle["version"])
    assert_url("gradle.distribution_url", gradle["distribution_url"])
    if not re.fullmatch(r"[0-9a-f]{64}", gradle["distribution_sha256"]):
        fail("gradle distribution checksum must be lowercase SHA-256")

    for key in ("compile_sdk", "build_tools", "ndk", "cmake"):
        assert_version(f"android.{key}", lock["android"][key])
    assert_version("kotlin.version", lock["kotlin"]["version"])
    assert_version("compose.bom", lock["compose"]["bom"])

    properties = load_properties(WRAPPER_PROPERTIES)
    expected_url = gradle["distribution_url"]
    if properties.get("distributionUrl") != expected_url:
        fail("wrapper distributionUrl does not match toolchain lock")
    if properties.get("distributionSha256Sum") != gradle["distribution_sha256"]:
        fail("wrapper distributionSha256Sum does not match toolchain lock")
    if properties.get("distributionBase") != "GRADLE_USER_HOME" or properties.get("zipStoreBase") != "GRADLE_USER_HOME":
        fail("wrapper must use GRADLE_USER_HOME storage")

    if not WRAPPER_JAR.is_file():
        fail(f"missing {WRAPPER_JAR.relative_to(ROOT)}")
    wrapper_hash = hashlib.sha256(WRAPPER_JAR.read_bytes()).hexdigest()
    expected_hash = gradle["wrapper_jar_sha256"]
    if not re.fullmatch(r"[0-9a-f]{64}", expected_hash):
        fail("wrapper_jar_sha256 is not finalized")
    if wrapper_hash != expected_hash:
        fail(f"wrapper jar checksum mismatch: {wrapper_hash}")

    versions = catalog_versions()
    expected_catalog = {
        "jdk": "21.0.8",
        "gradle": gradle["version"],
        "agp": "9.2.0",
        "kotlin": lock["kotlin"]["version"],
        "android-compile-sdk": lock["android"]["compile_sdk"],
        "android-build-tools": lock["android"]["build_tools"],
        "android-ndk": lock["android"]["ndk"],
        "cmake": lock["android"]["cmake"],
        "compose-bom": lock["compose"]["bom"],
    }
    for key, expected in expected_catalog.items():
        if versions.get(key) != expected:
            fail(f"catalog version {key!r} is {versions.get(key)!r}, expected {expected!r}")

    print("Toolchain lock, catalog, wrapper properties, and wrapper checksum: OK")


if __name__ == "__main__":
    try:
        main()
    except (KeyError, json.JSONDecodeError, OSError) as error:
        fail(str(error))
