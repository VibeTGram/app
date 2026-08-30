from __future__ import annotations

import hashlib
import json
import shutil
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parents[1] / "scripts"))

from validate_internal_artifact import (  # noqa: E402
    APPLICATION_ID,
    CHANNEL,
    ArtifactValidationError,
    validate_internal_apk,
)


ANDROID_NS = "http://schemas.android.com/apk/res/android"
MANIFEST = f'''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="{ANDROID_NS}" package="{APPLICATION_ID}">
    <application android:label="VibeTGram Internal">
        <meta-data android:name="org.vibetgram.channel" android:value="{CHANNEL}" />
    </application>
</manifest>
'''.encode()


class InternalArtifactTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = Path(tempfile.mkdtemp(prefix="vibetgram-artifact-test-"))
        self.addCleanup(shutil.rmtree, self.temp_dir, ignore_errors=True)
        self.apk = self.temp_dir / "app-internal-unsigned.apk"
        self._write_apk()

    def _write_apk(self, *, manifest: bytes = MANIFEST, extra: dict[str, bytes] | None = None) -> None:
        with zipfile.ZipFile(self.apk, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            archive.writestr("AndroidManifest.xml", manifest)
            archive.writestr("classes.dex", b"dex\n035\0fixture")
            for name, data in (extra or {}).items():
                archive.writestr(name, data)

    def _write_bom(self, *, apk_name: str | None = None, digest: str | None = None, size: int | None = None) -> Path:
        bom = {
            "schema_version": 1,
            "channel": CHANNEL,
            "application_id": APPLICATION_ID,
            "unsigned_artifact": {
                "filename": apk_name or self.apk.name,
                "sha256": digest or "placeholder",
                "size_bytes": size or self.apk.stat().st_size,
            },
        }
        path = self.temp_dir / "build-bom.json"
        path.write_text(json.dumps(bom), encoding="utf-8")
        return path

    def _valid_bom(self) -> Path:
        import hashlib

        return self._write_bom(
            digest=hashlib.sha256(self.apk.read_bytes()).hexdigest(),
            size=self.apk.stat().st_size,
        )

    def test_valid_internal_archive_checks_manifest_and_bom_linkage(self) -> None:
        result = validate_internal_apk(self.apk, bom_path=self._valid_bom())

        self.assertEqual(result["application_id"], APPLICATION_ID)
        self.assertEqual(result["channel"], CHANNEL)
        self.assertEqual(result["sha256"], hashlib.sha256(self.apk.read_bytes()).hexdigest())

    def test_rejects_wrong_manifest_application_id(self) -> None:
        manifest = MANIFEST.replace(APPLICATION_ID.encode(), b"org.vibetgram.client")
        self._write_apk(manifest=manifest)

        with self.assertRaisesRegex(ArtifactValidationError, "application ID"):
            validate_internal_apk(self.apk, bom_path=self._valid_bom())

    def test_rejects_wrong_manifest_channel(self) -> None:
        manifest = MANIFEST.replace(b'android:value="nightly"', b'android:value="stable"')
        self._write_apk(manifest=manifest)

        with self.assertRaisesRegex(ArtifactValidationError, "channel"):
            validate_internal_apk(self.apk, bom_path=self._valid_bom())

    def test_rejects_signing_metadata(self) -> None:
        self._write_apk(extra={"META-INF/CERT.RSA": b"certificate"})

        with self.assertRaisesRegex(ArtifactValidationError, "signing metadata"):
            validate_internal_apk(self.apk, bom_path=self._valid_bom())

    def test_rejects_bom_digest_mismatch(self) -> None:
        bom = self._write_bom(digest="0" * 64)

        with self.assertRaisesRegex(ArtifactValidationError, "BOM artifact sha256"):
            validate_internal_apk(self.apk, bom_path=bom)

    def test_binary_manifest_without_sdk_fails_explicitly(self) -> None:
        self._write_apk(manifest=b"\x03\x00\x08\x00binary-axml-fixture")

        with self.assertRaisesRegex(ArtifactValidationError, "Android SDK/build-tools"):
            validate_internal_apk(
                self.apk,
                bom_path=self._valid_bom(),
                sdk_root=self.temp_dir / "missing-sdk",
            )

    def test_binary_manifest_uses_pinned_aapt2_when_sdk_is_available(self) -> None:
        self._write_apk(manifest=b"\x03\x00\x08\x00binary-axml-fixture")
        aapt2 = self.temp_dir / "sdk" / "build-tools" / "36.0.0" / "aapt2"
        aapt2.parent.mkdir(parents=True)
        aapt2.write_text(
            """#!/usr/bin/env bash
case "$*" in
  *"dump badging"*) printf "package: name='org.vibetgram.client.nightly' versionCode='1' versionName='0.1.0'\\n" ;;
  *"dump xmltree"*) printf 'E: meta-data\\n  A: android:name(0x01010003)="org.vibetgram.channel" (Raw: "org.vibetgram.channel")\\n  A: android:value(0x01010024)="nightly" (Raw: "nightly")\\n' ;;
esac
""",
            encoding="utf-8",
        )
        aapt2.chmod(0o755)

        result = validate_internal_apk(self.apk, bom_path=self._valid_bom(), sdk_root=aapt2.parents[2])

        self.assertEqual(result["application_id"], APPLICATION_ID)
        self.assertEqual(result["channel"], CHANNEL)

    def test_rejects_path_traversal_and_duplicate_casefolded_entries(self) -> None:
        with zipfile.ZipFile(self.apk, "w") as archive:
            archive.writestr("AndroidManifest.xml", MANIFEST)
            archive.writestr("classes.dex", b"dex")
            archive.writestr("../outside", b"bad")

        with self.assertRaisesRegex(ArtifactValidationError, "unsafe ZIP path"):
            validate_internal_apk(self.apk, bom_path=self._valid_bom())


if __name__ == "__main__":
    unittest.main()
