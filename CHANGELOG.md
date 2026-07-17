# Changelog

## Unpublished

### 🛠 Breaking changes

### 🎉 New features

### 🐛 Bug fixes

### 💡 Others

## 0.4.0 — 2026-07-17

### 🎉 New features

- Android: Nerd Font private-use glyphs (powerline, devicons, material, …)
  now render via a bundled Symbols Nerd Font Mono instead of tofu. The font
  ships in the vendor tarball and is exposed as an AAR asset; cells whose
  first codepoint is in U+E000–F8FF or U+F0000+ draw with it.
- Android: cursor blink. Follows the terminal's DECSCUSR-driven blink state
  (blinking by default), holds solid on input/output, pauses when the window
  is unfocused, and respects the system animations-off setting.

## 0.3.0 — 2026-07-16

### 🎉 New features

- Android support: libghostty-vt (ghostty's VT state machine) driven through a
  JNI shim with a Kotlin Canvas renderer — same JS contract as iOS
  (`onInput`/`onResize` events; `write`/`writeText`/`finish` methods). Covers
  colors, wide CJK cells, color emoji, bold/italic/underline/strikethrough,
  IME text input, hardware keys via ghostty's key encoder, and scrollback
  gestures. arm64-v8a + x86_64; prebuilt static libs are fetched
  checksum-pinned at install time (`scripts/download-android-libs.mjs`,
  built by CI from a pinned ghostty commit).
