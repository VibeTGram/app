# Two-level Telegram API

Status: **Normative interface inventory; signatures are bootstrap pseudocode**

Owners: semantic contracts in `VibeTGram/core`, mod facade/types in
`VibeTGram/mods`

The GUI and addons do not call TDLib/JNI directly. The first level is a stable
semantic API organized around VibeTGram concepts. The second is a generated,
version-pinned `td_api` surface for features that cannot yet be represented by
the semantic level. TDLib remains the only MTProto engine.

## 1. Call shape

Semantic commands are cancellable `suspend` operations returning typed results;
state is an ordered `Flow`/subscription of immutable snapshots. Pseudocode:

```kotlin
suspend fun sendMessage(
    account: AccountHandle,
    chat: ChatRef,
    content: OutgoingContent,
    options: SendOptions,
): TelegramResult<MessageRef>

fun observeMessages(
    account: AccountHandle,
    chat: ChatRef,
): Flow<MessageDelta>
```

`TelegramResult<T>` contains typed values or stable errors such as
`PermissionDenied`, `NotFound`, `RateLimited`, `NetworkUnavailable`,
`IncompatibleSchema`, `UserConfirmationRequired`, `Cancelled` and
`UpstreamUnsupported`. Exceptions are reserved for programmer bugs and process
failure.

The host supplies the account handle and mod principal. Addon code never passes
a package/publisher/account identity string. Every operation has a generated
`OperationDescriptor` consumed by `core-policy` before the adapter executes it.

## 2. Semantic services

This is the initial service inventory. Closing a parity row may add an operation
through a reviewed interface change; it must not expose TDLib implementation
types in a semantic signature.

### Authorization and accounts

| Service | Operations | Addon exposure |
| --- | --- | --- |
| `AuthorizationService` | observe state; set TDLib parameters; phone/email/code/password/QR flows; accept terms; register; recover; close | GUI/app only |
| `AccountManager` | create/remove context; list/switch warm account; observe connection; logout; delete account; first-unlock recovery | GUI/app only; addons receive one bound context |
| `SessionService` | list/rename/terminate sessions; confirm login; list websites/bots/passkeys | Read/write capability; termination/password actions Critical |

Authorization state, database keys, auth keys, login codes and FCM tokens are
never part of either addon API level.

### Chats, folders and search

| Service | Operations |
| --- | --- |
| `ChatQuery` | get/list chats; pagination; archive; saved-message topics; nearby result when available |
| `ChatMutation` | create private/basic/supergroup/channel/secret chat; set title/photo/description/permissions; pin/archive/mute/mark unread |
| `FolderService` | list/create/edit/delete/reorder folders; recommended folders; invite-link flows |
| `SearchService` | search chats/messages/media/downloads/members/public chats; recent search; filters and pagination |

### Messages and drafts

| Service | Operations |
| --- | --- |
| `MessageQuery` | history/gaps/thread/topic; get by ref; search; replies; pinned/scheduled messages; read dates; statistics entry |
| `MessageComposer` | send text/media/album/contact/location/poll/dice/invoice; reply/quote; schedule; silent/send-as; send-when-online |
| `MessageMutation` | edit text/caption/media/live location; delete self/everyone; forward/copy; pin; report; block/moderate |
| `ReactionService` | list available/recent reactors; add/remove standard/custom/paid reaction with payment confirmation |
| `DraftService` | get/set/clear cloud draft and reply target |

`OutgoingContent` is a sealed semantic family. Adding a Telegram content type
adds a typed case and validation; it never introduces an untyped JSON payload.

### Files and media

| Service | Operations |
| --- | --- |
| `FileService` | observe TDLib file; start/pause/cancel/prioritize download; delete cache copy; upload state; stream range |
| `UserMediaPort` | import through Photo Picker/SAF; create bounded `MediaHandle`; export/share/open through MediaStore/SAF/system UI |
| `MediaMetadataService` | validated image/video/audio/document metadata, thumbnails, waveforms and streaming descriptors |
| `PlaybackService` | playlist/audio/voice/video-note playback state, speed, seek and audio focus |
| `StickerEmojiService` | installed/featured/recent/favorite sets; search; reorder; custom emoji and emoji status |

TDLib owns Telegram download/upload state. `FileHandle` and `MediaHandle` are
opaque, account/principal-bound and expiring. Neither level returns a raw local
path or arbitrary file descriptor to Lua.

### Contacts, profile, privacy and settings

| Service | Operations |
| --- | --- |
| `ContactService` | Telegram contacts; search/import after Android consent; add/remove; contact status |
| `ProfileService` | user/full info; photos; bio/birthdate/usernames; account profile mutations |
| `PrivacyService` | get/set privacy rules; blocked users; new-chat privacy; auto-delete account; local app lock ports |
| `NotificationService` | chat/category settings; sound; preview; scope exceptions; register encrypted push token |
| `NetworkService` | proxy CRUD/test/share; network statistics; auto-download/data-saving policies |
| `StorageService` | cache statistics; cleanup plan/execute; per-chat storage and low-storage state |

