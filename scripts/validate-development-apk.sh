#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
apk=${1:-$ROOT/app/build/outputs/apk/development/app-development.apk}
sdk_root=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}
build_tools=${VIBETGRAM_BUILD_TOOLS:-$sdk_root/build-tools/36.0.0}

if [[ ! -f "$apk" ]]; then
    printf 'Development APK missing: %s\n' "$apk" >&2
    exit 2
fi
for tool in unzip sha256sum; do
    command -v "$tool" >/dev/null || { printf 'Required tool missing: %s\n' "$tool" >&2; exit 2; }
done
for tool in zipalign apksigner aapt2; do
    [[ -x "$build_tools/$tool" ]] || { printf 'Pinned Build Tools executable missing: %s/%s\n' "$build_tools" "$tool" >&2; exit 2; }
done

unzip -t "$apk"
"$build_tools/zipalign" -c -P 16 -v 4 "$apk"
"$build_tools/apksigner" verify --verbose --print-certs "$apk"
badging=$("$build_tools/aapt2" dump badging "$apk")
printf '%s\n' "$badging"
grep -Fq "package: name='org.vibetgram.client.preview'" <<<"$badging" || {
    printf 'Unexpected development application ID\n' >&2
    exit 1
}
grep -Fq "launchable-activity: name='org.vibetgram.app.MainActivity'" <<<"$badging" || {
    printf 'Development APK has no expected launcher Activity\n' >&2
    exit 1
}
entries=$(unzip -Z1 "$apk")
for abi in arm64-v8a x86_64; do
    grep -Fxq "lib/$abi/libtdjsonjava.so" <<<"$entries" || {
        printf 'Development APK missing libtdjsonjava.so for %s\n' "$abi" >&2
        exit 1
    }
done
sha256sum "$apk"
