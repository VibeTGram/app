#!/usr/bin/env bash
# Build and validate the unsigned internal APK.
set -euo pipefail

ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$ROOT"

sdk_root=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}
if [[ -z "$sdk_root" || ! -d "$sdk_root/platforms/android-36" ]]; then
    printf 'Android SDK missing: install the pinned android-36 platform before building\n' >&2
    exit 2
fi
if [[ ! -x "$ROOT/gradlew" ]]; then
    printf 'Gradle wrapper missing: cannot run the internal Android build\n' >&2
    exit 2
fi

"$ROOT/gradlew" --no-daemon --console=plain :app:assembleInternal
artifact_dir="$ROOT/app/build/outputs/apk/internal"
shopt -s nullglob
artifacts=("$artifact_dir"/*-unsigned.apk)
if [[ "${#artifacts[@]}" -ne 1 ]]; then
    printf 'Expected exactly one internal unsigned APK under %s, found %s\n' "$artifact_dir" "${#artifacts[@]}" >&2
    exit 2
fi

bom=${VIBETGRAM_BUILD_BOM:-$ROOT/build/reports/vibetgram/build-bom.json}
if [[ ! -f "$bom" ]]; then
    printf 'Build BOM missing: set VIBETGRAM_BUILD_BOM or generate %s\n' "$bom" >&2
    exit 2
fi
python3 "$ROOT/scripts/validate_internal_artifact.py" "${artifacts[0]}" --bom "$bom"
