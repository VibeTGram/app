# Mod capability matrix

Status: **Normative bootstrap contract**

Owner after split: `VibeTGram/mods`, with TDLib policy overlay in
`VibeTGram/core`

## 1. Security principal

For a signed package the security principal is `(package_id,
verified_publisher_key_id, package_version, account_handle, lua_state_id)`. For
an unsigned Developer Mode project, the publisher fields in its manifest are
untrusted display metadata and the principal instead contains a random,
host-generated immutable `development_install_id`. The Mod Host constructs the
principal and binds it to the Lua state. Lua code cannot provide or replace any
identity field.

Unsigned installs have separate version pointers, priority entries, data,
grants, journals, cookies and IPC identities. They cannot replace, update,
shadow or inherit anything from a signed identity, even if their manifest
copies its ID and publisher key. Removing and reimporting a local project creates
a new development identity unless the host performs an explicit reconnect to
the same retained project record.

Every bridge call is evaluated in this order:

1. Modification Mode and package execution state.
2. Package signature/trust and compatibility.
3. Manifest declaration for capability, raw method, domain, hook and resource.
4. Account scope bound to the Lua state.
5. Existing user grant and its lifetime.
6. Argument, object, URL and destination validation.
7. Rate, CPU, memory, storage and event-journal quotas.
8. Operation execution through the owning module.
9. Redacted local audit event and quota accounting.

Any failed step denies the operation. Unknown capabilities, TDLib functions,
constructors, fields and enum values fail closed.

## 2. Permission classes

| Class | Default UX | Allowed grant lifetime | Sync hook behavior |
| --- | --- | --- | --- |
| Safe | Auto-allowed when declared | Package installation | Allowed within hook budget |
| Sensitive read | Prompt with data/account scope | Once, while mode enabled, persistent per account | Must already be granted |
| Sensitive write | Prompt with action/account scope | Once, while mode enabled, persistent per account | Must already be granted |
| Critical | Confirm concrete operation and target | One operation only | Denied; cannot prompt |
| ToS-sensitive | Global mode warning plus per-addon prompt | Same as underlying class | Must already be granted; critical remains one-shot |
| Forbidden | Never exposed | None | Denied |

Persistent means “until revoked for this package identity and account.” A
publisher-key change, development-install identity change, capability expansion
or account logout invalidates the grant. Signed package updates with the same
publisher and unchanged surface retain it.

The permission UI MUST highlight dangerous combinations, especially Telegram
message content plus outbound network access or reliable preservation.

## 3. Host capabilities

| Capability | Class | Interface and invariants |
| --- | --- | --- |
| `ui.extend` | Safe | Contribute validated nodes to declared extension slots |
| `ui.screen` | Safe | Register addon-owned declarative routes within node/depth limits |
| `ui.navigate` | Safe | Navigate only to typed public routes; no arbitrary intents/URIs |
| `ui.prompt` | Safe | Request host-rendered dialog/sheet/snackbar; rate limited |
| `storage.kv` | Safe | Private encrypted namespaced key-value storage under quota |
| `storage.documents` | Safe | Declared collections/indexes and migrations; no SQL |
| `events.best_effort` | Safe | Bounded FIFO subscription; state events may coalesce |
| `events.reliable` | Sensitive read | Encrypted at-least-once snapshots with visible quota/TTL |
| `network.https` | Sensitive write | HTTPS only to manifest domains; no credentials by default |
| `ipc.provide` | Safe | Publish a typed, versioned interface carrying non-Telegram addon data only |
| `ipc.consume` | Safe | Call a declared dependency interface under the same payload restriction and size/rate limits |
| `settings.global_safe` | Safe | Non-account, non-sensitive package settings only |
| `diagnostics.own` | Safe | Read sanitized errors/metrics for the current addon instance |

