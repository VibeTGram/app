#!/usr/bin/env bash
set -euo pipefail

# Reproducibly build the locked TDLib tdjson JNI library for one Android ABI.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOCK="$ROOT/tdlib/tdlib.lock.json"
SOURCE_DIR="${TDLIB_SOURCE_DIR:-$ROOT/.build/tdlib-src}"
BUILD_ROOT="${TDLIB_BUILD_DIR:-$ROOT/.build/tdlib}"
ABI="${ANDROID_ABI:-arm64-v8a}"
OPENSSL_ROOT_DIR="${OPENSSL_ROOT_DIR:-$ROOT/.build/openssl/$ABI}"
BUILD_JOBS="${TDLIB_BUILD_JOBS:-2}"

LOCK_DATA="$(python3 - "$LOCK" "$ABI" <<'PY'
import json
import sys
from pathlib import Path

lock = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
abi = sys.argv[2]
if abi not in lock["android_abis"]:
    raise SystemExit(f"unsupported Android ABI: {abi}")
print(lock["repository"])
print(lock["commit"])
PY
)"
REPOSITORY="${LOCK_DATA%%$'\n'*}"
COMMIT="${LOCK_DATA#*$'\n'}"

: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to the pinned Android NDK 26 installation}"
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK installation}"
: "${OPENSSL_ROOT_DIR:?Set OPENSSL_ROOT_DIR to the prebuilt OpenSSL ABI directory}"
if [[ ! -d "$OPENSSL_ROOT_DIR" ]]; then
    echo "OpenSSL directory does not exist: $OPENSSL_ROOT_DIR" >&2
    exit 1
fi

if [[ ! -d "$SOURCE_DIR/.git" ]]; then
    git clone --no-checkout "$REPOSITORY" "$SOURCE_DIR"
fi
git -C "$SOURCE_DIR" fetch --depth=1 origin "$COMMIT"
git -C "$SOURCE_DIR" checkout --detach --force "$COMMIT"
if [[ "$(git -C "$SOURCE_DIR" rev-parse HEAD)" != "$COMMIT" ]]; then
    echo "TDLib checkout is not locked to $COMMIT" >&2
    exit 1
fi

GENERATION_BUILD_ROOT="$BUILD_ROOT/generate"
cmake -S "$SOURCE_DIR" -B "$GENERATION_BUILD_ROOT" \
    -DTD_GENERATE_SOURCE_FILES=ON \
    -DCMAKE_BUILD_TYPE=Release
cmake --build "$GENERATION_BUILD_ROOT" --parallel "$BUILD_JOBS"

cmake -S "$SOURCE_DIR/example/android" -B "$BUILD_ROOT/$ABI" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$ABI" \
    -DANDROID_PLATFORM=android-21 \
    -DOPENSSL_FOUND=1 \
    -DOPENSSL_ROOT_DIR="$OPENSSL_ROOT_DIR" \
    -DOPENSSL_INCLUDE_DIR="$OPENSSL_ROOT_DIR/include" \
    -DOPENSSL_CRYPTO_LIBRARY="$OPENSSL_ROOT_DIR/lib/libcrypto.a" \
    -DOPENSSL_SSL_LIBRARY="$OPENSSL_ROOT_DIR/lib/libssl.a" \
    -DOPENSSL_LIBRARIES="$OPENSSL_ROOT_DIR/lib/libssl.a;$OPENSSL_ROOT_DIR/lib/libcrypto.a" \
    -DTD_ANDROID_JSON_JAVA=ON \
    -DCMAKE_INSTALL_PREFIX="$ROOT/tdlib/prebuilt/$ABI"
cmake --build "$BUILD_ROOT/$ABI" --target tdjni --parallel "$BUILD_JOBS"
mkdir -p "$ROOT/tdlib/prebuilt/$ABI/lib"
cp "$BUILD_ROOT/$ABI/libtdjsonjava.so" "$ROOT/tdlib/prebuilt/$ABI/lib/libtdjsonjava.so"
test -s "$ROOT/tdlib/prebuilt/$ABI/lib/libtdjsonjava.so"

printf 'Built TDLib %s for %s\n' "$COMMIT" "$ABI"
