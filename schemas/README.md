# VibeTGram machine-readable contracts

All schemas use JSON Schema Draft 2020-12. A validator MUST enable `format`
checking and MUST parse UTF-8 JSON with duplicate-key rejection before Schema
validation. Cryptographically signed JSON is canonicalized with RFC 8785 JCS;
transport whitespace and object-key order are not trusted.

## Contracts

| Schema | Object |
| --- | --- |
| [`vibemod.schema.json`](vibemod.schema.json) | Luau addon manifest |
| [`vibetheme.schema.json`](vibetheme.schema.json) | Declarative resource-pack manifest |
| [`publisher.schema.json`](publisher.schema.json) | Addon publisher public identity |
| [`hashes.schema.json`](hashes.schema.json) | Package payload hashes/sizes |
| [`signature-envelope.schema.json`](signature-envelope.schema.json) | Detached Ed25519 signature fields |
| [`publisher-rotation.schema.json`](publisher-rotation.schema.json) | Dual-signed publisher rotation |
| [`addon-registry-record.schema.json`](addon-registry-record.schema.json) | Exact reviewed addon commit/surface |
| [`registry-delegation.schema.json`](registry-delegation.schema.json) | Offline-root delegation |
| [`registry-index.schema.json`](registry-index.schema.json) | Delegated signed index |
| [`registry-revocation.schema.json`](registry-revocation.schema.json) | Delegated signed revocation |
| [`registry-provenance.schema.json`](registry-provenance.schema.json) | Registry workflow/source/review provenance |
| [`build-bom.schema.json`](build-bom.schema.json) | Immutable CI/source/toolchain BOM |
| [`release-bom.schema.json`](release-bom.schema.json) | Offline signed-APK binding |
| [`update-manifest.schema.json`](update-manifest.schema.json) | Channel update descriptor |

## Semantic validation after Schema

JSON Schema is not the complete security policy. The shared verifier also MUST:

- recompute public-key IDs from raw Ed25519 bytes;
- recompute every hash, file size, signed-byte digest and signature;
- enforce the domain-separated signed bytes in the package/store/runbook docs;
- require timestamp ordering, record/index expiry and monotonic sequence/version
  rules, with all signed sequence integers bounded to 32-bit positive values;
- enforce channel/application/update-key and APK certificate/package binding;
- require exactly one build-BOM entry for every repository name and its exact
  canonical VibeTGram URL; bind each upstream name to its canonical URL;
- reject duplicate dependencies by `(package_id, publisher_key_id)` and bind
  their exact version/commit/tree hash;
- require every consumed IPC provider to match exactly one hard dependency and
  route it by resolved install identity plus account;
- compare normalized SemVer ranges, require `minimum_inclusive <
  maximum_exclusive` when both exist, and reject build metadata;
- bind every signed registry index to exactly one same-sequence provenance
  document, reject mismatched provenance sequences, and require its review input
  digests to cover the referenced record, revocation and delegation inputs;
- enforce manifest capability/raw-field conditionals plus the exhaustive Policy
  Engine overlay;
- reject source URL redirects/hosts and archive paths outside host policy;
- compare identity, capability, domain, IPC, raw, quota and dependency diffs on
  update.

The client, registry CI and release CLI use the same conformance fixtures but
separate signer/verifier code paths where practical. Unknown schema versions
fail closed; migrations produce a new canonical document rather than changing
the interpretation of an already signed one.