Android contacts, location, camera, microphone, notifications and media access
remain Android ports with system permission/gesture gates; a Telegram capability
does not imply an Android permission.

### Groups, channels, topics and business

| Service | Operations |
| --- | --- |
| `MemberAdminService` | members; join requests; invite links; add/remove/restrict/ban; admin rights; ownership transfer |
| `TopicService` | create/edit/close/reopen/delete/reorder topics; general/direct-message topics |
| `ChannelService` | discussion; usernames; reactions; boosts; statistics; monetization/revenue entry points |
| `BusinessService` | business hours/location/greeting/away/quick replies; chat links; connected bots |

Ownership transfer, broad deletion, money movement and session/security changes
are Critical regardless of the service-level capability.

### Stories, bots, Mini Apps and payments

| Service | Operations |
| --- | --- |
| `StoryService` | list/get/post/edit/delete/pin/archive; privacy; areas; viewers; reactions/replies; channel stories |
| `BotService` | commands/menu; inline queries/results; callback queries; attachment-menu lifecycle |
| `MiniAppService` | request/confirm WebView URL; official JS events; close/prolong; permissions/downloads through WebView broker |
| `PaymentService` | invoice/form/shipping/checkout; Stars/subscriptions/transactions; paid media/messages; refunds where supported |
| `GiftPremiumService` | Premium state/options, gifts/collectibles/upgrades/giveaways and boosts |

Mini Apps run only in the host WebView profile/suffix. Lua receives no WebView,
JavaScript interface, cookie or payment credential.

### Calls and live streams

| Service | Operations |
| --- | --- |
| `CallService` | create/accept/decline/hang up; signaling; routes; mute; rating/debug consent |
| `GroupCallService` | create/join/leave/schedule; participants; mute/volume; video sources; recording/RTMP controls |
| `CapturePort` | camera/microphone/MediaProjection sessions after Android consent |

TDLib handles Telegram call signaling. Pinned `tgcalls` is hidden behind
`CallEngine`; neither semantic nor raw TDLib APIs expose its native pointers.

## 3. Semantic events

Core publishes immutable account-scoped deltas for authorization/connection,
chat lists/folders, chats, messages, files, notifications, users, groups,
stories, calls and settings. GUI reducers consume complete ordered streams.

Addon subscriptions are projections selected by declared capabilities:

- best-effort queues are bounded and may coalesce state-like events;
- message create/edit/delete events do not coalesce;
- reliable journals are opt-in, encrypted, quota/TTL bounded and sensitive;
- protected lifecycle/control-plane events are redacted and never mutable;
- original snapshots are immutable even when a separate mutation pipeline
  produces a validated downstream result.

## 4. Raw TDLib level

The raw level is generated from the exact pinned `td_api.tl` and identified by:

```text
(tdlib_commit, normalized_schema_hash, generator_version)
```

Its conceptual host surface is intentionally small:

```kotlin
suspend fun <R : RawObject> invoke(
    function: RawFunction<R>,
): TelegramResult<R>

fun observeRawUpdates(
    constructors: Set<RawUpdateConstructor>,
): Flow<ImmutableRawUpdate>
```

Addons additionally register manifest-declared synchronous patch/suppression
hooks through the Mod Host; those hooks are not methods on TDLib. The host
validates generated proxy types, exact function/update/field declarations,
effective permission class, immutable originals, copy-on-write patches,
deadlines and priority after every addon.

There is no function accepting a constructor name plus arbitrary JSON/bytes and
no generic MTProto request. A missing Telegram RPC is first proposed upstream;
if necessary, the minimal TDLib fork adds a concrete `td_api.tl` type, regenerates
both language bindings and expands the exhaustive policy overlay.

## 5. Exposure to GUI and mods

The replaceable GUI receives semantic service interfaces and immutable models.
It may use an internal parity adapter for a generated raw type only while a
semantic operation is being designed; Stable requires that exception to be
tracked in the parity matrix.

The Mod SDK exposes per-capability facades from the same semantic contracts,
not the whole trusted GUI interface. Raw access is optional and separately
declared. The policy matrix defines grants, critical prompts and forbidden
surfaces. Unsigned Developer Mode does not widen either level.

## 6. Versioning and completeness

- Semantic API uses independent SemVer and expand/migrate/contract changes.
- Signed compatibility requirements use the normalized range object defined by
  the package schema: one exact version, or inclusive-minimum/exclusive-maximum
  bounds. Arbitrary range strings are never trust inputs.
- Raw API compatibility is exact by default; Developer Mode may override the
  schema-hash check but not names, types or policy coverage.
- Every semantic operation maps to parity rows, capability rows and one or more
  TDLib/call/Android adapter operations.
- Every generated raw function, constructor, argument, result and field has a
  policy classification; missing coverage fails CI.
- Contract tests run semantic services against fake and TDLib adapters.
- Full method signatures and generated Luau types live in source after the six
  repositories are bootstrapped; this document owns the stable service boundary.
