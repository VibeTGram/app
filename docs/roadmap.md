# VibeTGram implementation roadmap

Status: **Accepted direction; no calendar estimates yet**

The roadmap uses parallel tracks. It does not define a reduced public MVP: the
first Stable still requires the complete parity gate in
[`feature-parity.md`](feature-parity.md). Internal vertical slices exist to
validate architecture, not to narrow the final scope.

## 1. Parallel tracks

| Track | Owner repository | Outcome |
| --- | --- | --- |
| A. Telegram engine | `core` | TDLib lifecycle, semantic/raw interfaces, persistence, calls and Android adapters |
| B. Presentation | `gui` | Replaceable Material 3 Expressive GUI and full parity screens |
| C. Modification runtime | `mods` | Luau host, permissions, hooks, storage, UI schema and SDK |
| D. Supply chain | `addons-market`, `mods`, `app` | Packages, review, registry, updater, signing and revocation |
| E. Integration/release | `app` | Composite build, BOM, channels, FCM, CI and release gates |
| F. Examples/docs | `mods-example`, `mods` | Executable tutorials, Wiki source and developer tooling |

## 2. Milestone 0 — specification bootstrap

Deliverables:

- architecture and ADRs;
- primary-source evidence;
- capability matrix;
- package/registry schemas;
- parity matrix;
- signing/release runbook;
- exact upstream discovery pins.

Exit criteria:

- internal links and JSON parse/Schema checks pass;
- every accepted product decision has an owning document;
- unresolved assumptions are labeled rather than presented as facts.

## 3. Milestone 1 — first vertical engineering slice

This milestone is internal and not a public Stable scope.

### Repository bootstrap

- Create the six repositories and license files.
- Protect `main`; require pull requests and CI.
- Add HTTPS submodules to `app` at exact commits.
- Pin JDK, Kotlin, AGP, Gradle, Android SDK, NDK, CMake and dependencies.
- Enable Gradle dependency verification and deterministic BOM output.

### Engine slice

- Build pinned TDLib for `arm64-v8a` and `x86_64`.
- Generate Kotlin TDLib types and a normalized `schemaHash`.
- Implement one encrypted account context and authorization state machine.
- Fetch chat list/history and send/receive text through a semantic interface.
- Provide fake and TDLib adapters for the same contract tests.

### GUI slice

- Bootstrap Compose and the Material 3 Expressive design module.
- Render account authorization, chat list and text conversation.
- Establish typed routes, screen state holders and adaptive layout primitives.
- Verify TalkBack, large text and screenshot baselines.

### Mod slice

- Embed pinned Luau interpreter without JIT.
- Create one per-account state with allocator and interrupt limits.
- Expose safe logging/settings/UI nodes only.
- Load a signed Hello World source addon from `mods-example`.
- Prove Modification Mode starts/stops all runtime work.

### Integration slice

- Build all modules through the `app` composite build.
- Run unit, fake-adapter, Compose and archive-validation tests.
- Produce an unsigned internal APK and build BOM.

Exit criteria: one test account can authorize, list chats, open a chat, exchange
text, and render a safe Hello World addon without crossing module interfaces.

## 4. Milestone 2 — security and lifecycle foundation

### Core

- Multi-account `AccountManager` with opaque handles.
- Keystore-wrapped per-account keys and transactional app storage.
- Complete update reducer, process-death recovery and logout deletion.
- FCM registration with encryption, microG compatibility and foreground-service
  fallback.
- TDLib file handles, download/upload lifecycle and SAF/MediaStore adapters.

### Mods

- Manifest/parser/signature pipeline and schemas.
- Complete capability decision engine and prompt descriptors.
- Generated raw proxy/patch layer plus exhaustive policy overlay.
- Effective raw permission is the maximum of capability, function, fields and
  context; Critical remains per-operation.
- Sync deadlines, async queues, cancellation, quotas and safe mode.
- Encrypted storage and reliable journal.
- Declarative Mod UI Contract and accessibility validator.
- Host-generated identities for unsigned projects and non-sensitive-only Mod
  IPC enforcement.

### Supply chain

- Publisher key tooling and schema-valid dual-signed rotation statements.
- Registry index/delegation/revocation validator, review evidence and delegated
  signing prototype.
