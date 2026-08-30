#!/usr/bin/env python3
"""Generate a deterministic VibeTGram build BOM.

The generator deliberately takes a small JSON input document instead of reading
ambient git or toolchain state.  Repository commits and file digests are still
measured from the paths named by that document, so a caller cannot accidentally
publish a digest for a different checkout or artifact.
"""
from __future__ import annotations

import argparse
import datetime as datetime_module
import hashlib
import json
import os
import re
import subprocess
import sys
from pathlib import Path
from typing import Any, Mapping

try:
    from jsonschema import Draft202012Validator, FormatChecker
except ImportError as error:  # pragma: no cover - exercised by the CLI environment
    raise SystemExit("build BOM generation requires the 'jsonschema' package") from error


COMMIT_RE = re.compile(r"^(?:[0-9a-f]{40}|[0-9a-f]{64})$")
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
APK_NAME_RE = re.compile(r"^[A-Za-z0-9._-]+\.apk$")
FLOATING_VERSION_MARKERS = ("latest", "snapshot", "master", "main", "develop", "development")

REPOSITORY_URLS = {
    "app": "https://github.com/VibeTGram/app",
    "gui": "https://github.com/VibeTGram/gui",
    "core": "https://github.com/VibeTGram/core",
    "mods": "https://github.com/VibeTGram/mods",
    "mods-example": "https://github.com/VibeTGram/mods-example",
    "addons-market": "https://github.com/VibeTGram/addons-market",
}
UPSTREAM_URLS = {
    "telegram_android": "https://github.com/DrKLO/Telegram",
    "tdlib": "https://github.com/tdlib/td",
    "tgcalls": "https://github.com/TelegramMessenger/tgcalls",
    "luau": "https://github.com/luau-lang/luau",
}


class BomError(ValueError):
    """Raised when build inputs cannot produce a trustworthy BOM."""


def _reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise BomError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def load_config(path: Path) -> dict[str, Any]:
    """Load a UTF-8 configuration document, rejecting duplicate keys."""
    try:
        raw = path.read_bytes()
        if raw.startswith(b"\xef\xbb\xbf"):
            raise BomError("configuration must not contain a UTF-8 BOM")
        value = json.loads(raw.decode("utf-8"), object_pairs_hook=_reject_duplicate_keys)
    except BomError:
        raise
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise BomError(f"invalid configuration JSON: {error}") from error
    if not isinstance(value, dict):
        raise BomError("configuration root must be an object")
    return value


def _required(mapping: Mapping[str, Any], key: str, context: str) -> Any:
    if key not in mapping:
        raise BomError(f"{context} is missing {key!r}")
    return mapping[key]


def _path(value: Any, *, context: str, base_dir: Path) -> Path:
    if not isinstance(value, str) or not value:
        raise BomError(f"{context} must be a non-empty path")
    path = Path(value).expanduser()
    return path if path.is_absolute() else base_dir / path


def _sha256(path: Path, context: str) -> str:
    if not path.is_file():
        raise BomError(f"{context} does not exist or is not a regular file: {path}")
    digest = hashlib.sha256()
    try:
        with path.open("rb") as source:
            for chunk in iter(lambda: source.read(1024 * 1024), b""):
                digest.update(chunk)
    except OSError as error:
        raise BomError(f"cannot read {context}: {error}") from error
    return digest.hexdigest()


def _git(path: Path, *args: str) -> str:
    try:
        return subprocess.check_output(
            ["git", "-C", str(path), *args],
            text=True,
            stderr=subprocess.PIPE,
        ).strip()
    except (OSError, subprocess.CalledProcessError) as error:
        detail = getattr(error, "stderr", "") or str(error)
        raise BomError(f"git query failed for {path}: {detail.strip()}") from error


def _repository_pins(config: Mapping[str, Any], base_dir: Path) -> list[dict[str, str]]:
    entries = _required(config, "repositories", "configuration")
    if not isinstance(entries, list) or len(entries) != len(REPOSITORY_URLS):
        raise BomError(f"repositories must contain exactly {len(REPOSITORY_URLS)} entries")

    found: dict[str, dict[str, str]] = {}
    for index, item in enumerate(entries):
        context = f"repositories[{index}]"
        if not isinstance(item, dict):
            raise BomError(f"{context} must be an object")
        name = _required(item, "name", context)
        repository = _required(item, "repository", context)
        if not isinstance(name, str) or name not in REPOSITORY_URLS:
            raise BomError(f"{context}.name is not a supported repository: {name!r}")
        if name in found:
            raise BomError(f"duplicate repository name: {name}")
        if repository != REPOSITORY_URLS[name]:
            raise BomError(f"{context}.repository is not the canonical URL for {name}")
        repo_path = _path(_required(item, "path", context), context=f"{context}.path", base_dir=base_dir)
        if not repo_path.is_dir():
            raise BomError(f"{context}.path is not a directory: {repo_path}")
        commit = _git(repo_path, "rev-parse", "--verify", "HEAD^{commit}")
        if not COMMIT_RE.fullmatch(commit):
            raise BomError(f"{context} resolved a non-full commit: {commit!r}")
        expected = item.get("commit")
        if expected is not None:
            if not isinstance(expected, str) or not COMMIT_RE.fullmatch(expected):
                raise BomError(f"{context}.commit is not a full lowercase commit")
            if expected != commit:
                raise BomError(f"{context} commit mismatch: expected {expected}, found {commit}")
        status = _git(repo_path, "status", "--porcelain", "--untracked-files=all")
        if status:
            raise BomError(f"repository {name} is not clean")
        found[name] = {"name": name, "repository": repository, "commit": commit}

    missing = set(REPOSITORY_URLS) - set(found)
    if missing:
        raise BomError(f"missing repositories: {', '.join(sorted(missing))}")
    return [found[name] for name in sorted(found)]


