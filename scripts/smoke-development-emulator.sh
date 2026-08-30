#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
apk=${1:-$ROOT/app/build/outputs/apk/development/app-development.apk}
serial=${ANDROID_SERIAL:-emulator-5554}
package=org.vibetgram.client.preview
activity=org.vibetgram.app.MainActivity
evidence_dir=${VIBETGRAM_SMOKE_DIR:-$ROOT/app/build/outputs/device-smoke}

command -v adb >/dev/null || { printf 'adb is required\n' >&2; exit 2; }
[[ -f "$apk" ]] || { printf 'APK missing: %s\n' "$apk" >&2; exit 2; }
mkdir -p "$evidence_dir"

timeout 30 adb -s "$serial" wait-for-device
[[ $(adb -s "$serial" get-state) == device ]] || { printf 'Device is not ready\n' >&2; exit 2; }
[[ $(adb -s "$serial" shell getprop sys.boot_completed | tr -d '\r') == 1 ]] || {
    printf 'Android boot is incomplete on %s\n' "$serial" >&2
    exit 2
}
adb -s "$serial" install -r -d "$apk"
device_record=$(adb -s "$serial" shell pm path "$package" | tr -d '\r')
device_path=${device_record#package:}
[[ -n "$device_path" ]] || { printf 'Installed package path was not found\n' >&2; exit 1; }
adb -s "$serial" shell am force-stop "$package"
adb -s "$serial" shell am start -W -n "$package/$activity"
timeout 20 adb -s "$serial" shell uiautomator dump /sdcard/vibetgram-smoke.xml
adb -s "$serial" pull /sdcard/vibetgram-smoke.xml "$evidence_dir/uiautomator.xml"
adb -s "$serial" exec-out screencap -p > "$evidence_dir/screenshot.png"
local_hash=$(sha256sum "$apk" | cut -d' ' -f1)
device_hash=$(adb -s "$serial" shell sha256sum "$device_path" | tr -d '\r' | cut -d' ' -f1)
printf 'package=%s\ndevice_path=%s\nlocal_sha256=%s\ndevice_sha256=%s\n' \
    "$package" "$device_path" "$local_hash" "$device_hash" | tee "$evidence_dir/identity.txt"
[[ "$local_hash" == "$device_hash" ]] || { printf 'Device APK hash mismatch\n' >&2; exit 1; }

# This smoke intentionally stops at the visible auth/native-blocker screen. It
# never enters a phone number, SMS code, 2FA password, api_id, or api_hash.
grep -Eq 'VibeTGram|Telegram Core unavailable|TELEGRAM_|TDLIB_|ACCOUNT_' "$evidence_dir/uiautomator.xml" || {
    printf 'Expected host state is not visible in UIAutomator output\n' >&2
    exit 1
}
