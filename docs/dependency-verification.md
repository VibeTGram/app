# Gradle dependency verification and locks

Status: **Normative internal-build policy**

The app composition root and each included build carry their own Gradle
verification metadata because Gradle treats every included build as an
independent build boundary:

| Build | Verification metadata | Public keyring | Dependency lock |
| --- | --- | --- | --- |
| app | `gradle/verification-metadata.xml` | `gradle/verification-keyring.keys` | no project configurations yet |
| core | `core/gradle/verification-metadata.xml` | `core/gradle/verification-keyring.keys` | `core/gradle.lockfile` |
| mods | `mods/gradle/verification-metadata.xml` | `mods/gradle/verification-keyring.keys` | no configurations yet |
| gui | `gui/gradle/verification-metadata.xml` | `gui/gradle/verification-keyring.keys` | `gui/gradle.lockfile` |

## Verification policy

Every metadata file must contain:

- `verify-metadata=true`, so dependency metadata is itself checked;
- `verify-signatures=true`, so available PGP signatures are checked;
- `keyring-format=armored`, so the checked-in keyring is portable text;
- SHA-256 checksums for every recorded POM, module descriptor and artifact.

The trusted key IDs are scoped in `verification-metadata.xml` to the component
or publisher group they are allowed to authenticate. A key that cannot be
retrieved from an audited source is recorded as an explicit Gradle ignored-key
exception, and its artifact retains a checksum. Such an exception is not a
trust grant: it must be replaced by a reviewed public key before a release
relies on that publisher's signature.

The `verification-keyring.keys` files contain only ASCII-armored public OpenPGP
keys. Private keys, encrypted private-key backups, passphrases and signing
services never belong in this repository. The same public keyring is copied at
each Gradle build root so included builds cannot silently use a different trust
store.

Gradle uses strict verification when metadata exists. The repository also makes
the mode explicit for operators and CI through:

```bash
scripts/gradle-strict.sh --no-daemon --console=plain -p core test
scripts/gradle-strict.sh --no-daemon --console=plain -p gui test
```

The launcher always adds `--dependency-verification=strict`; callers must not
replace it with `lenient` or `off` in CI.

## Dependency locks

`core/build.gradle.kts`, `gui/build.gradle.kts`, and the root build enable
`dependencyLocking { lockAllConfigurations(); lockMode = LockMode.STRICT }`.
The generated `gradle.lockfile` files include resolved transitive dependencies
and configuration names. They are deterministic inputs, not caches, and must be
reviewed together with verification metadata whenever a dependency changes.
The mods build currently applies only Gradle's `base` plugin and has no
resolvable configurations; its lack of a lockfile is intentional and validated.

To update a dependency in a controlled, network-enabled environment with the
pinned JDK and wrapper:

```bash
scripts/gradle-strict.sh -p core dependencies \
  --write-locks --write-verification-metadata sha256,pgp
scripts/gradle-strict.sh -p gui dependencies \
  --write-locks --write-verification-metadata sha256,pgp
```

Review the resulting lockfiles, metadata, key IDs, and artifact origins. Never
use `--refresh-dependencies` as a substitute for reviewing changed checksums.
The validator rejects duplicate coordinates, dynamic versions, malformed
checksums, missing keyrings, private-key markers, and declared dependencies
that are absent from a lockfile.

## Validation

From the repository root:

```bash
python3 scripts/validate-dependencies.py
scripts/validate-build.sh
```

The first command is offline and checks all four XML documents, all four public
keyrings, and both generated lockfiles. The build validation also checks the
pinned toolchain, repository composition, and the wrapper while invoking it in
explicit strict verification mode.
