# R5 integration-gate report

Date: 2026-08-30
Task: `t_477227bb` (recovery verification after stale task `t_a8318254`)
TDLib source pin: `022d60202e446ad1287b9fb68e687c8a0760788b`

## Outcome

R1 Core/native, R2 GUI, R3 Android host, and R4 Mods policy/verifier changes are integrated in the main checkout. The Android host now constructs the real TDLib engine and account manager directly and adapts them to the typed GUI services. Native-loading or credential failures remain explicit; there is no DemoData fallback in the launcher path.

This is a verified **development client slice**, not a Stable release. The development APK is buildable, signed with the Android debug certificate, installable, and exercised on an API 35 x86_64 emulator. Real Telegram authorization and live chat/send verification remain blocked because no operator-supplied `api_id`/`api_hash`, phone/SMS/2FA secrets, or disposable account were provided.

## Integrated seams

- Official `JsonClient.java` and `libtdjsonjava.so` are staged and loaded with the same `tdjsonjava` identity for `arm64-v8a` and `x86_64`.
- `AndroidCoreBootstrapProvider` creates `TdJsonClientManager`, `TdLibAccountRuntimeFactory`, encrypted account storage, and per-account execution ports.
- `CoreGuiDependenciesAdapter` maps authorization, account, chat, history, text-send, draft, and logout operations from Core to the GUI contracts. Unsupported photo/document/edit/delete and multi-account creation operations fail closed.
- TDLib `getChats` responses are decoded as chat IDs and hydrated with `getChat`; signed Telegram chat identifiers are accepted.
- Account encryption keys cross the storage/runtime boundary as clearable `ByteArray` values and are cleared after runtime creation.
- R4 package-policy and verifier changes are present. The host keeps account-scoped Mods execution ports fail-closed; no unverified package is enabled.
- Missing Telegram API credentials show `TELEGRAM_API_CREDENTIALS_UNAVAILABLE` in the actual launcher instead of fake data.

## Verification

### Module and integration gates

Passed:

```text
./gradlew --no-daemon --no-configuration-cache \
  :app:testDebugUnitTest :gui:test \
  :core:core-api:test :core:core-storage:test :core:core-tdlib:test \
  :app:validateNativeLibraries
BUILD SUCCESSFUL

uv run --with pytest pytest -q
52 passed

python3 -m unittest discover -s mods/tools -p 'test_*.py'
Ran 8 tests — OK

python3 -m compileall -q mods tests
python3 scripts/check-docs.py
Markdown local links and whitespace: 93 files OK

bash scripts/ci-lane.sh pin-validation
GitHub Actions immutable pins: 7 action references OK
Schema meta-validation: 14 schemas OK
Schema positive/negative fixtures: 14 schemas OK
Targeted security regression fixtures: OK
```

Dependency verification remains strict. Exact JUnit module checksums and the scoped upstream JUnit signing key are recorded; metadata/signature verification was not disabled.

The recovery run found one stale assertion in `TdLibAccountRuntimeFactoryTest`: it inspected the retained setup request after `TdLibEngine` had deliberately zeroized its encryption-key buffer. The test client now records only key size/non-zero state synchronously at the `TdClient.send` boundary and separately asserts that the retained request is zeroized. The targeted test and the full strict Gradle gate then passed with pinned JDK 21.

### Android build and APK

```text
./gradlew --no-daemon --no-configuration-cache :app:assembleDevelopment
BUILD SUCCESSFUL
```

Artifact:

- Path: `app/build/outputs/apk/development/app-development.apk`
- SHA-256: `45c89cbd6a2bc8fa5b6c1243e12133b7f994306a08e3ab7e7b4f61642c9b1455`
- Package: `org.vibetgram.client.preview`
- Launcher: `org.vibetgram.app.MainActivity`
- Version: `1` / `0.1.0`
- min/target SDK: `30` / `36`
- Signature: APK Signature Scheme v2, Android debug certificate
- Signer certificate SHA-256: `2035d635eb207cf6e3b068b389f2d20687274877c3cd3076d59b09066eb904b1`
- `unzip -t`: passed
- `zipalign -c -P 16 -v 4`: passed
- `apksigner verify --verbose --print-certs`: passed
- `aapt2 dump badging`: passed

Packaged TDLib:

| ABI | Entry | SHA-256 |
| --- | --- | --- |
| arm64-v8a | `lib/arm64-v8a/libtdjsonjava.so` | `3cb3bc5e07f397fb5840d7a00c8a4c6c020705a7aa1a8f52722305a77737b6d2` |
| x86_64 | `lib/x86_64/libtdjsonjava.so` | `7cd1f9cb19fd73058bf7d7e3473efe4107cb20cc7c0e5257f400e41092555979` |

ELF checks identify AArch64 and x86-64 respectively; x86_64 has SONAME `libtdjsonjava.so`, `JNI_OnLoad`, and only Android system-library dependencies.

### Emulator host smoke

Device: AVD `vibetgram-api35`, API 35, x86_64, KVM acceleration.

Passed:

- boot completion verified (`sys.boot_completed=1`);
- streamed APK installation;
- device `pm path` read-back;
- installed and local APK hashes matched exactly;
- launcher Activity cold-started and remained resumed;
- process remained alive;
- UIAutomator and screenshot captured;
- visible fail-closed state was `Telegram Core unavailable` / `TELEGRAM_API_CREDENTIALS_UNAVAILABLE`;
- screenshot review found no status/navigation inset overlap, clipping, or readability defect.

Evidence:

- `app/build/outputs/device-smoke/identity.txt`
- `app/build/outputs/device-smoke/uiautomator.xml`
- `app/build/outputs/device-smoke/screenshot.png`

Evidence SHA-256:

- `identity.txt`: `b3d42096b6bcb68940c9faf57c40bf94c62fb82a4411193809d941049237b8ba`
- `uiautomator.xml`: `fc7560b3b5a3f4f6fdbd5216564f1f9fa4bd3ff413416e2a3fc5d68013de3d94`
- `screenshot.png`: `0f7e366420710c05f3322476b23429e0e5a18fbb2f525c0b81ef38599e459c9b`

## Blocker matrix

| Gate | State | Evidence / required operator action |
| --- | --- | --- |
| TDLib source pin | Passed | Pin `022d60202e446ad1287b9fb68e687c8a0760788b`; verified native handoff hashes. |
| Native ABI/package/load | Passed | Both required ABIs packaged as `libtdjsonjava.so`; host reached the later credentials gate. |
| Core-to-GUI production seam | Passed statically and on host startup | Adapter tests and actual launcher composition pass; no DemoData fallback. |
| Real Telegram authorization | Blocked | Supply valid Telegram `api_id`/`api_hash` locally and manually enter phone/SMS/2FA secrets. |
| Live chat list/history/send smoke | Blocked | Requires successful authorization and a disposable Telegram account. |
| FCM/background notifications | Blocked/out of this slice | No verified FCM project/configuration or notification delivery evidence. |
| Stable signing/release identity | Blocked by design | No Stable signing keys or approval were supplied. Development identity remains isolated. |
| Stable publication | Not attempted | No commit, push, PR, upload, or Stable release was performed. |

## R6 handoff

R6 may audit the integrated development slice and the recorded blockers. It must not promote this artifact as Stable or claim real Telegram-network acceptance until credentials, test-account authorization, chat/history/send, FCM/background behavior, and Stable-key gates are independently verified.
