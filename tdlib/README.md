# Pinned TDLib build

`tdlib.lock.json` is the single source of truth for the TDLib commit, schema
hash, generator version, supported Android ABIs, and native toolchain floor.
The checked-in `core/raw/td_api.tl` is the exact schema input used to generate
Kotlin declarations.

Build one ABI with:

```bash
JAVA_HOME=/path/to/jdk-21 \
ANDROID_NDK_HOME=/path/to/ndk/26.* \
ANDROID_HOME=/path/to/android-sdk \
OPENSSL_ROOT_DIR=/path/to/prebuilt/openssl/<abi> \
ANDROID_ABI=arm64-v8a \
./scripts/build-tdlib.sh
```

Build the pinned Android OpenSSL prerequisites with the upstream script first:

```bash
cd .build/tdlib-src/example/android
./build-openssl.sh "$ANDROID_HOME" "26.3.11579264" "$PWD/../../../openssl" "OpenSSL_1_1_1w"
```

The script fetches and checks out the exact commit, verifies `HEAD`, configures
the upstream `example/android` JSON-Java target for Android 21, and installs
`libtdjsonjava.so` under `tdlib/prebuilt/<abi>/lib`. This is the native library
loaded by the pinned official `org.drinkless.tdlib.JsonClient` source. A plain
`libtdjson.so` is not JNI-compatible and must not be packaged as a substitute.
Repeat with `ANDROID_ABI=x86_64` for the second release ABI. Native outputs are
build artifacts and are intentionally not checked in.
