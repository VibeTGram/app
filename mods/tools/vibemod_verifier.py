"""Bootstrap verifier for VibeTGram .vibemod and .vibetheme packages.

This module implements the structural and payload-integrity boundary described
in docs/modding/package-formats.md. Cryptographic signature verification is
exposed separately and is never reported as successful unless an Ed25519/JCS
backend is explicitly supplied by the caller.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import stat
import unicodedata
import zipfile
from dataclasses import dataclass
from pathlib import PurePosixPath
from typing import Any, Iterable


class VerificationError(ValueError):
    """Raised when a package violates a verifier invariant."""


@dataclass(frozen=True)
class Limits:
    max_entries: int = 4096
    max_total_size: int = 1 << 30
    max_file_size: int = 1 << 30
    max_path_length: int = 240
    max_depth: int = 32
    max_compression_ratio: int = 1000


@dataclass(frozen=True)
class VerificationReport:
    package_type: str
    files: tuple[str, ...]
    tree_sha256: str
    signature_verified: bool = False


_FORBIDDEN_SUFFIXES = frozenset(
    {
        ".apk",
        ".aab",
        ".bat",
        ".bin",
        ".cmd",
        ".com",
        ".dll",
        ".dylib",
        ".elf",
        ".exe",
        ".jar",
        ".luac",
        ".o",
        ".obj",
        ".ps1",
        ".pyc",
        ".sh",
        ".so",
        ".tar",
        ".tgz",
        ".war",
        ".zip",
    }
)
_FORBIDDEN_NAMES = frozenset({"dex", "mach-o", "pe"})
# Content magic prefixes that identify executable/native/bytecode payloads no
# matter what the file is called (docs/modding/package-formats.md §1).
_MAGIC_PREFIXES: tuple[tuple[bytes, str], ...] = (
    (b"\x1bLua", "compiled Lua/Luau bytecode"),
    (b"\x1bLaJ", "Luau JIT-compiled bytecode"),
    (b"PK\x03\x04", "nested ZIP archive"),
    (b"PK\x05\x06", "nested empty ZIP archive"),
    (b"\x7fELF", "ELF native object"),
    (b"MZ", "PE native executable"),
    (b"\xca\xfe\xba\xbe", "Mach-O fat / Java class binary"),
    (b"\xfe\xed\xfa\xce", "Mach-O native object"),
    (b"\xce\xfa\xed\xfe", "Mach-O native object"),
    (b"\xfe\xed\xfa\xcf", "Mach-O native object (64-bit)"),
    (b"\xcf\xfa\xed\xfe", "Mach-O native object (64-bit)"),
    (b"dex\n", "DEX executable"),
    (b"dey\n", "DEX odex executable"),
    (b"#!", "shell script"),
    (b"#! /", "shell script"),
)
_LUAU_SUFFIXES = frozenset({".lua", ".luau"})
_DRIVE_PREFIX = re.compile(r"^[A-Za-z]:")
_SHA256 = re.compile(r"^[0-9a-f]{64}$")


def _duplicate_rejector(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise VerificationError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def parse_json_bytes(data: bytes, *, name: str = "JSON") -> Any:
    """Parse strict UTF-8 JSON and reject BOMs and duplicate object keys."""
    if data.startswith(b"\xef\xbb\xbf"):
        raise VerificationError(f"{name}: UTF-8 BOM is not allowed")
    try:
        text = data.decode("utf-8", errors="strict")
    except UnicodeDecodeError as exc:
        raise VerificationError(f"{name}: invalid UTF-8") from exc
    try:
        return json.loads(text, object_pairs_hook=_duplicate_rejector)
    except json.JSONDecodeError as exc:
        raise VerificationError(f"{name}: invalid JSON: {exc.msg}") from exc


def _validate_path(name: str, limits: Limits) -> str:
    if not name or "\x00" in name:
        raise VerificationError("empty or NUL-containing ZIP path")
    if "\\" in name or name.startswith("/") or _DRIVE_PREFIX.match(name):
        raise VerificationError(f"unsafe ZIP path: {name!r}")
    if unicodedata.normalize("NFC", name) != name:
        raise VerificationError(f"non-NFC ZIP path: {name!r}")
    parts = name.split("/")
    if any(part in {"", ".", ".."} for part in parts):
        raise VerificationError(f"unsafe ZIP path: {name!r}")
    if len(name) > limits.max_path_length or len(parts) > limits.max_depth:
        raise VerificationError(f"ZIP path exceeds limits: {name!r}")
    # PurePosixPath is intentionally only a final sanity check; it never
    # normalizes the untrusted input used above.
    if str(PurePosixPath(name)) != name:
        raise VerificationError(f"non-canonical ZIP path: {name!r}")
    return name


def _validate_zip_entry(info: zipfile.ZipInfo, limits: Limits) -> str:
    name = _validate_path(info.filename, limits)
    if info.flag_bits & 0x1:
        raise VerificationError(f"encrypted ZIP entry: {name}")
    mode = (info.external_attr >> 16) & 0xFFFF
    file_type = stat.S_IFMT(mode)
    if file_type not in {0, stat.S_IFREG}:
        raise VerificationError(f"non-regular ZIP entry: {name}")
    if info.file_size > limits.max_file_size:
        raise VerificationError(f"ZIP entry too large: {name}")
    if info.compress_size == 0 and info.file_size:
        raise VerificationError(f"invalid zero-size compressed entry: {name}")
    if info.compress_size and info.file_size / info.compress_size > limits.max_compression_ratio:
        raise VerificationError(f"ZIP compression ratio exceeds limit: {name}")
    return name


def _reject_forbidden_file(name: str, package_type: str) -> None:
    lower = name.casefold()
    suffix = PurePosixPath(lower).suffix
    basename = PurePosixPath(lower).name
    if suffix in _FORBIDDEN_SUFFIXES or basename in _FORBIDDEN_NAMES:
        raise VerificationError(f"forbidden executable/native/archive file: {name}")
    if package_type == "vibetheme" and suffix in _LUAU_SUFFIXES:
        raise VerificationError(f"theme contains executable Luau source: {name}")


def _reject_forbidden_content(name: str, payload: bytes, package_type: str) -> None:
    """Content-level source-only enforcement, independent of file name."""
    for magic, label in _MAGIC_PREFIXES:
        if payload.startswith(magic):
            raise VerificationError(f"{name}: {label} is not permitted")
    suffix = PurePosixPath(name.casefold()).suffix
    if package_type == "vibemod" and suffix in _LUAU_SUFFIXES:
        try:
            payload.decode("utf-8", errors="strict")
        except UnicodeDecodeError as exc:
            raise VerificationError(f"{name}: Luau source must be valid UTF-8") from exc


def canonical_tree_sha256(files: dict[str, bytes]) -> str:
    """Hash a normalized file tree as specified by package-format docs."""
    chunks: list[bytes] = []
    for name in sorted(files, key=lambda value: value.encode("utf-8")):
        payload = files[name]
        digest = hashlib.sha256(payload).digest()
        chunks.append(
            name.encode("utf-8")
            + b"\0"
            + str(len(payload)).encode("ascii")
            + b"\0"
            + digest
            + b"\n"
        )
    return hashlib.sha256(b"VIBETGRAM-TREE-V1\n" + b"".join(chunks)).hexdigest()


def _read_entries(archive: zipfile.ZipFile, limits: Limits) -> dict[str, bytes]:
    infos = archive.infolist()
    if len(infos) > limits.max_entries:
        raise VerificationError("ZIP entry count exceeds limit")
    files: dict[str, bytes] = {}
    folded: dict[str, str] = {}
    total_size = 0
    for info in infos:
        # Empty directory entries are allowed structurally and are not hashed.
        if info.is_dir():
            _validate_path(info.filename.rstrip("/"), limits)
            continue
        name = _validate_zip_entry(info, limits)
        folded_name = name.casefold()
        if folded_name in folded:
            raise VerificationError(f"duplicate ZIP path after case folding: {name}")
        folded[folded_name] = name
        payload = archive.read(info)
        if len(payload) != info.file_size:
            raise VerificationError(f"ZIP size mismatch while reading: {name}")
        total_size += len(payload)
        if total_size > limits.max_total_size:
            raise VerificationError("total uncompressed ZIP size exceeds limit")
        files[name] = payload
    return files


def _check_hashes(files: dict[str, bytes], hashes: Any) -> None:
    if not isinstance(hashes, dict) or hashes.get("schema_version") != 1:
        raise VerificationError("hashes.json: expected schema_version=1")
    declared = hashes.get("files")
    if not isinstance(declared, dict) or not declared:
        raise VerificationError("hashes.json: files must be a non-empty object")
    actual_payload_names = set(files) - {"hashes.json", "signature.ed25519"}
    if set(declared) != actual_payload_names:
        missing = sorted(actual_payload_names - set(declared))
        extra = sorted(set(declared) - actual_payload_names)
        raise VerificationError(f"hashes.json file set mismatch: missing={missing}, extra={extra}")
    for name, record in declared.items():
        if not isinstance(record, dict):
            raise VerificationError(f"hashes.json: invalid record for {name}")
        digest = record.get("sha256")
        size = record.get("size_bytes")
        if not isinstance(digest, str) or not _SHA256.fullmatch(digest):
            raise VerificationError(f"hashes.json: invalid SHA-256 for {name}")
        if not isinstance(size, int) or isinstance(size, bool) or size < 0:
            raise VerificationError(f"hashes.json: invalid size for {name}")
        payload = files[name]
        if size != len(payload) or digest != hashlib.sha256(payload).hexdigest():
            raise VerificationError(f"payload hash/size mismatch: {name}")


def verify_package(path: str, *, limits: Limits = Limits()) -> VerificationReport:
    """Verify package structure and payload hashes; do not fake crypto success."""
    package_type = PurePosixPath(path).suffix.removeprefix(".").casefold()
    if package_type not in {"vibemod", "vibetheme"}:
        raise VerificationError("package extension must be .vibemod or .vibetheme")
    with zipfile.ZipFile(path, "r") as archive:
        files = _read_entries(archive, limits)
    for name, payload in files.items():
        _reject_forbidden_file(name, package_type)
        _reject_forbidden_content(name, payload, package_type)
    required = {"manifest.json", "publisher.json", "hashes.json", "signature.ed25519"}
    missing = required - set(files)
    if missing:
        raise VerificationError(f"missing required package files: {sorted(missing)}")
    manifest = parse_json_bytes(files["manifest.json"], name="manifest.json")
    if not isinstance(manifest, dict) or manifest.get("type") != package_type:
        raise VerificationError("manifest.json: type does not match package extension")
    publisher = parse_json_bytes(files["publisher.json"], name="publisher.json")
    if not isinstance(publisher, dict):
        raise VerificationError("publisher.json: expected object")
    parse_json_bytes(files["signature.ed25519"], name="signature.ed25519")
    hashes = parse_json_bytes(files["hashes.json"], name="hashes.json")
    _check_hashes(files, hashes)
    return VerificationReport(
        package_type=package_type,
        files=tuple(sorted(files)),
        tree_sha256=canonical_tree_sha256(files),
        signature_verified=False,
    )


def _main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("package")
    args = parser.parse_args()
    try:
        report = verify_package(args.package)
    except (OSError, zipfile.BadZipFile, VerificationError) as exc:
        parser.error(str(exc))
    print(json.dumps({
        "package_type": report.package_type,
        "files": list(report.files),
        "tree_sha256": report.tree_sha256,
        "signature_verified": report.signature_verified,
    }, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(_main())
