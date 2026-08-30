# Android Compose host decisions

Date: 2026-08-30

## Sources read

- AndroidX Material 3 API reference: https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary
- AndroidX Material Expressive theme reference: https://developer.android.com/reference/kotlin/androidx/compose/material3/MaterialExpressiveTheme
- Android adaptive layout guidance: https://developer.android.com/develop/ui/compose/layouts/adaptive/build-adaptive-navigation
- Android accessibility guidance: https://developer.android.com/develop/ui/compose/accessibility
- Official Google Maven metadata for Compose BOM and Material 3:
  - https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/maven-metadata.xml
  - https://dl.google.com/dl/android/maven2/androidx/compose/material3/material3/maven-metadata.xml

## Decisions

- Keep the repository's verified `2025.03.00` Compose BOM. Google Maven reports
  Material 3 `1.4.0` as the latest stable release and `1.5.0-alpha27` as the
  latest preview, but the 1.4 `MaterialExpressiveTheme` and expanded `Shapes`
  symbols are internal to that published artifact for this pinned AGP/Kotlin
  toolchain. Taking the current BOM also produced a Kotlin ABI mismatch. The host
  therefore uses public `MaterialTheme` APIs with the project's expressive color,
  typography, shape and motion tokens; it does not take an alpha or bypass the
  repository toolchain/dependency-verification gate.
- Keep list/detail selection behind the existing typed `AdaptiveLayoutStrategy`.
  This preserves the replaceable GUI contract while following the official
  compact/medium/expanded navigation guidance.
- Honor the platform animator-disable setting as reduced motion. Motion duration
  becomes zero; accessibility corrections remain terminal and cannot be
  overridden by a resource pack.
- The app launcher never creates demo services. Until Core publishes a complete
  auth/account/chat/message dependency bundle, it displays typed blocker
  `CORE_GUI_DEPENDENCIES_UNAVAILABLE` and can only enter the GUI through an
  injected `GuiDependenciesProvider`.