def _upstream_pins(config: Mapping[str, Any]) -> dict[str, dict[str, str]]:
    values = _required(config, "upstreams", "configuration")
    if not isinstance(values, dict) or set(values) != set(UPSTREAM_URLS):
        raise BomError("upstreams must contain exactly telegram_android, tdlib, tgcalls and luau")
    result: dict[str, dict[str, str]] = {}
    for name in sorted(UPSTREAM_URLS):
        context = f"upstreams.{name}"
        item = values[name]
        if not isinstance(item, dict):
            raise BomError(f"{context} must be an object")
        repository = _required(item, "repository", context)
        commit = _required(item, "commit", context)
        if repository != UPSTREAM_URLS[name]:
            raise BomError(f"{context}.repository is not the canonical URL")
        if not isinstance(commit, str) or not COMMIT_RE.fullmatch(commit):
            raise BomError(f"{context}.commit must be a full lowercase commit")
        result[name] = {"repository": repository, "commit": commit}
    return result


def _toolchain(config: Mapping[str, Any]) -> dict[str, str]:
    value = _required(config, "toolchain", "configuration")
    required = ("jdk", "gradle", "agp", "kotlin", "android_sdk", "ndk", "cmake")
    if not isinstance(value, dict) or set(value) != set(required):
        raise BomError("toolchain must contain exactly jdk, gradle, agp, kotlin, android_sdk, ndk and cmake")
    result: dict[str, str] = {}
    for name in sorted(required):
        version = value[name]
        if not isinstance(version, str) or not version or len(version) > 160 or version != version.strip():
            raise BomError(f"toolchain.{name} must be a non-empty exact version string")
        if any(marker in version.lower() for marker in FLOATING_VERSION_MARKERS):
            raise BomError(f"toolchain.{name} contains a floating version marker")
        result[name] = version
    return result


def _digest_input(config: Mapping[str, Any], path_key: str, digest_key: str, base_dir: Path, label: str) -> str:
    configured_path = config.get(path_key)
    if configured_path is not None:
        digest = _sha256(_path(configured_path, context=path_key, base_dir=base_dir), label)
        configured_digest = config.get(digest_key)
        if configured_digest is not None and configured_digest != digest:
            raise BomError(f"{label} digest mismatch: expected {configured_digest}, found {digest}")
        return digest
    configured_digest = config.get(digest_key)
    if isinstance(configured_digest, str) and SHA256_RE.fullmatch(configured_digest):
        return configured_digest
    raise BomError(f"configuration requires {path_key!r} or a lowercase SHA-256 {digest_key!r}")


def _artifact(config: Mapping[str, Any], base_dir: Path) -> dict[str, Any]:
    configured = config.get("unsigned_artifact")
    configured_path = config.get("unsigned_artifact_path")
    configured_filename: Any = None
    expected_digest: Any = None
    expected_size: Any = None
    if isinstance(configured, str):
        configured_path = configured
    elif isinstance(configured, dict):
        configured_path = configured.get("path", configured_path)
        configured_filename = configured.get("filename")
        expected_digest = configured.get("sha256")
        expected_size = configured.get("size_bytes")
    elif configured is not None:
        raise BomError("unsigned_artifact must be a path or object")
    if configured_path is None:
        raise BomError("configuration requires unsigned_artifact_path")
    path = _path(configured_path, context="unsigned_artifact_path", base_dir=base_dir)
    filename = configured_filename if configured_filename is not None else path.name
    if not isinstance(filename, str) or not APK_NAME_RE.fullmatch(filename):
        raise BomError(f"unsigned artifact filename is not a safe APK filename: {filename!r}")
    if filename != path.name:
        raise BomError("unsigned artifact filename must match the artifact path basename")
    if not path.is_file():
        raise BomError(f"unsigned artifact does not exist or is not a regular file: {path}")
    try:
        size_before = path.stat().st_size
    except OSError as error:
        raise BomError(f"cannot stat unsigned artifact: {error}") from error
    digest = _sha256(path, "unsigned artifact")
    try:
        size = path.stat().st_size
    except OSError as error:
        raise BomError(f"cannot stat unsigned artifact: {error}") from error
    if size != size_before:
        raise BomError("unsigned artifact changed while it was being hashed")
    if size < 1:
        raise BomError("unsigned artifact must not be empty")
    if expected_digest is not None and expected_digest != digest:
        raise BomError(f"unsigned artifact digest mismatch: expected {expected_digest}, found {digest}")
    if expected_size is not None and expected_size != size:
        raise BomError(f"unsigned artifact size mismatch: expected {expected_size}, found {size}")
    return {"filename": filename, "sha256": digest, "size_bytes": size}


