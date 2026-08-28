#!/usr/bin/env python3
"""Check local Markdown links and whitespace in bootstrap text files."""

from __future__ import annotations

import re
from pathlib import Path
from urllib.parse import unquote


ROOT = Path(__file__).resolve().parents[1]
LINK = re.compile(r"\[[^\]]*\]\(([^)]+)\)")
TEXT_SUFFIXES = {".md", ".json", ".py"}


def main() -> None:
    errors: list[str] = []
    files = sorted(
        path
        for path in ROOT.rglob("*")
        if path.is_file() and ".git" not in path.parts and path.suffix in TEXT_SUFFIXES
    )

    for path in files:
        text = path.read_text(encoding="utf-8")
        for number, line in enumerate(text.splitlines(), start=1):
            if line.endswith((" ", "\t")):
                errors.append(f"{path.relative_to(ROOT)}:{number}: trailing whitespace")

        if path.suffix != ".md":
            continue
        for match in LINK.finditer(text):
            raw = match.group(1).strip()
            if raw.startswith(("https://", "http://", "mailto:", "#")):
                continue
            target_text = unquote(raw.split("#", 1)[0]).strip("<>")
            if not target_text:
                continue
            target = (path.parent / target_text).resolve()
            if not target.exists():
                line = text.count("\n", 0, match.start()) + 1
                errors.append(
                    f"{path.relative_to(ROOT)}:{line}: missing local link target {target_text}"
                )

    if errors:
        raise SystemExit("\n".join(errors))
    print(f"Markdown local links and whitespace: {len(files)} files OK")


if __name__ == "__main__":
    main()
