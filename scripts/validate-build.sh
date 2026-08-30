#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

python3 scripts/validate-build.py
python3 scripts/validate-composition.py
python3 scripts/validate-dependencies.py
scripts/gradle-strict.sh --no-daemon --console=plain --version
