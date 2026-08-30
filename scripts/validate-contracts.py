#!/usr/bin/env python3
"""Validate VibeTGram JSON Schemas and their bootstrap conformance fixtures."""

from __future__ import annotations

import copy
import json
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker


ROOT = Path(__file__).resolve().parents[1]
SCHEMA_DIR = ROOT / "schemas"
HEX = "a" * 64
HEX_B = "b" * 64
KEY_ID = f"sha256:{HEX}"
KEY_ID_B = f"sha256:{HEX_B}"
COMMIT = "c" * 40
PUBLIC_KEY = "A" * 43
SIGNATURE = "A" * 86
NOW = "2026-08-15T00:00:00Z"
LATER = "2026-08-16T00:00:00Z"
SEMVER_RANGE = {"minimum_inclusive": "1.0.0", "maximum_exclusive": "2.0.0"}


def signature(role: str) -> dict:
    return {
        "schema_version": 1,
        "algorithm": "ed25519",
        "role": role,
        "key_id": KEY_ID,
        "signed_sha256": HEX,
        "signature": SIGNATURE,
    }


def repository_pin(name: str) -> dict:
    return {
        "name": name,
        "repository": f"https://github.com/VibeTGram/{name}",
        "commit": COMMIT,
    }


FIXTURES = {
    "vibemod.schema.json": {
        "schema_version": 1,
        "type": "vibemod",
        "id": "org.example.mod",
        "version": "1.0.0",
        "name": {"en": "Example"},
        "description": {"en": "Example mod"},
        "publisher": {"key_id": KEY_ID},
        "entrypoint": "main.luau",
        "api": {"semantic": SEMVER_RANGE},
        "capabilities": [],
        "licenses": ["Apache-2.0"],
    },
    "vibetheme.schema.json": {
        "schema_version": 1,
        "type": "vibetheme",
        "id": "org.example.theme",
        "version": "1.0.0",
        "name": {"en": "Example"},
        "description": {"en": "Example theme"},
        "publisher": {"key_id": KEY_ID},
        "gui": {"version": {"exact": "1.0.0"}},
        "resources": {"tokens": ["tokens/base.json"]},
        "licenses": ["CC0-1.0"],
    },
    "publisher.schema.json": {
        "schema_version": 1,
        "algorithm": "ed25519",
        "key_id": KEY_ID,
        "public_key": PUBLIC_KEY,
    },
    "hashes.schema.json": {
        "schema_version": 1,
        "files": {
            "manifest.json": {"sha256": HEX, "size_bytes": 10},
            "publisher.json": {"sha256": HEX_B, "size_bytes": 20},
        },
    },
    "signature-envelope.schema.json": signature("addon-package"),
    "publisher-rotation.schema.json": {
        "schema_version": 1,
        "body": {
            "old_key_id": KEY_ID,
            "new_key_id": KEY_ID_B,
            "new_public_key": PUBLIC_KEY,
            "package_ids": ["org.example.mod"],
            "effective_at": NOW,
        },
        "old_signature": signature("publisher-rotation"),
        "new_signature": {**signature("publisher-rotation"), "key_id": KEY_ID_B},
    },
    "addon-registry-record.schema.json": {
        "schema_version": 1,
        "registry_sequence": 1,
        "addon": {"id": "org.example.mod", "type": "mod", "version": "1.0.0"},
        "source": {
            "repository": "https://github.com/example/mod",
            "verified_commit": COMMIT,
            "tree_sha256": HEX,
            "manifest_sha256": HEX_B,
        },
        "publisher": {"key_id": KEY_ID},
        "compatibility": {"semantic": SEMVER_RANGE},
        "reviewed_surface": {
            "capabilities": [],
            "domains": [],
            "raw_functions": [],
            "raw_updates": [],
            "raw_fields": [],
            "hooks": [],
            "ipc_interfaces": [],
            "dependencies": [],
            "quotas": {},
        },
        "licenses": ["Apache-2.0"],
        "review": {
            "state": "unverified",
            "reviewers": [],
            "evidence": ["https://github.com/example/mod/actions/runs/1"],
        },
        "warnings": [],
        "issue_tracker": "https://github.com/example/mod/issues",
        "published_at": NOW,
        "expires_at": LATER,
    },
    "registry-delegation.schema.json": {
        "schema_version": 1,
        "body": {
            "delegation_sequence": 1,
            "root_key_id": KEY_ID,
            "delegated_key_id": KEY_ID_B,
            "delegated_public_key": PUBLIC_KEY,
            "roles": ["index", "revocation"],
            "not_before": NOW,
            "not_after": LATER,
        },
        "signature": signature("registry-delegation"),
    },
    "registry-index.schema.json": {
        "schema_version": 1,
        "body": {
            "registry_sequence": 1,
            "delegated_key_id": KEY_ID_B,
            "delegation_sha256": HEX,
            "records": [{"path": "records/org.example.mod/1.0.0.json", "sha256": HEX}],
            "revocations": [],
            "provenance": {"path": "provenance/1.json", "sha256": HEX_B},
            "generated_at": NOW,
            "expires_at": LATER,
        },
        "signature": {**signature("registry-index"), "key_id": KEY_ID_B},
    },
    "registry-revocation.schema.json": {
        "schema_version": 1,
        "body": {
            "revocation_sequence": 1,
            "subjects": [{"kind": "record-sequence", "registry_sequence": 1}],
            "reason_category": "policy",
            "summary": "Withdrawn",
            "effective_at": NOW,
        },
        "signature": {**signature("registry-revocation"), "key_id": KEY_ID_B},
    },
    "registry-provenance.schema.json": {
        "schema_version": 1,
        "registry_sequence": 1,
        "source": {
            "repository": "https://github.com/VibeTGram/addons-market",
            "commit": COMMIT,
        },
        "workflow": {
            "provider": "github-actions",
            "repository": "https://github.com/VibeTGram/addons-market",
            "workflow_path": ".github/workflows/publish.yml",
            "workflow_commit": COMMIT,
            "run_id": 1,
            "run_attempt": 1,
            "environment": "addons-market-release",
        },
        "review_inputs": [
            {"path": "records/org.example.mod/1.0.0.json", "sha256": HEX},
            {"path": f"delegations/registry-{HEX_B}.json", "sha256": HEX},
        ],
        "generator": {"name": "vibetgram-registry", "version": "1.0.0"},
        "generated_at": NOW,
    },
    "build-bom.schema.json": {
        "schema_version": 1,
        "channel": "stable",
        "application_id": "org.vibetgram.client",
        "version_name": "1.0.0",
        "version_code": 1,
        "repositories": [
            repository_pin("app"),
            repository_pin("gui"),
            repository_pin("core"),
            repository_pin("mods"),
            repository_pin("mods-example"),
            repository_pin("addons-market"),
        ],
        "upstreams": {
            "telegram_android": {"repository": "https://github.com/DrKLO/Telegram", "commit": COMMIT},
            "tdlib": {"repository": "https://github.com/tdlib/td", "commit": COMMIT},
            "tgcalls": {"repository": "https://github.com/TelegramMessenger/tgcalls", "commit": COMMIT},
            "luau": {"repository": "https://github.com/luau-lang/luau", "commit": COMMIT},
        },
        "tdlib_schema_hash": HEX,
        "toolchain": {
            "jdk": "21",
            "gradle": "9",
            "agp": "9",
            "kotlin": "2",
            "android_sdk": "36",
            "ndk": "29",
            "cmake": "4",
        },
        "dependency_verification_sha256": HEX_B,
        "unsigned_artifact": {"filename": "app.apk", "sha256": HEX, "size_bytes": 1},
        "generated_at": NOW,
    },
    "release-bom.schema.json": {
        "schema_version": 1,
        "channel": "stable",
        "application_id": "org.vibetgram.client",
        "version_name": "1.0.0",
        "version_code": 1,
        "build_bom_sha256": HEX,
        "signed_artifact": {"filename": "app.apk", "sha256": HEX_B, "size_bytes": 1},
        "signing_certificate_sha256": HEX,
        "produced_at": NOW,
    },
    "update-manifest.schema.json": {
        "schema_version": 1,
        "channel": "stable",
        "application_id": "org.vibetgram.client",
        "update_key_id": KEY_ID,
        "version_name": "1.0.0",
        "version_code": 1,
        "artifact": {
            "url": "https://github.com/VibeTGram/app/releases/download/v1.0.0/app.apk",
            "sha256": HEX,
            "size_bytes": 1,
        },
        "signing_certificate_sha256": HEX_B,
        "bom": {
            "url": "https://github.com/VibeTGram/app/releases/download/v1.0.0/release-bom.json",
            "sha256": HEX,
        },
        "tdlib": {"commit": COMMIT, "schema_hash": HEX_B},
        "published_at": NOW,
        "expires_at": LATER,
    },
}


