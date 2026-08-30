# ADR 0001: Multi-repository composition

- Status: Accepted
- Date: 2026-08-15

## Context

The core, GUI and addon runtime must be independently replaceable and
versioned, while the Android application still needs a reproducible combination
of their exact sources.

## Decision

Use six public repositories in the `VibeTGram` organization:

`app`, `core`, `mods`, `gui`, `mods-example`, and `addons-market`.

`app` pins `core`, `mods`, and `gui` with HTTPS Git submodules at exact commits
and composes their Gradle builds. The dependency direction is
`core <- mods <- gui <- app`. Examples depend on the public Mod SDK. The market
is signed runtime data, not a build dependency.

Independent SemVer applies to core, mods and GUI interfaces. The CI build BOM
is the authoritative source/toolchain tuple; the offline release BOM binds its
digest to the final signed APK and certificate.

## Consequences

- Cross-repository breaking changes use expand/migrate/contract releases.
- A change is not integrated until an `app` PR tests the exact combination.
- Cloning the application recursively retrieves all build sources.
- Repository autonomy costs additional version and CI coordination.

## Rejected alternatives

- Monorepository: simpler atomic changes but fails the desired replaceability.
- Floating Maven/Git dependencies: not reproducible and hide source changes.
- GitHub Packages as the only source: public Maven access and credential
  requirements complicate clean builds.

## Verification

CI checks submodule commits against the build BOM and runs the full composite
build. Release verification checks the build-BOM digest embedded in the release
BOM and the release-BOM digest embedded in the signed update manifest.
