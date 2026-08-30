# Key management and Stable release runbook

Status: **Normative bootstrap runbook**

Scope: Android application channels, application updates, addon publishers and
the addons-market registry

This document is the operator procedure, not a promise that the release tooling
already exists. Commands prefixed with `vibetgram-keys` define the required CLI
contract and are a release blocker until that CLI is implemented, tested and
independently reviewed. OpenSSL and Android commands below can be used now for
key generation and inspection; they MUST NOT replace canonical JSON validation
or domain-separated signing in the project CLI.

Never paste a password, private key, recovery code, Telegram credential or
Firebase service credential into a terminal argument, issue, chat, CI log or
GitHub release. The examples intentionally rely on interactive password prompts.

## 1. Trust roles

| Key/credential | Algorithm | Normal location | Used for | MUST NOT authorize |
| --- | --- | --- | --- | --- |
| Stable Android signer | RSA 4096 in PKCS#12 | Offline encrypted media | Stable APK signing | Registry, addon or JSON signatures |
| Stable update signer | Ed25519, encrypted PKCS#8 | Offline encrypted media | Stable update manifests | APK, registry or addon signatures |
| Preview Android/update signers | Channel-specific | Protected CI environment | Preview APK/manifests | Stable or Nightly artifacts |
| Nightly Android/update signers | Channel-specific | Protected CI environment | Nightly APK/manifests | Stable or Preview artifacts |
| Registry root | Ed25519, encrypted PKCS#8 | Offline encrypted media | Short-lived registry delegations, including emergency delegations | Index or revocation signing |
| Registry delegated signer | Ed25519 | Protected release environment or signing service | Indexes and routine revocations until expiry | New delegation or Android release |
| Addon publisher | Ed25519, encrypted PKCS#8 | Publisher controlled | One publisher's packages and rotations | Registry review state |
| Telegram application credentials | Telegram-issued | Per-channel build configuration | Telegram client identification | Code or artifact signing |
| FCM configuration | Firebase project per channel | Per-channel build configuration | Push registration/delivery | Code or artifact signing |

The three Android channels use different application IDs, data, Telegram
credentials, Firebase projects, Android signing keys and update signing keys:

```text
Stable:  org.vibetgram.client
Preview: org.vibetgram.client.preview
Nightly: org.vibetgram.client.nightly
```

The Stable Android and update private keys never enter GitHub Actions. Stable is
built unsigned by CI and signed during a manual offline ceremony. Preview and
Nightly are CI-only and never reuse Stable identities.

## 2. People and locations

Every Stable or registry-root ceremony has two people:

- **operator** runs the commands and handles the primary key medium;
- **witness** reads the checklist, compares identifiers and records evidence.

Neither person approves alone. The ceremony uses:

1. a clean offline Linux signing machine with encrypted storage and no radio or
   network connection;
2. a separate online verification/upload machine;
3. one dedicated transfer medium used only for release handoff;
4. one primary encrypted key medium and two encrypted backup media stored in
   separate physical locations;
5. a password manager plus an independent sealed recovery record. Backing up
   only the encrypted files without their passphrases is not recovery.

The signing machine has pinned copies of:

- the same JDK `keytool` major version recorded in the build BOM;
- Android SDK Build Tools containing `zipalign` and `apksigner`;
- OpenSSL 3.x;
- the audited `vibetgram-keys` release tool and its checksum;
- a SHA-256 utility.

Record tool paths and versions before each ceremony. Do not install or update
software during a release ceremony.

## 3. Key inventory record

Maintain a public, non-secret inventory in the `app` repository. Each entry has:

- purpose and channel;
- key ID or Android certificate SHA-256 fingerprint;
- public key/certificate;
- creation ceremony date and evidence digest;
- activation and retirement dates;
- storage-media asset IDs, never passwords or physical addresses;
- successor/predecessor IDs;
- status: `preactive`, `active`, `retiring`, `revoked` or `destroyed`.

Private-key filenames are identifiers only. A filename or Git commit is never
proof that the correct key was used.

## 4. First Stable Android signing-key ceremony

### 4.1 Prepare the offline session

Connect only the primary encrypted key medium. Mount it at an explicit path
chosen by the operator. Do not use `$HOME`, `~`, broad globs or unresolved
environment variables in key commands.

