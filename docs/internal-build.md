# Internal Android artifact gate

The internal build is a deliberately small, release-like Android variant. It
uses the nightly channel identity (`org.vibetgram.client.nightly`) so it cannot
share Stable data or signing identity. The output is expected at:

```text
app/build/outputs/apk/internal/app-internal-unsigned.apk
```

The `internal` build type has no signing configuration. `scripts/validate_internal_artifact.py`
checks all of the following before an artifact can be handed to the BOM step:

- the output is a non-empty, readable ZIP with `AndroidManifest.xml` and DEX;
- ZIP paths are safe and unique, and ZIP symlinks are rejected;
- APK v1 signing metadata (`META-INF/MANIFEST.MF`, certificates and signature
  files) is absent;
- the manifest application ID and `org.vibetgram.channel` metadata are exactly
  the nightly values;
- `unsigned_artifact.filename`, `sha256` and `size_bytes` in the build BOM
  match the exact APK bytes.

Run the validator directly when an APK and BOM already exist:

```bash
python3 scripts/validate_internal_artifact.py \
  app/build/outputs/apk/internal/app-internal-unsigned.apk \
  --bom build/reports/vibetgram/build-bom.json
```

For a real APK, the validator needs the pinned Android Build Tools `aapt2` to
inspect the binary manifest. Small XML-manifest fixtures in the Python tests do
not need an SDK. If the SDK or `aapt2` is absent, validation fails explicitly;
it never treats an unreadable binary manifest as validated.

The complete local path is:

```bash
scripts/build-internal.sh
```

It requires the `android-36` SDK platform, a checked-in Gradle wrapper, and a
matching build BOM. It does not sign, upload, or publish an artifact.

## Development install artifact

The `development` build type is a locally installable Preview-identity artifact:

```text
app/build/outputs/apk/development/app-development.apk
application ID: org.vibetgram.client.preview
signer: local Android debug certificate (development testing only)
```

It never reuses the Stable application ID or signing identity. Build and verify
it with:

```bash
./gradlew --no-configuration-cache :app:assembleDevelopment
scripts/validate-development-apk.sh
```

The validator checks ZIP integrity, zip alignment, Android v2+ signing, package
and launcher identity, and pinned `arm64-v8a`/`x86_64` `libtdjsonjava.so` entries.
`app:validateNativeLibraries` and the APK validator fail closed when either
pinned TDLib binary is absent; a Compose/debug APK that contains only transitive
AndroidX native libraries is not a valid native integration artifact.

With a fully booted `vibetgram-api35` emulator, the bounded smoke is:

```bash
ANDROID_SERIAL=emulator-5554 scripts/smoke-development-emulator.sh
```

It checks boot completion, installs and cold-launches the exact APK, captures
UIAutomator/screenshot evidence, compares the local and device-side SHA-256, and
stops at the visible authorization/native-blocker screen. It never enters a
phone number, SMS code, 2FA password, Telegram `api_id`/`api_hash`, or any other
secret.

Real Telegram authorization additionally requires operator-owned application
credentials. Supply them only at build time through `telegramApiId` and
`telegramApiHash` Gradle properties, or `VIBETGRAM_TELEGRAM_API_ID` and
`VIBETGRAM_TELEGRAM_API_HASH` environment variables. Do not commit them or pass
phone, SMS, or 2FA secrets on a command line. When these values are absent, the
installed host must stop at the typed `TELEGRAM_API_CREDENTIALS_UNAVAILABLE`
state; it does not fall back to preview data.
