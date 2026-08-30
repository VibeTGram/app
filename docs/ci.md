# Internal CI lanes

The canonical workflow is [`../.github/workflows/internal-build.yml`](../.github/workflows/internal-build.yml).
It keeps each lane as a separate job and ends with `aggregate-status`, which fails
unless every lane succeeds. The only third-party action currently used is
`actions/checkout`, pinned to a full commit SHA in the workflow.

## Local commands

Each job has the same local entry point:

```bash
bash scripts/ci-lane.sh pin-validation
bash scripts/ci-lane.sh jvm-fake-adapter
bash scripts/ci-lane.sh compose-compilation
bash scripts/ci-lane.sh internal-artifacts
```

| Lane | Local command | What it checks |
| --- | --- | --- |
| Pin validation | `bash scripts/ci-lane.sh pin-validation` | Immutable action pins, Markdown links/whitespace, and JSON Schema fixtures |
| JVM/fake adapter | `bash scripts/ci-lane.sh jvm-fake-adapter` | `core` JVM and fake-adapter tests through the strict Gradle launcher |
| Compose compilation | `bash scripts/ci-lane.sh compose-compilation` | Composite configuration plus `classes` for checked-out `core`, `mods`, and `gui` builds |
| Internal artifacts | `bash scripts/ci-lane.sh internal-artifacts` | `assembleInternal`, then ZIP integrity, non-empty output, and SHA-256 for APK/AAB files |

The current checkout is the cross-repository specification bootstrap, not yet the
Android `app` composition repository. Therefore the three build-dependent lanes
print an explicit **CI-only requirement** and exit successfully until the app
repository supplies `gradlew`, the included builds, and its Android module. This
makes the requirement visible locally without pretending that an APK was built.
Once those files exist, the same commands execute the real checks. The expected
JVM command is:

```bash
scripts/gradle-strict.sh --no-daemon --console=plain -p core test
```

The Compose lane requires the pinned JDK/toolchain and Android build inputs from
the app repository. The artifact lane requires an `app:assembleInternal` Gradle
task and validates every APK/AAB produced below the checkout. No lane publishes,
signs, or uploads an artifact.

## CI-only prerequisites

The build lanes must run in an environment containing the app repository's pinned
JDK 21, Gradle wrapper, Android SDK/Build Tools, NDK, CMake, and recursively
checked-out exact submodule commits. The pin-validation lane additionally needs
Python 3 and the pinned `jsonschema` development dependency used by
`validate-contracts.py`. CI must not inject Stable private signing keys: internal
artifacts are unsigned and are only validation evidence.
