#!/usr/bin/env python3
"""Command-line entry point for deterministic build BOM generation."""
from __future__ import annotations

import sys
from pathlib import Path

# Make the sibling module importable when this file is invoked directly.
sys.path.insert(0, str(Path(__file__).resolve().parent))

from build_bom import main  # noqa: E402


if __name__ == "__main__":
    raise SystemExit(main())