- Signed revocation and cached offline behavior.
- Safe-update diff and pending permission expansion.
- GitHub-only source acquisition in a credential-free network-isolated job.

Exit criteria: adversarial tests cannot cross account/addon seams or execute
forbidden files/interfaces; every pinned TDLib surface is classified.

## 5. Milestone 3 — feature-parity implementation waves

Waves run across core and GUI together. A wave closes rows only with tests.

1. Authorization, accounts, chat list, folders, search and settings shell.
2. Message history, composer, text/entities, replies, forwards, edits and delete.
3. Media/file lifecycle, galleries, players, voice/video notes and downloads.
4. Groups, channels, topics, administration, statistics and sponsored messages.
5. Contacts, profiles, privacy/security, sessions, proxies and secret chats.
6. Stories, gifts, Stars, Premium, paid content and business features.
7. Bots, inline mode, Mini Apps, WebView profiles, payments and Passport.
8. tgcalls private/group/video calls, screen sharing and live streams.
9. Adaptive form factors, localization, accessibility and performance closure.

Each wave also exposes approved semantic functions, raw classifications and
declarative addon extension slots. Mod work does not wait until GUI parity is
finished.

## 6. Milestone 4 — addon ecosystem completion

- Full semantic Mod API reference and generated Luau types.
- Raw TDLib reference tied to schema commit/hash.
- Mod IPC and isolated dependency resolver.
- Resource-pack stack and conflict inspector.
- Store browser, install/update/revoke flows.
- Developer Mode, SAF project hot reload and last-working-version recovery.
- Example addons for safe UI, message observation, storage, HTTPS, raw update
  patching, dependency IPC and a resource pack.
- ToS-sensitive example only after warning/review language is finalized.
- Versioned `mods/docs` publication to GitHub Wiki.

Exit criteria: examples pass CI against the public SDK and store verifier; an
addon cannot obtain a capability that its manifest/update diff did not expose.

## 7. Milestone 5 — hardening and Preview

- Complete archive/parser/bridge fuzz corpora.
- Long-running multi-account/addon stress and memory-pressure tests.
- Call/media/network interruption and process-death tests.
- WebView account-switch/process-death tests for profile and data-directory
  suffix isolation.
- Migration and recovery-mode fault injection.
- Security review of package, registry, updater and WebView bridges.
- Dependency/SBOM/license/source-origin audit.
- English/Russian string and privacy-copy review.
- CI-only Preview releases with isolated ID/key/data.

Preview exit criteria:

- no open critical/high security findings;
- no unclassified TDLib surface;
- no data-loss issue in migration/recovery tests;
- parity matrix contains only terminal states or documented defects blocking
  Stable.

## 8. Milestone 6 — Stable release gate

Stable requires:

- parity matrix fully terminal (`DONE`, approved `BLOCKED_UPSTREAM`, or approved
  `NOT_APPLICABLE`);
- all protected branches/commits plus CI build BOM and offline release BOM
  finalized;
- Stable Telegram API and FCM credentials injected through release process;
- signed update manifest and artifact hashes verified independently;
- physical microG device checklist complete with SELinux Enforcing and no app
  root privileges;
- GMS emulator and foreground-service fallback checks complete;
- sanitized issue-report flow verified;
- offline Android/registry root key ceremony complete;
- release notes prominently identify the client as unofficial and explain
  Modification Mode risk.

## 9. After first Stable

- Start the next pinned Telegram Android/TDLib parity cycle.
- Revisit F-Droid only if a fully FOSS, non-Firebase flavor becomes a product
  goal.
- Evaluate separate-process Luau only from measured crash/security evidence.
- Add cross-account Mod API only through a dedicated ADR and permission model.
- Consider opt-in user benefits/telemetry only through a separate explicit
  proposal; the current invariant remains no telemetry.

## 10. Work-item definition of done

Every implementation issue states:

- owning module and interface;
- parity/capability/schema rows changed;
- security and account scope;
- expected errors and cancellation;
- tests at the module interface;
- documentation and migration impact;
- exact verification command/evidence.

Adjacent refactors or speculative abstractions are not part of a work item
unless required by its interface and verified outcome.