```bash
umask 077
keytool -J-version
apksigner version
openssl version
sha256sum --version
```

The witness checks the output against the pinned tool inventory. Create a
dedicated directory on the encrypted medium using the file manager or a narrowly
scoped command such as:

```bash
mkdir -m 700 /media/vibetgram-primary/stable-android
```

### 4.2 Generate the key

Run interactively so the passphrase never appears in shell history or process
arguments:

```bash
keytool -genkeypair \
  -alias vibetgram-stable \
  -keyalg RSA \
  -keysize 4096 \
  -sigalg SHA256withRSA \
  -validity 10000 \
  -storetype PKCS12 \
  -keystore /media/vibetgram-primary/stable-android/vibetgram-stable.p12
```

Use an organization-controlled distinguished name. Answer the final identity
confirmation explicitly; do not accept accidental placeholder data. Use a new,
unique passphrase generated in the password manager.

### 4.3 Export and record the certificate

```bash
keytool -exportcert \
  -rfc \
  -alias vibetgram-stable \
  -keystore /media/vibetgram-primary/stable-android/vibetgram-stable.p12 \
  -file /media/vibetgram-primary/stable-android/vibetgram-stable-cert.pem

keytool -list -v \
  -alias vibetgram-stable \
  -keystore /media/vibetgram-primary/stable-android/vibetgram-stable.p12

sha256sum \
  /media/vibetgram-primary/stable-android/vibetgram-stable.p12 \
  /media/vibetgram-primary/stable-android/vibetgram-stable-cert.pem
```

The operator and witness independently compare and record the certificate's
SHA-256 fingerprint. The public PEM may be committed later; the PKCS#12 file and
its digest record are never committed.

### 4.4 Create and verify two backups

Copy the key material to each already-encrypted backup medium in separate
sessions. Do not keep all three media connected together. After each copy:

```bash
sha256sum /media/vibetgram-backup-a/stable-android/vibetgram-stable.p12

keytool -list -v \
  -alias vibetgram-stable \
  -keystore /media/vibetgram-backup-a/stable-android/vibetgram-stable.p12
```

Repeat with the explicit path for backup B. The witness compares both file
digests and certificate fingerprints with the primary record. Eject each medium
before connecting the next. Store the primary and backups in separate locations.

An annual restore drill copies one backup to an isolated encrypted scratch
medium, opens it, prints the public certificate and compares its fingerprint. It
does not sign or publish an APK.

## 5. Stable update-manifest key ceremony

The update-manifest key is separate from the Android signer. Generate it on the
offline machine and create two backups under the same two-person controls:

```bash
mkdir -m 700 /media/vibetgram-primary/stable-update

openssl genpkey \
  -algorithm ED25519 \
  -aes-256-cbc \
  -out /media/vibetgram-primary/stable-update/stable-update-private.pem

openssl pkey \
  -in /media/vibetgram-primary/stable-update/stable-update-private.pem \
  -pubout \
  -out /media/vibetgram-primary/stable-update/stable-update-public.pem

openssl pkey \
  -pubin \
  -in /media/vibetgram-primary/stable-update/stable-update-public.pem \
  -text_pub \
  -noout
```

`vibetgram-keys key inspect` MUST derive the project key ID from the raw 32-byte
Ed25519 public key:

```bash
vibetgram-keys key inspect \
  --public-key /media/vibetgram-primary/stable-update/stable-update-public.pem
```

The key ID is `sha256:<lowercase SHA-256 of the raw public key>`. Commit the
public key and inventory record only after the two backups pass the same inspect
and digest checks.

The Stable app embeds this public key. It accepts a manifest only if all are true:

1. JSON Schema and duplicate-key checks pass before semantic use;
2. JCS canonicalization succeeds;
3. the detached Ed25519 signature verifies over
   `VIBETGRAM-APP-UPDATE-V1\n || JCS(manifest) || \n`, and its envelope conforms
   to [`signature-envelope.schema.json`](../../schemas/signature-envelope.schema.json)
   with role `app-update`;
4. channel, exact application ID, pinned `update_key_id`, monotonic
   `version_code`, mandatory expiry and rollback policy pass;
5. downloaded APK SHA-256 and size match the manifest;
6. the APK certificate SHA-256 matches `signing_certificate_sha256` and the
   locally pinned Stable signer;
