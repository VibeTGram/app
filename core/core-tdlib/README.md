# core-tdlib

`core-tdlib` is the only TDLib adapter for the MVP. It owns one injected
`ClientManager` client per `TdLibEngine` and translates typed TDLib callbacks to
the pure `core-api` contract. Android production uses the pinned official
JSON-Java JNI entry point through `OfficialJsonClientTransport`; the process-wide
`TdJsonClientManager` correlates `@extra` request IDs and routes the single
ordered receive loop by `@client_id`.

The adapter guarantees:

- the database key and isolated database/files directories are sent in the pinned
  `setTdlibParameters` shape (this TDLib revision has no separate key-check call);
- request IDs are correlated privately, including concurrent out-of-order replies;
- idempotent chat/history reads retry transient network failures and follow
  TDLib pagination cursors;
- authorization and message updates are delivered in callback order;
- process recovery closes and replaces the one client, then replays setup;
- no raw JSON, generic MTProto method, JNI object, or native pointer crosses the
  semantic boundary.

The Android package must contain `libtdjsonjava.so` for both `arm64-v8a` and
`x86_64`; plain `libtdjson.so` doesn't export the Java native methods. Tests use
deterministic callback and JSON transport fakes, so no Telegram credentials or
network are required for JVM verification.