def load_validators() -> dict[str, Draft202012Validator]:
    validators = {}
    for path in sorted(SCHEMA_DIR.glob("*.schema.json")):
        schema = json.loads(path.read_text(encoding="utf-8"))
        Draft202012Validator.check_schema(schema)
        validators[path.name] = Draft202012Validator(schema, format_checker=FormatChecker())
    return validators


def require_valid(validator: Draft202012Validator, instance: dict, label: str) -> None:
    errors = sorted(validator.iter_errors(instance), key=lambda error: list(error.path))
    if errors:
        details = "; ".join(error.message for error in errors[:4])
        raise AssertionError(f"{label} unexpectedly failed: {details}")


def require_invalid(validator: Draft202012Validator, instance: dict, label: str) -> None:
    if validator.is_valid(instance):
        raise AssertionError(f"{label} unexpectedly passed")


def require_ipc_dependency_binding(instance: dict) -> None:
    required = [
        (item["id"], item["publisher_key_id"])
        for item in instance.get("dependencies", {}).get("requires", [])
    ]
    for interface in instance.get("ipc", {}).get("consumes", []):
        provider = interface["provider"]
        identity = (provider["id"], provider["publisher_key_id"])
        if required.count(identity) != 1:
            raise AssertionError("IPC consumer provider must match exactly one hard dependency")


