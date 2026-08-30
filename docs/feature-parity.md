# Telegram Android feature-parity matrix

Status: **Discovery baseline**

Reference application commit:
[`45ab8f4308496e1f01026a97fcdb0d58a5274474`](https://github.com/DrKLO/Telegram/commit/45ab8f4308496e1f01026a97fcdb0d58a5274474)

TDLib commit:
[`022d60202e446ad1287b9fb68e687c8a0760788b`](https://github.com/tdlib/td/commit/022d60202e446ad1287b9fb68e687c8a0760788b)

## 1. Release rule

The first Stable release requires every user-facing feature observable in the
pinned Telegram Android commit to have one of these terminal states:

| State | Meaning |
| --- | --- |
| `DONE` | Core behavior, GUI, accessibility and required tests pass |
| `BLOCKED_UPSTREAM` | Exact missing TDLib/Telegram capability is evidenced and linked |
| `NOT_APPLICABLE` | Feature is build/distribution-specific and rationale is approved |

`TODO`, `AUDIT`, `PARTIAL`, `FAILED`, or a blank row blocks Stable. A
`BLOCKED_UPSTREAM` row must contain the absent TDLib function/object evidence,
an upstream issue or local-fork decision, and the user-visible fallback.

Feature parity means behavior and capability, not visual copying. VibeTGram uses
its own Material 3 Expressive GUI, branding, strings and resources.

## 2. Evidence required per row

A feature is `DONE` only when its linked evidence includes:

1. Exact reference behavior at the pinned Telegram Android commit.
2. TDLib functions, objects, options and updates used.
3. Semantic interface/use case and raw-policy classification.
4. GUI route/state and adaptive/accessibility behavior.
5. Unit/contract tests and required integration/UI/manual tests.
6. Error, offline, cancellation, permission and multi-account behavior.
7. Mod extension slots or explicit “not extensible” rationale.

The tables below are the minimum audit inventory. Discovery may add rows but
must never remove a reference feature silently.

## 3. Authorization and accounts

| ID | Feature | Initial state | Evidence/blocker |
| --- | --- | --- | --- |
| AUTH-001 | Phone-number authorization and country selection | AUDIT | |
| AUTH-002 | QR-code login and existing-session confirmation | AUDIT | |
| AUTH-003 | Login code variants, resend and recovery | AUDIT | |
| AUTH-004 | Two-step verification password and recovery email | AUDIT | |
| AUTH-005 | Terms, age/email/Firebase verification states exposed by TDLib | AUDIT | |
| AUTH-006 | Passkey/login-security flows present in reference | AUDIT | |
| AUTH-007 | Signup, name and profile photo | AUDIT | |
| AUTH-008 | Multiple accounts and account switcher | AUDIT | |
| AUTH-009 | Logout, account deletion and local-data confirmation | AUDIT | |
| AUTH-010 | Active sessions/devices and session revocation | AUDIT | |
| AUTH-011 | Account inactivity TTL | AUDIT | |
| AUTH-012 | Application PIN, biometrics and locked notification privacy | AUDIT | |

## 4. Chat list and navigation

| ID | Feature | Initial state | Evidence/blocker |
| --- | --- | --- | --- |
| LIST-001 | Main chat list, unread counters and pagination | AUDIT | |
| LIST-002 | Archive, archive settings and archived counters | AUDIT | |
| LIST-003 | Chat folders, recommended folders and invite links | AUDIT | |
| LIST-004 | Pinned chats and reorder | AUDIT | |
| LIST-005 | Mark read/unread, mute and notification shortcuts | AUDIT | |
| LIST-006 | Chat preview, draft, typing and delivery indicators | AUDIT | |
| LIST-007 | Global search and recent search | AUDIT | |
| LIST-008 | Search filters for chats, messages, media and downloads | AUDIT | |
| LIST-009 | Saved Messages and Saved Messages topics | AUDIT | |
| LIST-010 | Adaptive phone/tablet/foldable multi-pane navigation | AUDIT | |

## 5. Message history and composition

| ID | Feature | Initial state | Evidence/blocker |
| --- | --- | --- | --- |
| MSG-001 | Ordered history, gaps, pagination and date navigation | AUDIT | |
| MSG-002 | Text send with entities, markdown helpers and link preview | AUDIT | |
| MSG-003 | Reply, quote, cross-chat reply and thread/topic targeting | AUDIT | |
| MSG-004 | Edit, delete for self/everyone and bulk selection | AUDIT | |
| MSG-005 | Forward, copy, hide sender/caption and target picker | AUDIT | |
| MSG-006 | Drafts, cloud drafts and multi-device update | AUDIT | |
| MSG-007 | Scheduled and send-when-online messages | AUDIT | |
| MSG-008 | Silent send, protected content and content restrictions | AUDIT | |
| MSG-009 | Send as user/channel and message sender selection | AUDIT | |
| MSG-010 | Slow mode, paid messages and send-option validation | AUDIT | |
| MSG-011 | Reactions, custom/paid reactions and recent reactors | AUDIT | |
| MSG-012 | Message views, forwards, read dates and statistics entry points | AUDIT | |
| MSG-013 | Translation and language-detection behavior present in reference | AUDIT | |
| MSG-014 | Report, block and moderation actions | AUDIT | |
| MSG-015 | Pinned messages and pin-management UI | AUDIT | |
| MSG-016 | Discussion threads, forum topics and direct-message topics | AUDIT | |
| MSG-017 | Checklists/tasks present in the pinned reference | AUDIT | |
| MSG-018 | Suggested posts and approval flows present in reference | AUDIT | |

## 6. Message content and media

| ID | Feature | Initial state | Evidence/blocker |
| --- | --- | --- | --- |
| MEDIA-001 | Photo send/view/edit/caption/spoiler | AUDIT | |
| MEDIA-002 | Video send/stream/view/edit/caption/spoiler | AUDIT | |
| MEDIA-003 | Albums/media groups | AUDIT | |
| MEDIA-004 | Documents and generic files | AUDIT | |
| MEDIA-005 | Audio/music player, playlists and background playback | AUDIT | |
| MEDIA-006 | Voice messages, waveform, playback speed and transcription | AUDIT | |
| MEDIA-007 | Video notes, recording and playback | AUDIT | |
| MEDIA-008 | Camera/gallery/system picker composition | AUDIT | |
| MEDIA-009 | Contacts and vCard behavior | AUDIT | |
| MEDIA-010 | Location, venues and live location | AUDIT | |
| MEDIA-011 | Polls and quizzes | AUDIT | |
| MEDIA-012 | Dice, games and interactive message content | AUDIT | |
| MEDIA-013 | Stickers, animated/video stickers and recent/favorites | AUDIT | |
| MEDIA-014 | Custom emoji, emoji status and animated emoji | AUDIT | |
| MEDIA-015 | GIF/animation search, send and recent items | AUDIT | |
| MEDIA-016 | Link previews, instant view and embedded media | AUDIT | |
| MEDIA-017 | Invoices, paid media and payment-result messages | AUDIT | |
| MEDIA-018 | Giveaways, gifts and collectible/upgrade content | AUDIT | |
| MEDIA-019 | Download manager and chat download list | AUDIT | |
| MEDIA-020 | Save/export/share/open-with using SAF/MediaStore | AUDIT | |
| MEDIA-021 | Auto-download, streaming and storage policies | AUDIT | |
| MEDIA-022 | Content expiration and one-time media | AUDIT | |

## 7. Chat types and administration

| ID | Feature | Initial state | Evidence/blocker |
| --- | --- | --- | --- |
| CHAT-001 | Private chats and user profiles | AUDIT | |
| CHAT-002 | Basic groups and upgrade to supergroup | AUDIT | |
| CHAT-003 | Supergroups, channels and discussion groups | AUDIT | |
| CHAT-004 | Forum creation, topics, general topic and permissions | AUDIT | |
| CHAT-005 | Member list, search, invite/add/remove/ban | AUDIT | |
| CHAT-006 | Admin roles, granular rights and ownership transfer | AUDIT | |
| CHAT-007 | Default/member permissions and restricted users | AUDIT | |
| CHAT-008 | Invite links, join requests and QR invite display | AUDIT | |
| CHAT-009 | Public usernames, collectible usernames and links | AUDIT | |
| CHAT-010 | Chat photo, title, description, reactions and appearance | AUDIT | |
| CHAT-011 | Auto-delete timer and protected content | AUDIT | |
| CHAT-012 | Channel statistics, boosts, monetization and revenue views | AUDIT | |
| CHAT-013 | Sponsored messages required by Telegram terms | AUDIT | |
| CHAT-014 | Business chat links, connected bots and business features | AUDIT | |
| CHAT-015 | Nearby/people features present in the pinned reference | AUDIT | |

## 8. Stories and live content

| ID | Feature | Initial state | Evidence/blocker |
| --- | --- | --- | --- |
| STORY-001 | Story tray, active stories and pagination | AUDIT | |
| STORY-002 | Story photo/video composer and privacy selection | AUDIT | |
| STORY-003 | Captions, entities, areas, stickers and repost | AUDIT | |
| STORY-004 | Story views, reactions, replies and viewer list | AUDIT | |
| STORY-005 | Story archive, albums, pinning and editing | AUDIT | |
| STORY-006 | Stealth mode and Premium restrictions | AUDIT | |
| STORY-007 | Channel stories and boosts | AUDIT | |
| STORY-008 | Live stories/streams present in pinned TDLib/reference | AUDIT | |

## 9. Calls, video chats and live streams

| ID | Feature | Initial state | Evidence/blocker |
| --- | --- | --- | --- |
| CALL-001 | Incoming/outgoing private voice calls | AUDIT | |
| CALL-002 | Private video calls and camera switching | AUDIT | |
| CALL-003 | Call permissions, audio routes, focus and proximity | AUDIT | |
| CALL-004 | Call quality, reconnect, rating and debug submission | AUDIT | |
| CALL-005 | Group voice chats and participant controls | AUDIT | |
| CALL-006 | Group video, video sources and layout | AUDIT | |
| CALL-007 | Screen sharing with system consent | AUDIT | |
| CALL-008 | Scheduled video chats and notifications | AUDIT | |
| CALL-009 | RTMP streams and administrator controls | AUDIT | |
| CALL-010 | Group-call recording controls and status | AUDIT | |

## 10. Bots, Mini Apps and payments

| ID | Feature | Initial state | Evidence/blocker |
| --- | --- | --- | --- |
| BOT-001 | Bot profiles, commands, menus and privacy indicators | AUDIT | |
| BOT-002 | Reply/inline keyboards and callback queries | AUDIT | |
| BOT-003 | Inline bots, results and inline switch flow | AUDIT | |
| BOT-004 | Attachment-menu bots | AUDIT | |
| BOT-005 | Main/keyboard/inline/direct-link Mini Apps | AUDIT | |
| BOT-006 | Official Mini Apps JS events and close/prolong lifecycle | AUDIT | |
| BOT-007 | Per-account WebView profiles, cookies and permissions | AUDIT | |
| BOT-008 | Downloads/uploads initiated by Mini Apps | AUDIT | |
| BOT-009 | Invoices, shipping, checkout and payment confirmation | AUDIT | |
| BOT-010 | Telegram Stars, subscriptions and transaction history | AUDIT | |
| BOT-011 | Telegram Passport/identity authorization where present | AUDIT | |
| BOT-012 | OAuth/login URL confirmation | AUDIT | |

## 11. Contacts, profiles and privacy

| ID | Feature | Initial state | Evidence/blocker |
| --- | --- | --- | --- |
| PRIV-001 | Telegram contacts and Android contact permission flow | AUDIT | |
| PRIV-002 | User/profile media, bio, birthdate and usernames | AUDIT | |
| PRIV-003 | Blocked users and new-chat privacy | AUDIT | |
| PRIV-004 | Last seen/online privacy and exceptions | AUDIT | |
| PRIV-005 | Profile photo, calls, forwards, groups and message privacy | AUDIT | |
| PRIV-006 | Phone number discovery and visibility | AUDIT | |
| PRIV-007 | Read-time privacy and Premium-related controls | AUDIT | |
| PRIV-008 | Auto-delete account and data settings | AUDIT | |
| PRIV-009 | Two-step verification, recovery and passkeys | AUDIT | |
| PRIV-010 | Websites, bots and connected-session management | AUDIT | |
| PRIV-011 | Secret chats, key visualization and lifecycle | AUDIT | |
| PRIV-012 | Local app lock, screenshots and recent-app privacy | AUDIT | |

## 12. Notifications and background behavior

| ID | Feature | Initial state | Evidence/blocker |
| --- | --- | --- | --- |
| NOTIF-001 | Encrypted FCM registration and token rotation | AUDIT | |
| NOTIF-002 | Official GMS delivery | AUDIT | |
| NOTIF-003 | microG delivery | AUDIT | |
| NOTIF-004 | Foreground-service fallback and persistent notification | AUDIT | |
| NOTIF-005 | Per-chat/category notification settings | AUDIT | |
| NOTIF-006 | Notification grouping, reply, read and mute actions | AUDIT | |
| NOTIF-007 | Locked-screen preview privacy | AUDIT | |
| NOTIF-008 | Calls and scheduled reminder notifications | AUDIT | |
| NOTIF-009 | Reboot, first-unlock and process-death recovery | AUDIT | |
| NOTIF-010 | Doze, restricted battery and OEM behavior documentation | AUDIT | |

## 13. Settings, storage and network

| ID | Feature | Initial state | Evidence/blocker |
| --- | --- | --- | --- |
| SET-001 | Appearance, dark mode, dynamic color and text size | AUDIT | |
| SET-002 | English/Russian localization and locale formatting | AUDIT | |
| SET-003 | Data/storage usage and per-chat storage cleanup | AUDIT | |
| SET-004 | Auto-download by network/chat/media type | AUDIT | |
| SET-005 | Network statistics | AUDIT | |
| SET-006 | SOCKS5/HTTP/MTProto proxy configuration and sharing | AUDIT | |
| SET-007 | Calls data-saving and peer-to-peer policy | AUDIT | |
| SET-008 | Chat settings, swipe actions and media behavior | AUDIT | |
| SET-009 | Devices, privacy/security and notification settings | AUDIT | |
| SET-010 | Premium, Stars, gifts and subscription settings | AUDIT | |
| SET-011 | Help, FAQ, terms, privacy and support actions | AUDIT | |
| SET-012 | Cache optimizer and low-storage behavior | AUDIT | |
| SET-013 | Update checker and signed GitHub updater | AUDIT | |
| SET-014 | Sanitized local diagnostics and prefilled GitHub issue | AUDIT | |

## 14. Modification platform

These rows are additional VibeTGram requirements and do not substitute for
Telegram parity.

| ID | Feature | Initial state | Evidence/blocker |
| --- | --- | --- | --- |
| MOD-001 | Modification Mode off/on lifecycle and 15-second warning | TODO | |
| MOD-002 | Per-addon/account Luau states and allocator/watchdog | TODO | |
| MOD-003 | Semantic interface and typed errors | TODO | |
| MOD-004 | Generated raw TDLib proxies and `schemaHash` | TODO | |
| MOD-005 | Policy overlay exhaustive coverage and raw effective-class maximum | TODO | |
| MOD-006 | Permission prompts, durations and revocation | TODO | |
| MOD-007 | Ordered update mutation/suppression pipeline | TODO | |
| MOD-008 | Bounded best-effort and reliable events | TODO | |
| MOD-009 | Encrypted key-value/document storage | TODO | |
| MOD-010 | HTTPS domain bridge and dangerous-combination warning | TODO | |
| MOD-011 | Declarative UI slots/routes and accessibility validation | TODO | |
| MOD-012 | Non-sensitive Mod IPC and publisher-bound dependency isolation | TODO | |
| MOD-013 | `.vibemod` and `.vibetheme` validation/signatures | TODO | |
| MOD-014 | Byte-exact signed store index/delegation/review/revocation | TODO | |
| MOD-015 | Resource-pack priority and conflicts | TODO | |
| MOD-016 | Developer Mode, host-isolated unsigned identity and hot reload | TODO | |
| MOD-017 | ToS-sensitive labels, grants and warning flow | TODO | |
| MOD-018 | Safe-mode/crash-loop recovery | TODO | |

## 15. Quality gates

| ID | Gate | Initial state | Evidence/blocker |
| --- | --- | --- | --- |
| QA-001 | No unclassified TDLib function/field | TODO | |
| QA-002 | API 30 and latest-SDK unit/instrumented matrix | TODO | |
| QA-003 | TalkBack, large font, keyboard/D-pad and reduced motion | TODO | |
| QA-004 | Phone/tablet/foldable/ChromeOS adaptive layouts | TODO | |
| QA-005 | Test DC integration suite with disposable accounts | TODO | |
| QA-006 | GMS emulator push suite | TODO | |
| QA-007 | Dedicated microG release-device checklist | TODO | |
| QA-008 | Archive/parser/bridge fuzzing | TODO | |
| QA-009 | Dependency verification, SBOM and provenance | TODO | |
| QA-010 | Stable offline-signing ceremony | TODO | |
| QA-011 | WebView per-account profile/suffix process-restart isolation | TODO | |

## 16. Audit procedure

For each new pinned Telegram Android commit:

1. Diff user-facing feature flags, settings, screens, message types and actions
   against the previous reference.
2. Add new matrix rows before updating the reference pin.
3. Map rows to the pinned TDLib schema and changelog.
4. Reject the update if removed rows have no approved replacement/rationale.
5. Complete Nightly/Preview testing before the new reference can gate Stable.

The reference pin moves only through an explicit parity-cycle pull request. It
does not follow Telegram Android automatically.