def _source_date_epoch(config: Mapping[str, Any], explicit: int | None) -> int:
    raw: Any = explicit
    if raw is None:
        raw = os.environ.get("SOURCE_DATE_EPOCH", config.get("source_date_epoch"))
    if isinstance(raw, bool) or raw is None:
        raise BomError("SOURCE_DATE_EPOCH is required for deterministic output")
    try:
        epoch = int(str(raw), 10)
    except (TypeError, ValueError) as error:
        raise BomError("SOURCE_DATE_EPOCH must be a non-negative integer") from error
    if epoch < 0:
        raise BomError("SOURCE_DATE_EPOCH must be a non-negative integer")
    return epoch


def _generated_at(epoch: int) -> str:
    try:
        value = datetime_module.datetime.fromtimestamp(epoch, datetime_module.timezone.utc)
    except (OverflowError, OSError, ValueError) as error:
        raise BomError(f"SOURCE_DATE_EPOCH is outside the supported timestamp range: {epoch}") from error
    return value.isoformat(timespec="seconds").replace("+00:00", "Z")


def validate_bom(bom: Mapping[str, Any], schema_path: Path | None = None) -> bool:
    """Validate a generated BOM with the repository's Draft 2020-12 schema."""
    schema_path = schema_path or Path(__file__).resolve().parents[1] / "schemas" / "build-bom.schema.json"
    try:
        schema = json.loads(schema_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise BomError(f"cannot load BOM schema: {error}") from error
    errors = sorted(
        Draft202012Validator(schema, format_checker=FormatChecker()).iter_errors(bom),
        key=lambda error: list(error.path),
    )
    if errors:
        details = "; ".join(f"{'.'.join(map(str, error.path)) or '<root>'}: {error.message}" for error in errors)
        raise BomError(f"generated BOM does not validate: {details}")
    return True


def generate_bom(config: Mapping[str, Any], *, base_dir: Path | None = None, source_date_epoch: int | None = None) -> dict[str, Any]:
    """Build and validate one BOM from immutable repository/file inputs."""
    if not isinstance(config, Mapping):
        raise BomError("configuration root must be an object")
    base_dir = Path.cwd() if base_dir is None else base_dir
    channel = _required(config, "channel", "configuration")
    application_id = _required(config, "application_id", "configuration")
    version_name = _required(config, "version_name", "configuration")
    version_code = _required(config, "version_code", "configuration")
    if not isinstance(channel, str) or not isinstance(application_id, str) or not isinstance(version_name, str):
        raise BomError("channel, application_id and version_name must be strings")
    if isinstance(version_code, bool) or not isinstance(version_code, int):
        raise BomError("version_code must be an integer")
    epoch = _source_date_epoch(config, source_date_epoch)
    bom = {
        "schema_version": 1,
        "channel": channel,
        "application_id": application_id,
        "version_name": version_name,
        "version_code": version_code,
        "repositories": _repository_pins(config, base_dir),
        "upstreams": _upstream_pins(config),
        "tdlib_schema_hash": _digest_input(config, "tdlib_schema_path", "tdlib_schema_hash", base_dir, "TDLib schema"),
        "toolchain": _toolchain(config),
        "dependency_verification_sha256": _digest_input(config, "dependency_verification_path", "dependency_verification_sha256", base_dir, "dependency verification metadata"),
        "unsigned_artifact": _artifact(config, base_dir),
        "generated_at": _generated_at(epoch),
    }
    validate_bom(bom)
    return bom


def write_bom(path: Path, bom: Mapping[str, Any]) -> None:
    """Write a validated BOM with stable JSON formatting."""
    validate_bom(bom)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.tmp")
    try:
        temporary.write_text(json.dumps(bom, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
        temporary.replace(path)
    except OSError as error:
        raise BomError(f"cannot write BOM: {error}") from error


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", "--input", required=True, type=Path, help="JSON build input document")
    parser.add_argument("--out", required=True, type=Path, help="output build-bom.json path")
    parser.add_argument("--source-date-epoch", type=int, help="override SOURCE_DATE_EPOCH")
    args = parser.parse_args(argv)
    try:
        config_path = args.config.resolve()
        bom = generate_bom(
            load_config(config_path),
            base_dir=config_path.parent,
            source_date_epoch=args.source_date_epoch,
        )
        write_bom(args.out, bom)
    except (BomError, OSError) as error:
        print(f"build BOM generation failed: {error}", file=sys.stderr)
        return 2
    print(f"build BOM written: {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
