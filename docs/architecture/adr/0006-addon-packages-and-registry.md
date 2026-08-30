# ADR 0006: Source-only packages and signed commit registry

- Status: Accepted
- Date: 2026-08-15

## Context

Users need automatic installation from organization repositories without
trusting movable branches or opaque binaries. Publishers need continuity, while
the organization needs review and emergency revocation without deleting user
data.

## Decision

Use `.vibemod` for source-only Luau addons and `.vibetheme` for declarative
resource packs. Reject native libraries, DEX/JAR, executables, symlinks,
traversal, duplicate normalized paths, decompression bombs and arbitrary Luau
bytecode.

The `addons-market` repository records an exact GitHub source repository and
reviewed commit plus canonical source-tree hash, publisher key, manifest hash,
capabilities, publisher-bound exact dependencies, review evidence and issue
tracker. Credential-free isolated CI verifies the source and signs byte-exact
schema-valid index/delegation/revocation documents using a delegated Ed25519 key
rooted in an offline key.

Updates are automatic only with unchanged publisher identity and no capability,
domain, hook, dependency or quota expansion. Signed revocation blocks normal
execution but never deletes source or data.

A manually installed publisher-signed but unlisted package is labeled
unreviewed and has no store auto-update. An unsigned Developer Mode package
receives a random host identity and cannot collide with or inherit a signed
package's data/grants. Initial Mod IPC forbids Telegram-derived and host-sensitive
payloads, so dependencies cannot lend each other capabilities.

## Consequences

- Users download source archives automatically but execute only the verified
  normalized tree.
- Store availability depends on Git hosting and a valid signed cached index.
- Two independent human reviews are required for `verified`; dangerous addons
  receive security review.
- Publisher key loss cannot be silently repaired.

## Rejected alternatives

- Prebuilt addon binaries: unauditable and incompatible with the sandbox goal.
- Branch/tag references: movable and not reproducible.
- Organization-only signatures without publisher continuity: enables silent
  takeover of an addon identity.

## Verification

Parser corpus tests cover malformed archives and canonicalization ambiguity.
Registry CI rebuilds hashes from the exact commit and verifies both publisher
and delegated registry signatures. Conformance tests share canonical fixtures
across client, registry and CLI while negative tests cover identity collision,
dependency confusion, IPC laundering and registry source SSRF.
