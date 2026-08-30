# ADR 0008: Release channels and key trust

- Status: Accepted
- Date: 2026-08-15

## Context

The application is distributed outside app stores and can install addons from a
project registry. Compromise of either Android signing or registry trust can
deliver code to users. Test builds must never update a Stable installation.

## Decision

Distribute through GitHub Releases. Stable, Preview and Nightly use different
application IDs, data, FCM projects and signing certificates.

The Stable Android key, separate Stable update-manifest Ed25519 key and registry
Ed25519 root remain offline with two encrypted backups each. CI may use separate
Preview/Nightly keys and a short-lived registry key delegated by the offline
root through a protected release environment. Third-party Actions are pinned by
commit SHA and fork PRs receive no secrets or write token.

CI emits an immutable build BOM for sources/toolchain/unsigned artifact. Offline
signing creates a release BOM binding that build BOM to the signed APK and
certificate. Application updates use a domain-separated signed manifest that
binds channel, application/update-key identity, mandatory expiry, release-BOM
digest, artifact SHA-256 and Android signing fingerprint before invoking the
system installer.

## Consequences

- Stable signing requires a manual release ceremony.
- Losing the Stable key prevents in-place updates; losing the registry root
  prevents trusted delegation.
- Test channels can coexist with Stable and cannot overwrite it.
- Official APK credentials prevent fully independent byte-for-byte reproduction;
  public CI, BOM, provenance and hashes provide auditability.

## Rejected alternatives

- Stable keystore in ordinary GitHub Secrets: larger remote compromise surface.
- One key/application ID for all channels: test compromise reaches Stable.
- Silent/self-privileged installation: unnecessary and unsafe.

## Verification

The release runbook records fingerprints, offline verification and two-person
checks where maintainers are available. Negative tests attempt cross-channel
updates and manifests signed by expired/revoked keys.
