#!/usr/bin/env python3
"""Require immutable commit pins for third-party GitHub Actions."""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW_DIR = ROOT / ".github" / "workflows"
USES = re.compile(r"^\s*uses:\s*([^\s#]+)")
FULL_SHA = re.compile(r"^[0-9a-f]{40}(?:[0-9a-f]{24})?$")


def main() -> None:
    workflows = sorted(WORKFLOW_DIR.glob("*.y*ml"))
    if not workflows:
        raise SystemExit(f"no workflow files found under {WORKFLOW_DIR}")

    errors: list[str] = []
    action_count = 0
    for workflow in workflows:
        for number, line in enumerate(workflow.read_text(encoding="utf-8").splitlines(), 1):
            match = USES.match(line)
            if not match:
                continue
            target = match.group(1)
            if target.startswith("./"):
                continue
            action_count += 1
            if "@" not in target:
                errors.append(f"{workflow.relative_to(ROOT)}:{number}: action is not pinned: {target}")
                continue
            _, revision = target.rsplit("@", 1)
            if not FULL_SHA.fullmatch(revision):
                errors.append(
                    f"{workflow.relative_to(ROOT)}:{number}: action must use a full commit SHA: {target}"
                )

    if errors:
        raise SystemExit("\n".join(errors))
    print(f"GitHub Actions immutable pins: {action_count} action references OK")


if __name__ == "__main__":
    main()
