# Core storage

`core-storage` contains the JVM-testable account and persistence boundary used by
`core`. It does not import Android classes or expose account IDs, raw paths, SQL,
or encryption keys to callers.

- `AccountManager` issues opaque account handles and gives each account random
  TDLib, database, file, addon, key-alias, and WebView-suffix boundaries.
- `EncryptedAccountStore` uses AES-GCM, copies values at the API boundary, and
  commits changes through sibling-file replacement. Schema migrations run on a
  copy and are published only after the complete chain succeeds.
- `KeyProtector`, `AccountRuntime`, addon cleanup, and WebView cleanup are ports;
  the Android composition root supplies Keystore/TDLib/runtime implementations.
- Locked device-protected keys return an explicit first-unlock state. Failed
  WebView cleanup keeps Mini Apps disabled and quarantines the suffix.

This is a bootstrap implementation. Android adapters must provide a real
Keystore-backed `KeyProtector` and account-owned TDLib/database/file adapters.
