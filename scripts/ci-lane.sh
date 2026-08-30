#!/usr/bin/env bash
# Run one internal-build CI lane locally.
set -euo pipefail

ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$ROOT"

lane=${1:-}
if [[ -z "$lane" ]]; then
    printf 'usage: %s {pin-validation|jvm-fake-adapter|compose-compilation|internal-artifacts}\n' "$0" >&2
    exit 2
fi

ci_only() {
    printf 'CI-only requirement: %s\n' "$1"
}

gradle() {
    if [[ ! -x "$ROOT/gradlew" ]]; then
        ci_only "the app composition build (gradlew and its pinned JDK/toolchain) must be present"
        return 0
    fi
    if [[ -x "$ROOT/scripts/gradle-strict.sh" ]]; then
        "$ROOT/scripts/gradle-strict.sh" "$@" --no-configuration-cache
    else
        "$ROOT/gradlew" "$@"
    fi
}

validate_archive() {
    local artifact=$1
    test -s "$artifact"
    unzip -tqq "$artifact"
    sha256sum "$artifact"
    printf 'Archive validation: %s OK\n' "$artifact"
}

case "$lane" in
    pin-validation)
        python3 scripts/validate-ci-pins.py
        python3 scripts/check-docs.py
        python3 scripts/validate-contracts.py
        ;;
    jvm-fake-adapter)
        if [[ -f "$ROOT/core/build.gradle.kts" && -x "$ROOT/gradlew" ]]; then
            gradle --no-daemon --console=plain -p core \
                :core-api:test :core-storage:test :core-tdlib:test
        else
            ci_only "the app repository must provide core JVM/fake-adapter tests"
        fi
        ;;
    compose-compilation)
        if [[ -x "$ROOT/gradlew" ]]; then
            gradle --no-daemon --console=plain tasks --all
            if [[ -f "$ROOT/core/build.gradle.kts" ]]; then
                gradle --no-daemon --console=plain -p core \
                    :core-api:classes :core-storage:classes :core-tdlib:classes
            else
                ci_only "the core included build must be checked out before compiling the composite"
            fi
            if [[ -f "$ROOT/gui/build.gradle.kts" ]]; then
                gradle --no-daemon --console=plain -p gui classes
            else
                ci_only "the gui included build must be checked out before compiling the composite"
            fi
            if [[ -d "$ROOT/mods" ]]; then
                python3 -m compileall -q mods
            else
                ci_only "the mods source must be checked out before compiling the composite"
            fi
        else
            ci_only "the app repository must provide gradlew and checked-out core/mods/gui included builds"
        fi
        ;;
    internal-artifacts)
        if [[ -x "$ROOT/gradlew" && -f "$ROOT/app/build.gradle.kts" ]]; then
            gradle --no-daemon --console=plain \
                :app:assembleInternal :app:validateNativeLibraries :app:validateInternalArchive
        else
            ci_only "the app repository must provide an Android app module and an assembleInternal task"
        fi

        shopt -s nullglob globstar
        artifacts=("$ROOT"/**/*.apk "$ROOT"/**/*.aab)
        if ((${#artifacts[@]} == 0)); then
            ci_only "an internal APK/AAB must be produced before archive validation"
        else
            for artifact in "${artifacts[@]}"; do
                validate_archive "$artifact"
            done
        fi
        ;;
    *)
        printf 'unknown CI lane: %s\n' "$lane" >&2
        exit 2
        ;;
esac