def require_provenance_binding(index: dict, provenance: dict) -> None:
    body = index["body"]
    sequence = body["registry_sequence"]
    if provenance["registry_sequence"] != sequence:
        raise AssertionError("provenance sequence does not match index")
    if body["provenance"]["path"] != f"provenance/{sequence}.json":
        raise AssertionError("provenance path does not match index sequence")
    inputs = {(item["path"], item["sha256"]) for item in provenance["review_inputs"]}
    required = {(item["path"], item["sha256"]) for item in body["records"] + body["revocations"]}
    key_hex = body["delegated_key_id"].removeprefix("sha256:")
    required.add((f"delegations/registry-{key_hex}.json", body["delegation_sha256"]))
    if not required.issubset(inputs):
        raise AssertionError("provenance inputs do not cover signed index inputs")


def main() -> None:
    validators = load_validators()
    if set(validators) != set(FIXTURES):
        missing = sorted(set(validators) - set(FIXTURES))
        extra = sorted(set(FIXTURES) - set(validators))
        raise AssertionError(f"fixture/schema mismatch: missing={missing}, extra={extra}")

    for name, fixture in FIXTURES.items():
        require_valid(validators[name], fixture, f"{name} positive fixture")
        negative = copy.deepcopy(fixture)
        negative.pop("schema_version")
        require_invalid(validators[name], negative, f"{name} required-field negative")

    mod_validator = validators["vibemod.schema.json"]
    raw_invoke = copy.deepcopy(FIXTURES["vibemod.schema.json"])
    raw_invoke["capabilities"] = ["tdlib.raw.invoke"]
    raw_invoke["api"]["raw"] = {
        "tdlib_commit": COMMIT,
        "schema_hash": HEX,
        "generator_version": "1.0.0",
    }
    raw_invoke["raw"] = {"functions": ["getMe"]}
    require_invalid(mod_validator, raw_invoke, "raw invoke without declared fields")
    raw_invoke["raw"]["fields"] = ["user.id"]
    require_valid(mod_validator, raw_invoke, "raw invoke with fields")

    suppress = copy.deepcopy(raw_invoke)
    suppress["capabilities"] = ["tdlib.raw.suppress_incoming"]
    suppress["raw"] = {"updates": ["updateNewMessage"]}
    require_valid(mod_validator, suppress, "constructor-only suppress")

    network = copy.deepcopy(FIXTURES["vibemod.schema.json"])
    network["capabilities"] = ["network.https"]
    require_invalid(mod_validator, network, "network capability without domain declaration")
    network["network"] = {"domains": ["api.example.com"]}
    require_valid(mod_validator, network, "network capability with domain declaration")

    consume = copy.deepcopy(FIXTURES["vibemod.schema.json"])
    consume["capabilities"] = ["ipc.consume"]
    consume["dependencies"] = {
        "requires": [{
            "id": "org.example.provider",
            "publisher_key_id": KEY_ID,
            "version_range": {"exact": "1.0.0"},
        }]
    }
    consumed_interface = {
        "provider": {"id": "org.example.provider", "publisher_key_id": KEY_ID},
        "name": "example.local",
        "version": "1.0.0",
        "schema_sha256": HEX,
        "data_class": "addon-local-nonsensitive",
    }
    consume["ipc"] = {"consumes": [consumed_interface]}
    require_valid(mod_validator, consume, "publisher-bound IPC consumer")
    require_ipc_dependency_binding(consume)
    missing_provider = copy.deepcopy(consume)
    missing_provider["ipc"]["consumes"][0].pop("provider")
    require_invalid(mod_validator, missing_provider, "IPC consumer without provider")
    wrong_provider = copy.deepcopy(consume)
    wrong_provider["ipc"]["consumes"][0]["provider"]["id"] = "org.example.other"
    try:
        require_ipc_dependency_binding(wrong_provider)
    except AssertionError:
        pass
    else:
        raise AssertionError("IPC consumer without matching hard dependency unexpectedly passed")

    ipc = copy.deepcopy(FIXTURES["vibemod.schema.json"])
    ipc["capabilities"] = ["ipc.provide", "telegram.messages.read"]
    ipc["ipc"] = {
        "provides": [{
            "name": "example.local",
            "version": "1.0.0",
            "schema_sha256": HEX,
            "data_class": "addon-local-nonsensitive",
        }]
    }
    require_invalid(mod_validator, ipc, "IPC with capability outside v1 safe allowlist")

    for version in ["1.0.0-01", "1.0.0+build"]:
        bad_version = copy.deepcopy(FIXTURES["vibemod.schema.json"])
        bad_version["version"] = version
        require_invalid(mod_validator, bad_version, f"forbidden SemVer {version}")

    bad_range = copy.deepcopy(FIXTURES["vibemod.schema.json"])
    bad_range["api"]["semantic"] = "^1.0"
    require_invalid(mod_validator, bad_range, "unstructured compatibility range")
    bad_entrypoint = copy.deepcopy(FIXTURES["vibemod.schema.json"])
    bad_entrypoint["entrypoint"] = "src//main.luau"
    require_invalid(mod_validator, bad_entrypoint, "noncanonical entrypoint path")
    dot_entrypoint = copy.deepcopy(FIXTURES["vibemod.schema.json"])
    dot_entrypoint["entrypoint"] = "src/./main.luau"
    require_invalid(mod_validator, dot_entrypoint, "dot-segment entrypoint path")

    bad_theme_path = copy.deepcopy(FIXTURES["vibetheme.schema.json"])
    bad_theme_path["resources"]["tokens"] = ["tokens/"]
    require_invalid(validators["vibetheme.schema.json"], bad_theme_path, "directory resource path")

    bad_hash_path = copy.deepcopy(FIXTURES["hashes.schema.json"])
    bad_hash_path["files"]["assets/"] = {"sha256": HEX, "size_bytes": 0}
    require_invalid(validators["hashes.schema.json"], bad_hash_path, "directory hash path")

    unauthenticated_signature_time = copy.deepcopy(FIXTURES["signature-envelope.schema.json"])
    unauthenticated_signature_time["created_at"] = NOW
    require_invalid(
        validators["signature-envelope.schema.json"],
        unauthenticated_signature_time,
        "unauthenticated signature timestamp",
    )

    record_validator = validators["addon-registry-record.schema.json"]
    no_expiry = copy.deepcopy(FIXTURES["addon-registry-record.schema.json"])
    no_expiry.pop("expires_at")
    require_invalid(record_validator, no_expiry, "registry record without expiry")
    noncanonical_source = copy.deepcopy(FIXTURES["addon-registry-record.schema.json"])
    noncanonical_source["source"]["repository"] += ".git"
    require_invalid(record_validator, noncanonical_source, "noncanonical GitHub source URL")

    index_validator = validators["registry-index.schema.json"]
    no_provenance = copy.deepcopy(FIXTURES["registry-index.schema.json"])
    no_provenance["body"].pop("provenance")
    require_invalid(index_validator, no_provenance, "registry index without provenance")
    require_provenance_binding(
        FIXTURES["registry-index.schema.json"],
        FIXTURES["registry-provenance.schema.json"],
    )
    mismatched_provenance = copy.deepcopy(FIXTURES["registry-provenance.schema.json"])
    mismatched_provenance["review_inputs"] = mismatched_provenance["review_inputs"][:1]
    try:
        require_provenance_binding(FIXTURES["registry-index.schema.json"], mismatched_provenance)
    except AssertionError:
        pass
    else:
        raise AssertionError("incomplete registry provenance unexpectedly passed")

    bom_validator = validators["build-bom.schema.json"]
    wrong_repo = copy.deepcopy(FIXTURES["build-bom.schema.json"])
    wrong_repo["repositories"][0]["repository"] = "https://github.com/VibeTGram/gui"
    require_invalid(bom_validator, wrong_repo, "repository name/URL mismatch")
    wrong_upstream = copy.deepcopy(FIXTURES["build-bom.schema.json"])
    wrong_upstream["upstreams"]["tdlib"]["repository"] = "https://github.com/example/td"
    require_invalid(bom_validator, wrong_upstream, "upstream name/URL mismatch")
    wrong_channel = copy.deepcopy(FIXTURES["build-bom.schema.json"])
    wrong_channel["application_id"] = "org.vibetgram.client.nightly"
    require_invalid(bom_validator, wrong_channel, "BOM channel/application mismatch")

    oversized = copy.deepcopy(FIXTURES["update-manifest.schema.json"])
    oversized["artifact"]["size_bytes"] = 9007199254740992
    require_invalid(validators["update-manifest.schema.json"], oversized, "unsafe JCS integer")

    print(f"Schema meta-validation: {len(validators)} schemas OK")
    print(f"Schema positive/negative fixtures: {len(FIXTURES)} schemas OK")
    print("Targeted security regression fixtures: OK")


if __name__ == "__main__":
    main()
