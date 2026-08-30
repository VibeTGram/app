# Addons market registry

Status: **Normative bootstrap contract**

Repository: `VibeTGram/addons-market`

## 1. Purpose

The registry is a signed catalog of exact reviewed source commits. It is not a
binary repository and does not claim that listed ToS-sensitive behavior complies
with Telegram rules.

An application installation has an offline registry root key. It accepts an
index only when the chain is:

```text
offline root -> time-bounded delegated registry key -> index -> record digest
```

Publisher signatures are an independent chain:

```text
publisher key -> package source hashes
```

Both chains must validate for a normal verified install.

## 2. Repository layout

```text
index.json
delegations/
  registry-<64-lowercase-key-id-hex>.json
records/
  <addon-id>/
    <version>.json
revocations/
  <sequence>.json
provenance/
  <sequence>.json
schemas/
review-evidence/
tools/
```

`index.json`, delegations and revocations contain a `body` plus a fixed-format
signature object; signatures are not layout alternatives. Package and app
update signatures use the same detached envelope fields. All JSON rejects
duplicate keys before Schema/JCS processing.

The record schema is
[`schemas/addon-registry-record.schema.json`](../../schemas/addon-registry-record.schema.json).
Other byte-level contracts are:

- [`registry-index.schema.json`](../../schemas/registry-index.schema.json);
- [`registry-delegation.schema.json`](../../schemas/registry-delegation.schema.json);
- [`registry-revocation.schema.json`](../../schemas/registry-revocation.schema.json);
- [`registry-provenance.schema.json`](../../schemas/registry-provenance.schema.json);
- [`publisher-rotation.schema.json`](../../schemas/publisher-rotation.schema.json);
- [`signature-envelope.schema.json`](../../schemas/signature-envelope.schema.json).

## 3. Signature bytes and encoding

Keys are raw 32-byte Ed25519 public keys encoded as unpadded base64url. Key IDs
are lowercase `sha256:` plus SHA-256 of those raw bytes. Signatures are exactly
64 bytes encoded as 86 unpadded base64url characters. A signature object records
the SHA-256 of the exact signed byte string; the verifier recomputes it before
Ed25519 verification and checks that the signature key matches its role/body.

The only signed byte strings are:

```text
VIBETGRAM-REGISTRY-DELEGATION-V1\n || JCS(delegation.body) || \n
VIBETGRAM-REGISTRY-INDEX-V1\n      || JCS(index.body)      || \n
VIBETGRAM-REGISTRY-REVOCATION-V1\n || JCS(revocation.body) || \n
VIBETGRAM-PUBLISHER-ROTATION-V1\n  || JCS(rotation.body)  || \n
```

No surrounding document, whitespace, signature object or transport metadata is
included. Root signs delegation; an unexpired delegated key with the exact role
signs index/revocation; old and new publisher keys both sign the same rotation
body. Signature envelopes deliberately contain no unauthenticated timestamp;
all decision-relevant times are fields of the signed body. Timestamps are UTC
RFC 3339 `date-time`; semantic validation requires
`not_before < not_after`, `generated_at < expires_at`, monotonic sequences,
sorted unique digest entries and exact ID/public-key matches. Record
`published_at` must precede `expires_at`; an expired record cannot authorize a
new install or update, though expiry alone does not delete local data.

Every index `sha256` for a delegation, record, revocation or provenance path is
SHA-256 of `JCS(complete validated JSON document)` at that path. Provenance
`review_inputs` instead hash the exact Git blob bytes at `source.commit`, because
those inputs may be non-JSON. Neither digest depends on transport whitespace or
a mutable working tree.

## 4. Record contents

Every record includes:

- addon identity, type and version;
- source repository HTTPS URL;
- full reviewed commit ID;
- canonical source-tree SHA-256;
- manifest and publisher key IDs;
- normalized semantic/GUI ranges and exact raw compatibility;
- reviewed capabilities, domains, raw methods/hooks and quota requests;
- reviewed IPC interface names, versions, schema hashes and fixed data class;
- dependencies resolved or constrained to reviewed identities;
- license expressions;
- issue tracker;
- review state and evidence;
- `ToS-sensitive` and other warning labels;
- publication/expiry timestamps and registry sequence.

No branch, tag or “latest” URL is a trust input.

Dependencies in `reviewed_surface` are exact tuples of package ID, publisher
key, resolved version, reviewed commit and tree hash. Manifest ranges are
normalized exact-or-half-open objects before signing. A semantic validator
rejects duplicate `(id, publisher key)` identities even if JSON `uniqueItems`
would see different objects.

## 5. Review states

| State | Meaning |
| --- | --- |
| `community` | Discoverable source; not independently reviewed |
| `unverified` | Automated checks may pass; human threshold not met |
| `verified` | Exact commit passed CI and required independent reviews |
| `revoked` | Execution blocked in normal mode by signed revocation |

`verified` requires two independent human approvals, and an author cannot
approve their own addon. Network, raw TDLib, sensitive/critical, ToS-sensitive,
message-preservation and combined message-read/network access require a security
review. Until enough maintainers exist, project-owned addons remain unverified.

