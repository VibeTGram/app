from __future__ import annotations

import hashlib
import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).parents[1] / "scripts"))

from build_bom import BomError, generate_bom, validate_bom  # noqa: E402


REPOSITORIES = (
    ("app", "https://github.com/VibeTGram/app"),
    ("gui", "https://github.com/VibeTGram/gui"),
    ("core", "https://github.com/VibeTGram/core"),
    ("mods", "https://github.com/VibeTGram/mods"),
    ("mods-example", "https://github.com/VibeTGram/mods-example"),
    ("addons-market", "https://github.com/VibeTGram/addons-market"),
)


class BuildBomTests(unittest.TestCase):
    def make_input(self) -> tuple[dict, Path]:
        tmp = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, tmp, ignore_errors=True)
        repositories = []
        for name, url in REPOSITORIES:
            repo = tmp / name
            subprocess.run(["git", "init", "-q", str(repo)], check=True)
            (repo / "README").write_text(name, encoding="utf-8")
            subprocess.run(["git", "-C", str(repo), "add", "README"], check=True)
            subprocess.run(
                [
                    "git",
                    "-C",
                    str(repo),
                    "-c",
                    "user.name=Test",
                    "-c",
                    "user.email=test@example.invalid",
                    "commit",
                    "-q",
                    "-m",
                    "initial",
                ],
                check=True,
            )
            repositories.append({"name": name, "repository": url, "path": str(repo)})

        schema = tmp / "tdlib-schema.tl"
        schema.write_text("// pinned schema\n", encoding="utf-8")
        verification = tmp / "verification-metadata.xml"
        verification.write_text("<verification/>", encoding="utf-8")
        artifact = tmp / "vibetgram-stable-unsigned.apk"
        artifact.write_bytes(b"unsigned test apk")
        config = {
            "channel": "stable",
            "application_id": "org.vibetgram.client",
            "version_name": "1.2.3",
            "version_code": 123,
            "repositories": repositories,
            "upstreams": {
                "telegram_android": {
                    "repository": "https://github.com/DrKLO/Telegram",
                    "commit": "a" * 40,
                },
                "tdlib": {
                    "repository": "https://github.com/tdlib/td",
                    "commit": "b" * 40,
                },
                "tgcalls": {
                    "repository": "https://github.com/TelegramMessenger/tgcalls",
                    "commit": "c" * 40,
                },
                "luau": {
                    "repository": "https://github.com/luau-lang/luau",
                    "commit": "d" * 40,
                },
            },
            "tdlib_schema_path": str(schema),
            "toolchain": {
                "jdk": "21.0.8+9",
                "gradle": "9.4.1",
                "agp": "9.2.0",
                "kotlin": "2.2.0",
                "android_sdk": "36",
                "ndk": "29.0.13599879",
                "cmake": "4.1.0",
            },
            "dependency_verification_path": str(verification),
            "unsigned_artifact_path": str(artifact),
        }
        return config, tmp

    def test_generates_schema_valid_bom_with_real_digests(self) -> None:
        config, tmp = self.make_input()
        with mock.patch.dict("os.environ", {"SOURCE_DATE_EPOCH": "1700000000"}):
            bom = generate_bom(config)

        self.assertEqual(bom["generated_at"], "2023-11-14T22:13:20Z")
        self.assertEqual(bom["repositories"][0]["name"], "addons-market")
        self.assertEqual(bom["repositories"][0]["commit"], self.git_head(tmp / "addons-market"))
        self.assertEqual(
            bom["tdlib_schema_hash"],
            hashlib.sha256((tmp / "tdlib-schema.tl").read_bytes()).hexdigest(),
        )
        self.assertEqual(
            bom["dependency_verification_sha256"],
            hashlib.sha256((tmp / "verification-metadata.xml").read_bytes()).hexdigest(),
        )
        self.assertEqual(
            bom["unsigned_artifact"]["sha256"],
            hashlib.sha256((tmp / "vibetgram-stable-unsigned.apk").read_bytes()).hexdigest(),
        )
        self.assertTrue(validate_bom(bom))

    def test_same_source_date_epoch_produces_byte_identical_json(self) -> None:
        config, _ = self.make_input()
        with mock.patch.dict("os.environ", {"SOURCE_DATE_EPOCH": "1700000000"}):
            first = json.dumps(generate_bom(config), sort_keys=True, separators=(",", ":"))
            second = json.dumps(generate_bom(config), sort_keys=True, separators=(",", ":"))
        self.assertEqual(first, second)

    def test_rejects_dirty_repository_and_missing_source_date_epoch(self) -> None:
        config, tmp = self.make_input()
        (tmp / "app" / "README").write_text("changed", encoding="utf-8")
        with mock.patch.dict("os.environ", {}, clear=True):
            with self.assertRaisesRegex(BomError, "SOURCE_DATE_EPOCH"):
                generate_bom(config)
        with mock.patch.dict("os.environ", {"SOURCE_DATE_EPOCH": "1700000000"}):
            with self.assertRaisesRegex(BomError, "not clean"):
                generate_bom(config)

    def test_rejects_repository_commit_override(self) -> None:
        config, _ = self.make_input()
        config["repositories"][0]["commit"] = "f" * 40
        with mock.patch.dict("os.environ", {"SOURCE_DATE_EPOCH": "1700000000"}):
            with self.assertRaisesRegex(BomError, "commit mismatch"):
                generate_bom(config)

    def test_schema_validator_rejects_tampered_artifact_digest(self) -> None:
        config, _ = self.make_input()
        with mock.patch.dict("os.environ", {"SOURCE_DATE_EPOCH": "1700000000"}):
            bom = generate_bom(config)
        bom["unsigned_artifact"]["sha256"] = "0" * 63
        with self.assertRaisesRegex(BomError, "does not validate"):
            validate_bom(bom)

    def test_rejects_artifact_metadata_that_does_not_match_bytes(self) -> None:
        config, _ = self.make_input()
        config["unsigned_artifact"] = {
            "path": config.pop("unsigned_artifact_path"),
            "sha256": "0" * 64,
        }
        with mock.patch.dict("os.environ", {"SOURCE_DATE_EPOCH": "1700000000"}):
            with self.assertRaisesRegex(BomError, "digest mismatch"):
                generate_bom(config)

    @staticmethod
    def git_head(path: Path) -> str:
        return subprocess.check_output(["git", "-C", str(path), "rev-parse", "HEAD"], text=True).strip()


if __name__ == "__main__":
    unittest.main()