`network.https` denies IP literals, localhost, loopback, link-local and private
LAN ranges. DNS, redirects and every final host are checked against declared
domains. Raw sockets, WebSocket, custom TLS, custom DNS and certificate bypass
are not available. Cookies are private to `(addon, account, domain)` and no
Telegram/FCM/Mini App credentials are inserted automatically.

### IPC data boundary

Initial Mod IPC may carry only schema-validated addon-local values: booleans,
bounded numbers/strings, declarative UI values and package-owned record IDs. It
MUST reject Telegram-derived text/media/metadata, account/user/chat/message
identifiers, opaque host handles, reliable-event payloads, credentials and any
value marked sensitive by the host. Serialization does not remove this label.

The registry evaluates the complete dependency/IPC graph, but an addon cannot
use IPC to lend a capability to another principal. Sensitive cross-addon data
flows require a future ADR, explicit payload labels, receiver-side effective
capabilities and dangerous-combination review.

To make that boundary enforceable without pretending Luau has complete dynamic
taint tracking, an IPC manifest in v1 may request only `ipc.*`, `ui.*`,
`storage.*`, `settings.global_safe` and `diagnostics.own`. It cannot combine IPC
with `events.*`, `network.*`, any `telegram.*` or `tdlib.raw.*` capability.
Reusable code needing Telegram access is a library loaded inside the consumer's
sandbox, not a second IPC principal.

The runtime repeats this check against the complete declared and currently
effective grant set before starting either endpoint. A principal with any
capability outside that allowlist cannot provide or consume IPC; stale grants
from an older surface are revoked, not ignored for this calculation. Therefore
v1 does not depend on provenance/taint surviving arbitrary Luau computation.

Every `consumes` entry names one provider by `(id, publisher_key_id)` and MUST
match exactly one `dependencies.requires` entry. Resolution binds it to the
concrete installed dependency identity; dispatch is keyed by `(account_handle,
provider_install_identity, interface_name, interface_version,
schema_sha256)`. Calls never cross accounts, never select a provider by display
name or priority, and fail closed if the resolved provider changes or becomes
ambiguous. Unsigned projects use their host-generated `development_install_id`
only after Developer Mode dependency resolution; a claimed manifest identity
never becomes the routing key.

## 4. Semantic Telegram capabilities

Names are stable product identifiers. The exact Kotlin/Luau method inventory is
generated later, but methods MUST map to one or more rows below.

| Capability | Class | Examples and constraints |
| --- | --- | --- |
| `telegram.account.read_basic` | Sensitive read | Current account display name/avatar through redacted model |
| `telegram.chats.read` | Sensitive read | Chat list, folders and basic chat metadata |
| `telegram.messages.read_metadata` | Sensitive read | IDs, timestamps, sender refs, reply/thread relations |
| `telegram.messages.read_content` | Sensitive read | Text, captions and media metadata; excludes protected content |
| `telegram.messages.observe` | Sensitive read | New/edit/delete semantic events through bounded queues |
| `telegram.messages.send` | Sensitive write | Send declared content types to a concrete chat |
| `telegram.messages.edit` | Sensitive write | Edit messages where TDLib reports permission |
| `telegram.messages.delete` | Sensitive write or Critical | Per-message delete may be grantable; bulk/history deletion is critical |
| `telegram.messages.forward` | Sensitive write | Forward/copy to a concrete destination |
| `telegram.reactions.write` | Sensitive write | Add/remove reactions and paid reaction confirmation where applicable |
| `telegram.drafts.write` | Sensitive write | Read/write draft only in the bound account |
| `telegram.contacts.read` | Sensitive read | Telegram contacts; never Android contacts automatically |
| `telegram.contacts.write` | Sensitive write | Add/import/remove Telegram contacts with validated fields |
| `telegram.profile.write` | Sensitive write | Bio, photo and account settings; high-impact changes may be critical |
| `telegram.chat.manage` | Sensitive write or Critical | Member/admin/settings operations according to impact |
| `telegram.files.download` | Sensitive read | TDLib-managed download returning `FileHandle` |
| `telegram.files.upload` | Sensitive write | TDLib-managed upload from an approved `MediaHandle` |
| `telegram.files.import_user` | Critical user gesture | SAF/Photo Picker selection; handle only, no path |
| `telegram.files.export_user` | Critical user gesture | MediaStore/SAF destination chosen by the user |
| `telegram.calls.observe` | Sensitive read | Call state and participant metadata in redacted semantic form |
| `telegram.calls.control` | Sensitive write or Critical | Answer/hang up/mute; starting/inviting may require one-shot confirmation |
| `telegram.calls.capture_audio` | Critical + Android permission | Microphone only during a visible call action |
| `telegram.calls.capture_video` | Critical + Android permission | Camera only during a visible call action |
| `telegram.calls.capture_screen` | Critical + system consent | MediaProjection prompt for every capture session |
| `telegram.location.read` | Critical + Android permission | Foreground user-visible request only in initial interface |
| `telegram.location.send` | Critical | Concrete chat and payload preview |
| `telegram.secret_chats.read` | Sensitive read | Secret-chat metadata/content only when separately granted |
| `telegram.secret_chats.observe` | Sensitive read | Secret-chat events; reliable retention is separately gated |
| `telegram.secret_chats.preserve_deleted` | ToS-sensitive read/write | Disabled outside Modification Mode and prominently labeled |
| `telegram.ephemeral.read` | Sensitive read | View ephemeral content through non-path handles |
| `telegram.ephemeral.persist` | ToS-sensitive Critical | Explicit warning and destination for every persistence operation |