7. Android package/version identity matches the manifest.

## 6. Registry root and delegated key ceremony

### 6.1 Generate the offline root

```bash
mkdir -m 700 /media/vibetgram-primary/registry-root

openssl genpkey \
  -algorithm ED25519 \
  -aes-256-cbc \
  -out /media/vibetgram-primary/registry-root/registry-root-private.pem

openssl pkey \
  -in /media/vibetgram-primary/registry-root/registry-root-private.pem \
  -pubout \
  -out /media/vibetgram-primary/registry-root/registry-root-public.pem

vibetgram-keys key inspect \
  --public-key /media/vibetgram-primary/registry-root/registry-root-public.pem
```

Back up and inventory the root exactly like the Stable update key. The root
private key remains offline and is not used to sign routine indexes.

### 6.2 Create a short-lived delegated signer

Generate a delegated Ed25519 key in an isolated release environment. Its default
validity is 90 days, and its allowed roles are only `index` and `revocation`.
Transfer only its public key to the offline ceremony:

```bash
vibetgram-keys key generate \
  --algorithm ed25519 \
  --purpose registry-delegated \
  --private-out ./registry-delegated-private.pem \
  --public-out ./registry-delegated-public.pem
```

On the offline machine, verify its displayed raw-key ID, intended validity and
roles, then sign a delegation:

```bash
vibetgram-keys registry delegate \
  --root-private-key /media/vibetgram-primary/registry-root/registry-root-private.pem \
  --delegate-public-key /media/vibetgram-transfer/registry-delegated-public.pem \
  --roles index,revocation \
  --not-before 2026-08-15T00:00:00Z \
  --not-after 2026-11-13T00:00:00Z \
  --out /media/vibetgram-transfer/registry-delegation.json
```

Dates above are examples and MUST be replaced with the ceremony's approved UTC
window. The root signs
`VIBETGRAM-REGISTRY-DELEGATION-V1\n || JCS(delegation body) || \n`.
The output conforms to
[`registry-delegation.schema.json`](../../schemas/registry-delegation.schema.json).
The witness verifies the generated document before it leaves the offline machine:

```bash
vibetgram-keys registry verify-delegation \
  --root-public-key /media/vibetgram-primary/registry-root/registry-root-public.pem \
  --delegation /media/vibetgram-transfer/registry-delegation.json \
  --at 2026-08-15T00:00:00Z
```

Import the delegated private key only into the protected `addons-market`
release environment. Repository pull-request jobs can validate candidate data
but cannot read or invoke the signer. The signing job requires the protected
branch, required reviews and manual environment approval.

### 6.3 Routine registry release

The protected job:

1. checks every record, exact commit, tree hash, publisher signature and review
   threshold;
2. creates a deterministic index with a sequence greater than the last accepted
   sequence and the previous index digest;
3. checks that the delegation is currently valid and permits the requested role;
4. signs `VIBETGRAM-REGISTRY-INDEX-V1\n || JCS(index.body) || \n`;
5. publishes the index, delegation, record set, signature and provenance
   atomically;
6. downloads the published objects and re-verifies them from scratch.

Renew the delegated key before expiry. Overlap old and new delegations briefly
so clients can refresh, but never extend an expired delegation by editing dates.

Emergency revocations are signed over
`VIBETGRAM-REGISTRY-REVOCATION-V1\n || JCS(revocation.body) || \n` and carry a
monotonically increasing sequence. They are never published as undiscoverable
standalone files: the emergency job atomically publishes the valid delegation,
revocation and a new higher-sequence index that references the revocation. This
emergency index is allowed while the routine index job remains frozen.

## 7. Addon publisher keys

A publisher generates an encrypted Ed25519 key locally:

```bash
openssl genpkey \
  -algorithm ED25519 \
  -aes-256-cbc \
  -out publisher-private.pem

openssl pkey \
  -in publisher-private.pem \
  -pubout \
  -out publisher-public.pem

vibetgram-keys key inspect --public-key publisher-public.pem
```

The packager rejects uncommitted or dirty source by default, normalizes paths,
builds `hashes.json`, validates the manifest and signs only:

```text
VIBETGRAM-ADDON-SIGNATURE-V1\n || JCS(hashes.json) || \n
```

Planned command:

```bash
vibetgram-keys addon pack \
  --source ./addon-source \
  --publisher-private-key ./publisher-private.pem \
  --out ./addon.vibemod

vibetgram-keys addon verify \
  --package ./addon.vibemod \
  --strict
```

Normal rotation is one canonical statement signed by both old and new keys over
`VIBETGRAM-PUBLISHER-ROTATION-V1\n || JCS(rotation body) || \n`. A lost old key is
not rotation: it creates a new identity, requires fresh review and never inherits
automatic updates or user grants. The statement conforms to
[`publisher-rotation.schema.json`](../../schemas/publisher-rotation.schema.json).

## 8. CI key configuration

### Stable

Stable CI may access build-time Telegram/FCM configuration through a protected
environment, but it outputs only an aligned **unsigned** APK, build BOM, SBOM,
checksums, test evidence and provenance. It has no Stable Android or update
private key. Fork pull requests receive no release credentials.

All GitHub Actions are pinned to full commit SHA. Release jobs use minimal
permissions, dependency verification, a protected environment and artifact
retention appropriate for audit. Never print Gradle properties, environment
variables or generated service configuration.

### Preview and Nightly

Each channel has independent Android/update keys and independent Firebase and
Telegram application configuration. Store CI secrets only in that channel's
protected environment. A workflow must assert the expected application ID,
signing-certificate fingerprint, update-key ID and Git ref before signing.

Compromise of a CI channel does not authorize Stable. Preview/Nightly test data
must never be promoted into the Stable package or key inventory.

## 9. Stable release ceremony

### 9.1 Freeze and build online

1. Freeze the six exact repository commits in the `app` superproject/build BOM.
2. Pin Telegram Android, TDLib, tgcalls, Luau, toolchains and every dependency.
3. Complete parity, security, license, localization and release gates.
4. Create the protected release tag and let CI build the aligned unsigned APK.
5. CI publishes to a restricted handoff artifact: unsigned APK, build BOM, SBOM,
   checksums, test reports and provenance. It does not create a public release.
6. On the online verification machine, verify the workflow identity, tag,
   provenance, dependency verification result and artifact SHA-256.
7. Copy the immutable handoff bundle to the dedicated transfer medium. Print or
   independently record the SHA-256 values for the offline witness.

The build BOM conforms to
[`build-bom.schema.json`](../../schemas/build-bom.schema.json) and contains the
superproject commit, exactly one entry for each of the six repositories,
upstream pins, Gradle dependency graph, Android tool versions, TDLib schema
hash, expected application/channel identifiers and unsigned APK digest. Its
semantic validator rejects duplicate/missing repository names. It does not
claim the not-yet-created signed APK digest or signing fingerprint.

### 9.2 Verify offline

On the offline machine:

```bash
umask 077
sha256sum -c /media/vibetgram-transfer/handoff/SHA256SUMS
zipalign -c -P 16 -v 4 /media/vibetgram-transfer/handoff/vibetgram-stable-unsigned.apk
```

The witness compares the bundle digest with the independently recorded online
value and checks the build-BOM identities. Stop on any mismatch; do not
regenerate or edit the unsigned artifact offline.

### 9.3 Sign the APK

Write the output to a fresh release directory on the transfer medium. The
command prompts for the keystore password because no password option is supplied:

```bash
apksigner sign \
  --verbose \
  --ks /media/vibetgram-primary/stable-android/vibetgram-stable.p12 \
  --ks-key-alias vibetgram-stable \
  --out /media/vibetgram-transfer/release/vibetgram-stable.apk \
  /media/vibetgram-transfer/handoff/vibetgram-stable-unsigned.apk

apksigner verify \
  --verbose \
  --print-certs \
  /media/vibetgram-transfer/release/vibetgram-stable.apk

sha256sum /media/vibetgram-transfer/release/vibetgram-stable.apk
```

The witness compares the signer certificate SHA-256 with the key inventory. A
successful `apksigner verify` with the wrong certificate is a failed release.

### 9.4 Build the release BOM and sign the update manifest

First bind the immutable build BOM to the signed APK and its verified
certificate in a
[`release-bom.schema.json`](../../schemas/release-bom.schema.json) document:

```bash
vibetgram-keys app release-bom create \
  --build-bom /media/vibetgram-transfer/handoff/build-bom.json \
  --apk /media/vibetgram-transfer/release/vibetgram-stable.apk \
  --out /media/vibetgram-transfer/release/release-bom.json

vibetgram-keys app release-bom verify \
  --release-bom /media/vibetgram-transfer/release/release-bom.json \
  --build-bom /media/vibetgram-transfer/handoff/build-bom.json \
  --apk /media/vibetgram-transfer/release/vibetgram-stable.apk \
  --expected-certificate-sha256 SHA256_FROM_PUBLIC_KEY_INVENTORY \
  --strict
```

Replace the fingerprint placeholder with the public inventory value. Then use
the final predictable GitHub Release URLs and the release-BOM/APK digests. The
manifest schema is
[`update-manifest.schema.json`](../../schemas/update-manifest.schema.json).

```bash
vibetgram-keys app manifest create \
  --channel stable \
  --application-id org.vibetgram.client \
  --update-key-id SHA256_UPDATE_KEY_ID_FROM_PUBLIC_INVENTORY \
  --bom /media/vibetgram-transfer/release/release-bom.json \
  --apk /media/vibetgram-transfer/release/vibetgram-stable.apk \
  --expires-at 2026-09-14T00:00:00Z \
  --artifact-url https://github.com/VibeTGram/app/releases/download/v1.0.0/vibetgram-stable.apk \
  --bom-url https://github.com/VibeTGram/app/releases/download/v1.0.0/release-bom.json \
  --out /media/vibetgram-transfer/release/update-manifest.json

vibetgram-keys app manifest sign \
  --manifest /media/vibetgram-transfer/release/update-manifest.json \
  --private-key /media/vibetgram-primary/stable-update/stable-update-private.pem \
  --out /media/vibetgram-transfer/release/update-manifest.sig.json

vibetgram-keys app manifest verify \
  --manifest /media/vibetgram-transfer/release/update-manifest.json \
  --signature /media/vibetgram-transfer/release/update-manifest.sig.json \
  --public-key /media/vibetgram-primary/stable-update/stable-update-public.pem \
  --apk /media/vibetgram-transfer/release/vibetgram-stable.apk \
  --strict
```

Replace the example version/URLs/key ID/expiry. The tool MUST refuse an URL/version
mismatch, wrong channel/application/key, wrong certificate, non-monotonic
version, invalid build/release BOM or missing/expired manifest.

### 9.5 Publish and independently verify online

1. Eject the primary key medium before reconnecting the transfer medium to an
   online machine.
2. Upload the signed APK, build BOM, release BOM, SBOM, provenance, checksums,
   update manifest and detached signature to a draft GitHub Release for the
   exact protected tag.
3. A second maintainer downloads every draft asset and verifies hashes,
   signatures, certificate, URLs, version and tag using public inputs only.
4. Publish the release only after both maintainers sign the checklist.
5. Download the public assets again and compare them with the signed ceremony
   record. Verify the in-app updater against the public URL.
6. Install/update the exact public APK on the dedicated Android 11 release phone
   with SELinux Enforcing, root unavailable to the app, microG active and normal
   Doze. Run the Stable release checklist. Run the separate GMS emulator and
   foreground-service fallback checks.

Release evidence may contain public hashes, fingerprints and test results. It
must not contain passwords, private keys, account/session databases, auth keys,
phone numbers, notification payloads or unredacted logs.

## 10. Rotation

### Android signer

Android signer rotation is constrained by platform update rules and configured
APK signature schemes. It requires its own ADR, supported-device verification
and an old-signer-authorized migration path. Never replace the certificate field
in a manifest and assume existing installations will accept the APK.

Generate a successor well before it is needed, keep it `preactive`, and test the
chosen lineage mechanism on supported Android versions. The old key remains
recoverable for the entire supported migration window.

### Stable update signer

Ship the new public key in a Stable APK signed by the existing Android signer
before making it authoritative. During a bounded overlap the client may accept
old or new manifest keys from an explicitly versioned key set. Publish the first
new-key manifest only after the APK containing the new trust anchor is widely
available. Remove the old public key in a later signed release.

### Registry root

The registry format does not define root cross-signing. Root replacement uses
only the independent Android/update trust chain: freeze catalog publication,
ship a Stable APK that contains the new root, wait for the approved adoption
threshold, and only then let the new root issue delegations. Clients that have
not installed that APK continue to trust only their embedded old root and do
not receive new catalog state.

