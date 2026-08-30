# Addon package formats

Status: **Normative bootstrap contract**

VibeTGram recognizes two installable package types:

- `.vibemod`: Luau source plus declarative metadata/assets;
- `.vibetheme`: declarative resources only, never executable code.

Store installation may download a Git source archive at an exact commit instead
of a prebuilt extension. After normalization it MUST satisfy the same logical
layout and schemas as the corresponding package type.

## 1. Common archive rules

Packages are ZIP archives with UTF-8 relative POSIX paths. A verifier MUST reject:

- absolute paths, drive letters, `.`/`..` segments, empty segments or NUL;
- backslashes or paths that change after Unicode NFC normalization;
- duplicate paths, including case-folded duplicates;
- symlinks, hard links, devices, FIFOs and entries other than regular files or
  directories; directory entries carry no payload and are never hashed;
- encrypted ZIP entries;
- nested executable archives intended to bypass validation;
- compression ratio, entry count, total size, path length or depth over host
  limits;
- DEX, JAR, APK, AAB, ELF, PE, Mach-O, `.so`, native object/library, shell or
  arbitrary Luau bytecode files;
- any file whose digest differs from `hashes.json`.

Limits are host policy, not package compatibility. The store may enforce tighter
limits than manual Developer Mode installation.

## 2. Common top-level files

```text
manifest.json          required; package metadata and declared interface
publisher.json         required; untrusted until signature verification; publisher schema
hashes.json            required; hashes schema and SHA-256 for every payload file
signature.ed25519      required for signed packages; signature-envelope schema
icon.*                 optional validated image
locales/               optional translations
assets/                optional validated passive assets
LICENSES/              required when licenses are not fully expressed inline
```

`hashes.json` lists every regular payload file except itself and
`signature.ed25519`. It therefore includes `manifest.json`, `publisher.json`,
source, assets and license files.

All JSON is UTF-8 without BOM and is canonicalized with JSON Canonicalization
Scheme (RFC 8785) for signatures. The supporting contracts are
[`publisher.schema.json`](../../schemas/publisher.schema.json),
[`hashes.schema.json`](../../schemas/hashes.schema.json) and
[`signature-envelope.schema.json`](../../schemas/signature-envelope.schema.json).

## 3. Common identity

- `id` is a lowercase reverse-domain identifier such as
  `org.example.chat_tools`.
- `version` is canonical SemVer without a leading `v` or build metadata.
- `publisher.key_id` is `sha256:<lowercase hex SHA-256 of raw Ed25519 public
  key>`.
- A signed package identity is `(id, verified publisher.key_id)`; the display
  name is never an identity field.
- An automatic update requires the same identity or a valid old-key/new-key
  rotation statement accepted by the registry.

For an unsigned Developer Mode project, every manifest identity field is an
untrusted claim. The host generates a random immutable `development_install_id`
and uses it for the version pointer, priority, storage, grants, journal, cookies
and IPC. An unsigned install cannot replace, shadow, update or inherit data from
a signed package, even when it copies the same manifest ID/key.

## 4. Signature envelope

`publisher.json` contains the raw Ed25519 public key, derived key ID and optional
public publisher metadata. `signature.ed25519` is JSON conforming to the
signature-envelope schema with role `addon-package`, algorithm/key ID,
`signed_sha256` and a base64url-unpadded 64-byte Ed25519 signature. Signature
metadata contains no unauthenticated timestamp; security-relevant times live in
the signed body of the corresponding contract.

The signed bytes are:

```text
UTF8("VIBETGRAM-ADDON-SIGNATURE-V1\n") || JCS(hashes.json) || UTF8("\n")
```

The domain prefix prevents a package signature from being reused as a registry,
update-manifest or key-delegation signature. The verifier first validates
canonical paths and payload hashes, then validates the key ID, then verifies the
signature.

Unsigned packages are retained and executable only while Developer Mode is
active. They are always marked visually and cannot be submitted as verified
store records.

## 5. `.vibemod`

### Layout

```text
manifest.json
publisher.json
hashes.json
signature.ed25519
main.luau
src/**/*.luau
assets/**
locales/*.json
LICENSES/**
```

Only UTF-8 Luau source is executable. The runtime may compile source internally
in memory but MUST NOT accept or persist package bytecode.

### Manifest contract

The machine-readable baseline is
[`schemas/vibemod.schema.json`](../../schemas/vibemod.schema.json). Important
fields are:

- `schema_version`, `id`, `version`, `name`, `description`;
- `publisher.key_id`;
- `entrypoint`, normally `main.luau`;
- semantic Mod API range;
- optional exact raw TDLib commit/schema/generator compatibility;
- requested capabilities and quotas;
- manifest-declared HTTPS domains;
- exact raw functions/update constructors/fields;
- async/sync hooks;
- Mod IPC interfaces and dependencies;
- UI extension slots/routes;
- document collections, indexes and migrations;
- required packages and `load_after` ordering hints;
- bundled resource packs.

Capabilities, domains, raw surfaces, hooks, dependencies and quota requests are
security-relevant. Expanding any of them blocks automatic update until the user
reviews the difference.

Every IPC interface binds a schema digest and the initial fixed data class
`addon-local-nonsensitive`. Telegram-derived values and host-sensitive handles
are rejected regardless of their serialized shape. A v1 IPC package also cannot
declare events, network, Telegram semantic or raw TDLib capabilities; the
manifest schema enforces the safe-capability allowlist.

Every consumed interface includes the provider's `(id, publisher_key_id)` and
must match exactly one hard dependency. The host resolves that reference to an
installed identity and routes only within the current account; addon priority
does not participate in provider selection.

