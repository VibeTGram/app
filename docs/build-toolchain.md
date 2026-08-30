# Reproducible Android build toolchain

The `app` repository keeps build inputs in two synchronized, reviewable files:

- [`toolchain.lock.json`](../toolchain.lock.json) is the machine-readable lock,
  including the Gradle distribution and its SHA-256 checksum.
- [`gradle/libs.versions.toml`](../gradle/libs.versions.toml) is the Gradle
  version-catalog view consumed by future Android modules.

The Gradle wrapper is pinned to **9.4.1** in
[`gradle/wrapper/gradle-wrapper.properties`](../gradle/wrapper/gradle-wrapper.properties).
That file's `distributionSha256Sum` must match the lock. The checked-in
`gradle/wrapper/gradle-wrapper.jar` is also hashed in the lock, so wrapper code
cannot change without a reviewable lock update.

## Pinned inputs

| Input | Pin |
| --- | --- |
| JDK | Eclipse Temurin 21.0.8+9 |
| Gradle | 9.4.1 |
| Android Gradle Plugin | 9.2.0 |
| Kotlin | 2.3.20 |
| Android compile SDK | 36 |
| Android Build Tools | 36.0.0 |
| Android NDK | 26.3.11579264 |
| CMake | 4.1.2 |
| Compose BOM | 2025.03.00 |

The JDK archive URL and Gradle distribution URL are immutable versioned URLs.
Build inputs MUST NOT use `latest`, `SNAPSHOT`, a branch name, or an open-ended
version range. Dependency additions belong in a reviewed change and must update
the lock/BOM evidence.

Gradle dependency verification is described in
[`dependency-verification.md`](dependency-verification.md). Use the strict
launcher (`scripts/gradle-strict.sh`) for every local or CI Gradle invocation.

## Validation

From the repository root:

```bash
scripts/validate-build.sh
```

The script validates the JSON lock, catalog parity, wrapper properties,
wrapper-jar checksum, and then asks the checked-in wrapper to report its
version. It does not install an SDK or accept licenses; CI/build images must
provide the exact SDK packages declared above. Once Android modules land, the
composition task-list check belongs to that module's validation job.