Account deletion, logout, transfer of ownership, password/passkey changes,
session revocation, payment confirmation, broad history deletion and equivalent
irreversible operations are always Critical even if a broader semantic
capability is granted.

## 5. Raw TDLib capabilities

| Capability | Class | Invariants |
| --- | --- | --- |
| `tdlib.raw.objects` | Sensitive read | Typed proxy access only for declared constructors/fields |
| `tdlib.raw.invoke` | Sensitive write | Only manifest-listed `td_api` functions and generated validators |
| `tdlib.raw.observe_incoming` | Sensitive read | Immutable original updates; sensitive fields redacted separately |
| `tdlib.raw.mutate_incoming` | Sensitive write | Same constructor, copy-on-write fields, per-addon deadline |
| `tdlib.raw.suppress_incoming` | Sensitive write | Separate grant; terminal suppression in priority pipeline |

The manifest MUST list exact raw function and update constructor names in
addition to requesting the capability. Wildcards are forbidden in every
manifest, including Developer Mode. Developer Mode may override a raw
schema-version mismatch after a separate warning, but it still requires exact
function/update/field declarations and rejects a name absent from the installed
generated schema.

Raw invoke never means raw MTProto. It remains TDLib `td_api` plus reviewed
typed extensions in the pinned fork.

Every raw operation computes its effective permission class before any data is
returned or request is sent:

```text
effective_class = maximum(
  raw capability class,
  function/update constructor class,
  every input and output field class,
  argument/entity/target context class,
  dangerous-combination class
)
```

`Forbidden` at any level denies the operation. `Critical` always requires a
fresh confirmation describing the concrete function, account, target and
effects; it can never be satisfied by a persistent `tdlib.raw.invoke` grant.
ToS-sensitive is an additional conjunctive gate and does not lower the
underlying class. Incoming updates are redacted to the maximum field class the
principal already holds; a mutator cannot write an undeclared or more-sensitive
field. CI fails if any generated function, constructor, argument, result or
field lacks policy metadata.

### Protected incoming state

The following categories can be observed only in a redacted immutable form and
cannot be mutated or suppressed:

- `updateAuthorizationState` and every authorization/database-encryption state;
- client closing, closed, initialization and request-correlation state;
- connection/control-plane state needed to maintain TDLib consistency;
- Mod Host identity, grants, revocation and policy state;
- Android permission results and signing/trust decisions.