### Dependencies

`requires` is a hard dependency identified by `(id, publisher_key_id)` plus a
normalized `version_range`. The signed form is either `{"exact":"1.2.3"}` or
an interval containing `minimum_inclusive` and/or `maximum_exclusive`; at least
one bound is required. Human-facing tooling may accept convenient range syntax,
but it MUST serialize this normalized object before review and signing. The
store resolves it to a specific reviewed version, commit and tree hash and
displays the complete tree before install. Duplicate dependency identities are
rejected even when their ranges differ. `load_after` is not a dependency and
only suggests initial priority; if two publishers use the same package ID,
`load_after` and ordering UI use the full publisher-bound identity.

Luau library packages do not receive a runtime identity. They execute inside the
consumer's sandbox and capabilities. Different addons may resolve different
versions of the same library.

### Settings and migrations

Global settings are restricted to non-sensitive, non-account values. Every
account-scoped collection is private to `(addon, account)`. Migrations are
versioned, transactional and cannot execute network/UI calls.

## 6. `.vibetheme`

### Layout

```text
manifest.json
publisher.json
hashes.json
signature.ed25519
tokens/*.json
typography/*.json
shapes/*.json
motion/*.json
icons/**
backgrounds/**
locales/*.json
LICENSES/**
```

The machine-readable baseline is
[`schemas/vibetheme.schema.json`](../../schemas/vibetheme.schema.json).

A theme MAY define:

- Material color/token overrides;
- typography references to packaged, licensed fonts;
- shapes and spacing tokens;
- motion tokens within host accessibility limits;
- icons and backgrounds;
- localization resources.

A theme MUST NOT contain Luau, JavaScript, executable expressions, remote asset
URLs, account selectors, network declarations, hooks, storage, permissions or
other active content.

Theme/resource-pack priority is controlled by the user's independent stack.
The top pack applies last. Accessibility corrections always apply after the
stack and cannot be overridden.

## 7. Canonical source-tree hash

Registry source archives are not trusted by their ZIP digest because hosting
providers may regenerate archive metadata. The verifier computes a canonical
tree digest after safe extraction:

1. Require exactly one common, non-empty top-level wrapper directory and at
   least one regular file below it. The ZIP may contain one directory entry for
   the wrapper itself; omit that entry, then strip exactly that wrapper
   component from every descendant. Reject root files, multiple wrappers, an
   empty wrapper or any descendant path that becomes empty.
2. Normalize every remaining relative path as specified above and reject path
   collisions after normalization.
3. Validate but do not hash directory entries. Reject every other non-regular
   entry. Hash every regular file after wrapper stripping; there is no
   VCS/build ignore list and no source file is excluded. The stripped wrapper
   directory itself is not a file and is not hashed.
4. Hash each file content with SHA-256.
5. Sort entries by unsigned UTF-8 path bytes.
6. For each entry append:

   ```text
   UTF8(path) || NUL || ASCII(decimal_size) || NUL || raw_sha256 || LF
   ```

7. Prefix the concatenation with `VIBETGRAM-TREE-V1\n` and SHA-256 the result.

File modification times, ZIP order, compression and host-specific permissions
do not affect the digest. Executable mode is irrelevant because executable
files are forbidden.

Package, Mod API, GUI and dependency versions use SemVer 2.0 precedence without
build metadata. Numeric prerelease identifiers cannot contain leading zeroes.
Comparisons follow SemVer precedence; release `1.2.3` is greater than every
`1.2.3-*` prerelease. Conformance tests MUST include `1.0.0-alpha <
1.0.0-alpha.1 < 1.0.0-beta < 1.0.0`, reject `1.0.0-01`, and reject
`1.0.0+build`.

## 8. Installation transaction

Installation is atomic:

1. Download to private temporary storage with size/time limits.
2. Parse central directory without extracting active content.
3. Validate paths, types and limits.
4. Extract into a fresh private temporary directory.
5. Verify payload hashes and publisher signature; for a store install also
   verify the registry record and chain.
6. Validate schema, licenses, compatibility, source and capabilities.
7. Resolve and verify the dependency tree.
8. Show install/update/permission diff.
9. Atomically switch the installed-version pointer.
10. Start the addon only when Modification Mode and grants allow it.

Failure before step 9 leaves the previous version untouched. Failed first-run
health checks roll back the pointer without deleting diagnostic evidence.

## 9. Manual installation trust states

Modification Mode may install a locally selected, correctly publisher-signed
package that is absent from the registry. It is labeled **Publisher signed,
not registry reviewed**, receives its normal signed identity and never receives
the `verified` badge. It has no store auto-update source. A later manually
selected update must keep publisher continuity and show the same
capability/domain/raw/dependency/quota diff as a store update.
Dependencies of an unlisted package are resolved only from already installed
matching identities or additional packages explicitly selected by the user;
manual install never follows an untrusted dependency URL.

Replacing a store-installed package with a manual package requires the same
signed identity plus an explicit loss-of-registry-trust confirmation. Unchanged
grants may be retained only when the complete reviewed surface is unchanged;
the package becomes manual until a matching signed registry record is accepted.
Known signed revocations still apply.

An unsigned archive/project requires Developer Mode, receives a host-generated
development identity, cannot auto-update and cannot reuse signed-package grants
or data. Leaving Developer Mode freezes it. Signature failure is never treated
as “unsigned”; it is a corrupt or forged signed package and is rejected.

## 10. Key rotation

Normal rotation includes a statement signed by both old and new Ed25519 keys.
The registry preserves the chain. If the old key is lost, manual review may
create a new publisher identity and transfer the listing, but clients MUST NOT
auto-update or silently reuse old grants.
