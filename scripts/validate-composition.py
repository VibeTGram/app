#!/usr/bin/env python3
"""Validate app submodule declarations and Gradle composite wiring."""

from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
EXPECTED = {
    "core": (
        "https://github.com/VibeTGram/core.git",
        "b99cd91e5a10a11f1c60108890f452631f8b02f3",
    ),
    "mods": (
        "https://github.com/VibeTGram/mods.git",
        "32085a55aaf7309a8422ef83fd1ac35566df74e9",
    ),
    "gui": (
        "https://github.com/VibeTGram/gui.git",
        "c2d92ceada10c370b73dcc5e69aea261551d2bb3",
    ),
}
COMMIT = re.compile(r"^[0-9a-f]{40}$")


def fail(message: str) -> None:
    raise SystemExit(f"composition validation failed: {message}")


def properties(path: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        key, separator, value = line.partition("=")
        if not separator or not key.strip():
            fail(f"invalid property line: {line!r}")
        result[key.strip()] = value.strip()
    return result


def check_submodules() -> None:
    text = (ROOT / ".gitmodules").read_text(encoding="utf-8")
    for name, (url, commit) in EXPECTED.items():
        expected = (
            f'[submodule "{name}"]\n'
            f"\tpath = {name}\n"
            f"\turl = {url}\n"
        )
        if expected not in text:
            fail(f".gitmodules is missing the exact {name} declaration")
        if f"branch =" in text:
            fail(".gitmodules must not declare a floating branch")
        if not COMMIT.fullmatch(commit):
            fail(f"invalid {name} commit pin")


def check_pins() -> None:
    values = properties(ROOT / "gradle/upstreams.properties")
    for name, (url, commit) in EXPECTED.items():
        if values.get(f"{name}.path") != name:
            fail(f"{name}.path is not {name!r}")
        if values.get(f"{name}.repository") != url:
            fail(f"{name}.repository is not the canonical HTTPS URL")
        if values.get(f"{name}.commit") != commit:
            fail(f"{name}.commit does not match the reviewed gitlink")
        if not COMMIT.fullmatch(values.get(f"{name}.commit", "")):
            fail(f"{name}.commit is not a full lowercase commit")


def check_gradle() -> None:
    settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
    if 'rootProject.name = "vibetgram-app"' not in settings:
        fail("root project name is not vibetgram-app")
    for name in EXPECTED:
        if f'includeBuild("{name}")' not in settings:
            fail(f"settings.gradle.kts does not include {name}")
        build = ROOT / name
        if not (build / "settings.gradle.kts").is_file():
            fail(f"included build {name} has no settings.gradle.kts")
        if not (build / "build.gradle.kts").is_file():
            fail(f"included build {name} has no build.gradle.kts")


def check_documentation() -> None:
    docs = (ROOT / "docs/repositories.md").read_text(encoding="utf-8")
    for name, (url, _) in EXPECTED.items():
        docs_url = url.removesuffix(".git")
        if f"| `{name}` | {docs_url} |" not in docs:
            fail(f"repository ownership documentation is missing {name}")
    if "`core`, `mods`, and `gui` are HTTPS Git submodules of `app`." not in docs:
        fail("repository composition rule is not documented")


def main() -> None:
    check_submodules()
    check_pins()
    check_gradle()
    check_documentation()
    print("Submodule pins, repository ownership, and Gradle composite wiring: OK")


if __name__ == "__main__":
    main()
