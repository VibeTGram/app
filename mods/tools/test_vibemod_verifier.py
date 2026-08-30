from __future__ import annotations

import hashlib
import io
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from vibemod_verifier import VerificationError, canonical_tree_sha256, verify_package


KEY_ID = "sha256:" + ("a" * 64)


def package_bytes(package_type: str = "vibemod", extra: dict[str, bytes] | None = None) -> bytes:
    files: dict[str, bytes] = {
        "manifest.json": json.dumps(
            {
                "schema_version": 1,
                "type": package_type,
                "id": "org.example.demo",
                "version": "1.0.0",
                "name": {"en": "Demo"},
                "description": {"en": "Demo package"},
                "publisher": {"key_id": KEY_ID},
                "entrypoint": "main.luau" if package_type == "vibemod" else None,
                "api": {"semantic": {"version": "1.0.0"}} if package_type == "vibemod" else None,
                "capabilities": [] if package_type == "vibemod" else None,
                "gui": {"version": {"exact": "1.0.0"}} if package_type == "vibetheme" else None,
                "resources": {"tokens": ["tokens/base.json"]} if package_type == "vibetheme" else None,
                "licenses": ["Apache-2.0"],
            },
            separators=(",", ":"),
        ).encode(),
        "publisher.json": json.dumps({"key_id": KEY_ID}, separators=(",", ":")).encode(),
        "signature.ed25519": b"{}",
    }
    if package_type == "vibemod":
        files["main.luau"] = b"return { name = 'demo' }\n"
    else:
        files["tokens/base.json"] = b"{\"primary\":\"#6750A4\"}\n"
    if extra:
        files.update(extra)
    hashes = {
        "schema_version": 1,
        "files": {
            name: {"sha256": hashlib.sha256(data).hexdigest(), "size_bytes": len(data)}
            for name, data in files.items()
            if name not in {"hashes.json", "signature.ed25519"}
        },
    }
    files["hashes.json"] = json.dumps(hashes, sort_keys=True, separators=(",", ":")).encode()
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for name, data in files.items():
            archive.writestr(name, data)
    return output.getvalue()


class PackageVerifierTests(unittest.TestCase):
    def verify_bytes(self, data: bytes, suffix: str = ".vibemod"):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / f"demo{suffix}"
            path.write_bytes(data)
            return verify_package(str(path))

    def test_valid_vibemod_checks_hashes_and_tree(self) -> None:
        report = self.verify_bytes(package_bytes())
        self.assertEqual(report.package_type, "vibemod")
        self.assertFalse(report.signature_verified)
        self.assertEqual(len(report.tree_sha256), 64)
        self.assertIn("main.luau", report.files)

    def test_valid_vibetheme_is_declarative(self) -> None:
        report = self.verify_bytes(package_bytes("vibetheme"), ".vibetheme")
        self.assertEqual(report.package_type, "vibetheme")
        self.assertIn("tokens/base.json", report.files)

    def test_duplicate_json_keys_are_rejected(self) -> None:
        source = package_bytes()
        output = io.BytesIO()
        with zipfile.ZipFile(io.BytesIO(source)) as archive, zipfile.ZipFile(
            output, "w", compression=zipfile.ZIP_DEFLATED
        ) as target:
            for item in archive.infolist():
                payload = archive.read(item)
                if item.filename == "manifest.json":
                    payload = payload.replace(
                        b'"type":"vibemod"',
                        b'"type":"vibemod","type":"vibemod"',
                    )
                target.writestr(item, payload)
        with self.assertRaises(VerificationError):
            self.verify_bytes(output.getvalue())

    def test_traversal_path_is_rejected(self) -> None:
        with self.assertRaises(VerificationError):
            self.verify_bytes(package_bytes(extra={"../escape.txt": b"x"}))

    def test_case_fold_duplicate_is_rejected(self) -> None:
        with self.assertRaises(VerificationError):
            self.verify_bytes(package_bytes(extra={"MAIN.LUAU": b"other"}))

    def test_hash_mismatch_is_rejected(self) -> None:
        data = package_bytes()
        output = io.BytesIO()
        with zipfile.ZipFile(io.BytesIO(data)) as source, zipfile.ZipFile(
            output, "w", compression=zipfile.ZIP_DEFLATED
        ) as target:
            for item in source.infolist():
                payload = source.read(item)
                if item.filename == "main.luau":
                    payload += b"tampered"
                target.writestr(item, payload)
        with self.assertRaises(VerificationError):
            self.verify_bytes(output.getvalue())

    def test_theme_cannot_contain_luau(self) -> None:
        with self.assertRaises(VerificationError):
            self.verify_bytes(package_bytes("vibetheme", {"main.luau": b"return {}"}), ".vibetheme")

    def test_forbidden_native_file_is_rejected(self) -> None:
        with self.assertRaises(VerificationError):
            self.verify_bytes(package_bytes(extra={"lib.so": b"native"}))


if __name__ == "__main__":
    unittest.main()
