# Generated Luau SDK

`spec.json` is the source of truth for public types and capability-scoped
facades. `generated/types.luau` and `generated/facades.luau` are deterministic
outputs and contain no host identity fields or native handles.

The generator emits calls through the private host dispatch primitive only;
the host maps each facade to a manifest grant. It does not expose Java, Android,
JNI, raw filesystem, WebView, process, socket, or Telegram-auth surfaces.

Compatibility: SDK, Mod API, semantic API, and GUI API `1.0.0`.