## 6. Registry CI

For each submitted record, CI MUST:

1. Accept only canonical `https://github.com/<owner>/<repository>` source URLs.
   Construct GitHub API/codeload requests internally; never fetch a record's
   arbitrary URL or follow a redirect to another host.
2. Verify commit existence and immutability assumptions.
3. Safely materialize and canonicalize the source tree.
4. Compare tree, manifest and file hashes.
5. Verify publisher signature and key continuity.
6. Reject forbidden files and archive ambiguity.
7. Validate JSON schemas and SPDX license expressions.
8. Parse/analyze all Luau source and run package tests.
9. Compare declared capabilities/domains/raw methods/hooks to static usage.
10. Resolve the exact publisher-bound dependency graph, reject duplicate
    identities/cycles and inspect every declared IPC edge. Initial IPC rejects
    Telegram-derived or host-sensitive payloads rather than transferring a
    capability between principals.
11. Verify Mod API, raw TDLib and GUI compatibility.
12. Validate review evidence and required approvers.
13. Produce a deterministic record/index diff.
14. Sign only through the protected delegated-key release job.

Static analysis is not a proof of safety. Human review remains mandatory for
`verified`. Source acquisition runs in an unprivileged network-isolated job with
no signing/organization credentials, metadata-service access or private-network
route. It rejects IP literals, userinfo, private/link-local DNS answers and any
redirect outside the approved GitHub/codeload hosts.

## 7. Client refresh and offline behavior

- The application caches the latest valid index, delegation and revocation
  sequence in encrypted private storage.
- The client polls only the authenticated `index.json` head. Every emergency
  revocation is discoverable through a newly signed higher-sequence emergency
  index; standalone unreferenced revocation files have no effect.
- A newer invalid, expired or rollback index is rejected without deleting the
  last valid copy.
- Previously known revocations remain effective offline.
- Lack of network does not revoke an already installed non-revoked package.
- The catalog may refresh and eligible addon updates may install while
  Modification Mode is off, but no addon code executes.
- Capability-expanding updates may download and verify, then remain pending
  until Modification Mode is enabled and the user reviews them.

## 8. Installation and update policy

The client downloads the hosting provider's source ZIP automatically from the
exact commit. Users do not manually retrieve store ZIP files. ZIP transport
hashes may be logged for diagnostics, but trust is based on the canonical source
tree hash and signatures.

An update is silent/automatic only when all are unchanged or reduced:

- package and publisher identity;
- declared capability set and permission classes;
- network domain allowlist;
- raw TDLib functions, updates and fields;
- sync/async hooks;
- dependencies and public IPC consumption;
- requested memory, CPU, storage, journal, network and UI quotas;
- ToS-sensitive labels;
- minimum semantic/raw/GUI compatibility requirements.

Any expansion creates a pending update with a user-visible old/new diff. The
old version remains installed until approval.

## 9. Dependency handling

Before installation, the client displays the full tree, resolved exact versions,
commits/tree hashes, publisher identities, permissions and domains. Each
executable addon receives its own grants. Libraries run with the consuming
addon's identity and cannot add capabilities. Dependency declarations include
publisher key IDs; package ID alone never selects a resolver candidate.

The registry rejects:

- dependency cycles;
- a verified record depending on unreviewed executable source;
- ranges that cannot resolve deterministically;
- conflicting singletons where isolation is impossible;
- undeclared IPC interfaces or incompatible interface versions.

## 10. Revocation

A signed revocation identifies one or more of:

- package identity;
- exact source commit/tree hash;
- publisher key;
- delegated registry key;
- registry record sequence.

It contains reason category, human-readable summary, evidence reference,
effective time and replacement guidance. In normal mode the client immediately
stops affected addon instances and prevents restart. Source, settings and data
remain. Developer Mode may allow an explicit local override after its own
warning; the UI continues to show the revoked state.

## 11. Key loss and takeover resistance

Publisher rotation requires old/new signatures. Lost-key recovery creates a new
identity and requires fresh trust; it never inherits auto-update or grants.

The registry organization cannot replace publisher source invisibly because the
client verifies both publisher and registry chains. The organization can revoke
or delist a package but cannot remotely delete its local data.

## 12. ToS-sensitive listings

Such records:

- carry a machine-readable `tos-sensitive` warning;
- are hidden until Modification Mode is enabled;
- enumerate concrete ToS-sensitive capabilities;
- repeat that `verified` is a source/security claim only;
- require additional confirmation during install and permission grant.

## 13. Transparency

Every signed index publishes its sequence, previous index digest, deterministic
record/revocation digest sets, delegation key ID and the digest of exactly one
`provenance/<sequence>.json` document. That provenance document binds the
protected workflow run, source commit and review inputs used to produce the
index; it is content-addressed by the signed index but is not itself an
additional trust root. It conforms to `registry-provenance.schema.json`; its
sequence equals the index sequence, and its input digest set must cover every
record, revocation and delegation input referenced by that index. Clients reject a lower sequence unless the user
explicitly clears registry state in Developer Mode.