The exact constructor/field overlay lives in `core-policy`. CI compares it to
the generated TDLib schema and fails when coverage is incomplete.

## 6. ToS-sensitive capability family

These capabilities exist only inside Modification Mode and are labeled as
conflicting or potentially conflicting with Telegram API Terms:

| Capability | Underlying operation |
| --- | --- |
| `telegram.tos.read_receipts.modify` | Delay, suppress or alter read/open reporting behavior |
| `telegram.tos.presence.modify` | Alter online/last-seen reporting or display behavior |
| `telegram.tos.typing.modify` | Alter typing/chat-action reporting or display behavior |
| `telegram.tos.deleted_content.preserve` | Preserve content after deletion semantics |
| `telegram.ephemeral.persist` | Retain self-destructing/one-time content |

The global warning is not a grant. Each addon declares and requests the exact
capability. Store records repeat it and explain the risk. “Verified” means
source/security review only.

## 7. Forbidden and unavailable interfaces

The following are absent from the bridge and cannot be requested:

| Identifier | Reason |
| --- | --- |
| `android.context` / arbitrary intents | Escapes host validation and Android permission UX |
| `java.reflect` / class loading | Arbitrary JVM execution |
| `jni.handle` / `native.load` | Native-code execution and memory-safety escape |
| `dex.load` / `jar.load` | Executable binary loading |
| `filesystem.raw` | Telegram databases, keys and unrelated user data exposure |
| `process.exec` | Arbitrary operating-system execution |
| `network.socket` / unrestricted network | Bypasses domain, TLS and quota policy |
| `tdlib.client` / `mtproto.raw` | Bypasses ordering, state and policy invariants |
| `telegram.auth_key` / `firebase.token` | Credential theft and session compromise |
| `webview.javascript_interface` / Mini App cookies | Cross-sandbox code/data access |
| background clipboard access | Sensitive ambient data; not in the initial interface |
| cross-account access | Requires a future ADR and explicit user model |

## 8. Quotas

Every capability call consumes one or more quota dimensions:

- calls per second/minute;
- bytes in/out;
- concurrent async operations;
- Lua instructions and wall-clock time;
- Lua heap bytes;
- event queue count/bytes;
- storage records/bytes;
- reliable journal records/bytes/TTL;
- UI node count/depth/update rate.

Initial numeric defaults are calibrated on the dedicated release phone and are
not part of Mod API compatibility. A manifest may request a larger declared
quota. Increases require user approval and block automatic updates.

For raw TDLib, `fields` declares which object fields may be read, observed or
written. `tdlib.raw.objects`, `tdlib.raw.invoke`,
`tdlib.raw.observe_incoming` and `tdlib.raw.mutate_incoming` therefore require a
non-empty field list. `tdlib.raw.suppress_incoming` is the only constructor-only
operation: it requires update constructors but no field access unless combined
with another raw capability. Runtime authorization still computes the maximum
risk of the capability, constructor/function, requested fields, arguments,
context and their combination for every call.

## 9. Permission revocation

Revocation is immediate for new work:

- cancel in-flight bridge operations where safe;
- remove subscriptions and network sessions;
- prevent new reliable snapshots;
- retain already stored/preserved data until the user separately confirms
  deletion;
- record a sanitized local audit entry.

Disabling Modification Mode behaves as a global execution suspension, not as
individual grant revocation. Grants remain encrypted and are reusable when the
mode is explicitly enabled again.

## 10. Review checklist

A new capability is incomplete until it has:

1. Stable identifier and permission class.
2. Manifest schema entry.
3. User-facing prompt/risk copy.
4. Account and package scoping rule.
5. Argument/return validators and redaction.
6. Quota accounting.
7. Revocation/cancellation behavior.
8. Sync-hook rule.
9. Store review rule.
10. Positive, negative and confused-deputy tests.
