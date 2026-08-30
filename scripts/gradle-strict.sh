#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"

for argument in "$@"; do
    case "$argument" in
        --dependency-verification|--dependency-verification=*|-F|-F*)
            printf '%s\n' 'gradle-strict.sh: dependency verification mode is enforced as strict' >&2
            exit 2
            ;;
    esac
done

exec "$ROOT/gradlew" "$@" --dependency-verification=strict