### Delegated registry signer

Issue a new 90-day delegation before the old one expires. Publish both valid
delegations during overlap, then remove the expired public material from active
metadata while retaining it in transparency history.

## 11. Compromise and loss playbooks

| Incident | Immediate action | Recovery boundary |
| --- | --- | --- |
| Stable Android private key suspected compromised | Stop all Stable releases, preserve evidence, publish notice through independent channels | Do not sign again until platform-compatible rotation/replacement plan is reviewed; a new unrelated certificate cannot update existing installs normally |
| Stable update key compromised, Android key safe | Stop updater publication and revoke URL/CDN access where possible | Release an Android-signed app with a new update trust anchor through manual GitHub installation; do not trust a new manifest key automatically |
| Registry delegated key compromised | Freeze index job; offline root signs a fresh short-lived emergency delegation with `index,revocation` roles; its new delegated key signs a revocation for the compromised key and then a higher-sequence index | The root signs only delegations, never revocations; clients retain the last valid index until the new delegation, revocation and index all validate |
| Registry root compromised | Freeze store and all delegations | Ship a Stable app with a new root using the independent app trust chain; old clients require an explicit recovery path |
| Publisher key lost | Mark listing unavailable for normal updates | New key means new identity, new review and new grants |
| Publisher key compromised | Registry issues signed revocation for key/package commits | New identity and review; preserve local addon data but block normal execution |
| Preview/Nightly CI key compromised | Stop affected channel workflow and revoke CI secrets | Rotate/reinstall that isolated channel; Stable keys and data remain unaffected |
| Passphrase lost but private key intact | Stop use and consult sealed recovery record | Encrypted backups without a recoverable passphrase do not help; never weaken protection by guessing in production |
| Transfer medium lost | Treat unsigned public artifacts as non-secret; assess signed unpublished artifacts | No private key is ever placed on transfer media; replace medium and redo handoff if integrity is uncertain |

Every incident gets a public, sanitized identifier and a private evidence log.
Do not collect user telemetry or upload client logs automatically.

## 12. Ceremony checklist

Both operator and witness initial every item.

### Before

- [ ] Exact release tag and six repository commits are protected.
- [ ] Parity and security gates are terminal with no waived critical/high issue.
- [ ] CI provenance and handoff SHA-256 are independently recorded.
- [ ] Offline machine/tool checksums match the inventory.
- [ ] Primary key medium is the only key medium connected.
- [ ] Transfer medium contains no private key or password file.

### Sign

- [ ] Handoff checksum and alignment pass offline.
- [ ] BOM channel, app ID, upstream pins and TDLib schema hash are correct.
- [ ] APK signer certificate fingerprint equals the Stable inventory value.
- [ ] Signed APK verification and SHA-256 pass.
- [ ] Manifest Schema/JCS/domain/signature checks pass.
- [ ] Manifest APK hash, size, certificate, version and URLs match exactly.

### Publish

- [ ] Key medium was ejected before any online transfer.
- [ ] Draft assets were downloaded and independently verified.
- [ ] Public assets were re-downloaded after publication and verified.
- [ ] In-app update works from the public manifest.
- [ ] Android 11 microG physical-device, GMS emulator and fallback checks pass.
- [ ] Release notes state that VibeTGram is unofficial and explain Modification
      Mode/Telegram Terms risk.
- [ ] Sanitized ceremony evidence is archived; secrets are not present.

## 13. Required release-tool tests

`vibetgram-keys` cannot be declared release-ready until tests cover:

- duplicate JSON keys, invalid UTF-8, non-JCS values and alternate encodings;
- wrong domain prefix, signature/key length and key ID;
- manifest rollback, expiry, channel/application mismatch and version overflow;
- APK hash/size/certificate/package/version mismatch;
- delegation role abuse, not-before/not-after boundaries and root mismatch;
- registry sequence rollback and previous-digest fork;
- package ZIP traversal, duplicate/case-fold paths and hash ambiguity;
- publisher dual-signature rotation and lost-key non-rotation;
- deterministic output across supported host systems;
- redaction: no passphrase/private key in stdout, stderr or exception text.

The signer and verifier use different code paths where practical, and release
verification is exercised against published artifacts, not only local fixtures.
