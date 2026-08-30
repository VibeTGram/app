#!/usr/bin/env python3
"""Generate the checked-in public Luau SDK artifacts deterministically."""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEC_PATH = ROOT / "mods" / "sdk" / "spec.json"
OUT_DIR = ROOT / "mods" / "sdk" / "generated"
sys.path.insert(0, str(ROOT))

from mods.sdk import FacadeSpec, MethodSpec, SdkSpec, TypeSpec, generate_luau_sdk


def load_spec() -> SdkSpec:
    data = json.loads(SPEC_PATH.read_text(encoding="utf-8"))
    return SdkSpec(
        version=data["version"],
        types=tuple(TypeSpec(item["name"], tuple(tuple(field) for field in item["fields"])) for item in data["types"]),
        facades=tuple(
            FacadeSpec(
                item["name"],
                item["capability"],
                tuple(MethodSpec(method["name"], tuple(method["parameters"]), method["result"]) for method in item["methods"]),
            )
            for item in data["facades"]
        ),
    )


def main() -> None:
    generated = generate_luau_sdk(load_spec())
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    (OUT_DIR / "types.luau").write_text(generated.types_luau, encoding="utf-8")
    (OUT_DIR / "facades.luau").write_text(generated.facades_luau, encoding="utf-8")
    (OUT_DIR / "manifest.json").write_text(
        json.dumps({"sdk_version": load_spec().version, "source_sha256": generated.source_sha256}, sort_keys=True, indent=2) + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
