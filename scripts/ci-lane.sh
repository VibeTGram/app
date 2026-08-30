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
        "$ROOT/scripts/gradle-strict.sh" "$@"
    else
        "$ROOT/gradlew" "$@"
    fi
}

has_gradle_task() {
    local project=$1
    local task=$2
    local build_file="$ROOT/$project/build.gradle.kts"
    [[ -f "$build_file" && "$task" =~ ^(classes|test)$ ]] || return 1
    grep -Eq '^[[:space:]]*(id\("(java|java-library|application|org\.jetbrains\.kotlin\.jvm)"\)|kotlin\("jvm"\))( version "[^"]+")?[[:space:]]*$' "$build_file" || return 1
    local plugin_version
    plugin_version=$(grep -E '^[[:space:]]*kotlin\("jvm"\) version "[^"]+"' "$build_file" \
        | sed -E 's/.*version "([^"]+)".*/\1/' | sed -n '1p')
    if [[ -n "$plugin_version" ]]; then
        local metadata="$ROOT/$project/gradle/verification-metadata.xml"
        [[ -f "$metadata" ]] || return 1
        grep -Fq "version=\"$plugin_version\"" "$metadata" || return 1
    fi
}

has_applied_gradle_plugin() {
    local project=$1
    local build_file="$ROOT/$project/build.gradle.kts"
    [[ -f "$build_file" ]] || return 1
    grep -Eq '^[[:space:]]*(id\("(java|java-library|application|org\.jetbrains\.kotlin\.jvm)"\)|kotlin\("jvm"\))( version "[^"]+")?[[:space:]]*$' "$build_file"
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
            if has_gradle_task core test; then
                gradle --no-daemon --console=plain -p core test
            else
                ci_only "the checked-out core build does not expose a test task yet"
            fi
        else
            ci_only "the app repository must provide core JVM/fake-adapter tests (expected command: scripts/gradle-strict.sh --no-daemon --console=plain -p core test)"
        fi
        ;;
    compose-compilation)
        if [[ -x "$ROOT/gradlew" ]]; then
            for project in core mods gui; do
                if [[ -f "$ROOT/$project/build.gradle.kts" ]]; then
                    if has_gradle_task "$project" classes; then
                        gradle --no-daemon --console=plain -p "$project" classes
                    else
                        ci_only "the checked-out $project build does not expose a classes task yet"
                    fi
                else
                    ci_only "the $project included build must be checked out before compiling the composite"
                fi
            done
        else
            ci_only "the app repository must provide gradlew and checked-out core/mods/gui included builds"
        fi
        ;;
    internal-artifacts)
        if [[ -x "$ROOT/gradlew" && -f "$ROOT/app/build.gradle.kts" ]]; then
            blocked_project=""
            for project in core mods gui; do
                if has_applied_gradle_plugin "$project" && ! has_gradle_task "$project" classes; then
                    blocked_project=$project
                    break
                fi
            done
            if [[ -n "$blocked_project" ]]; then
                ci_only "the checked-out $blocked_project build lacks strict verification metadata for its applied plugin"
            else
                gradle --no-daemon --console=plain :app:assembleInternal
            fi
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
