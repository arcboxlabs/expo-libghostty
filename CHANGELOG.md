# Changelog

## Unpublished

### 🛠 Breaking changes

### 🎉 New features

- Android: keyboard accessory bar (Esc / Ctrl / Alt / Tab / arrows / nav)
  above the soft keyboard. Ctrl and Alt are sticky and compose the next key
  — from the bar, the IME, or a hardware keyboard — through ghostty's
  encoder. The view now pads itself above the IME in edge-to-edge windows,
  so the covered grid rows come back too.
- Android: inertial scrollback (fling), a fading scroll-position indicator,
  and a jump-to-bottom chip; typed input snaps back to the live view.
- Android: selection polish — long-press keeps extending the selection
  without lifting, drags show the system magnifier (API 28+), and the end
  handle accounts for wide (CJK) final cells.

### 🐛 Bug fixes

### 💡 Others

- CI now compiles the Android module (and lints/builds the JS) on every
  push and pull request.
- Cleaned up `create-expo-module` scaffold leftovers: LICENSE attribution is
  now ArcBox, Inc., the podspec version is read from `package.json`, the
  unused jest and webpack scaffolding is gone, and the example app has
  terminal-themed icons instead of the Expo defaults.

## 0.5.0 — 2026-07-17

### 🎉 New features

- Android: touch selection and clipboard. Long-press selects the word under
  the finger (ghostty word-boundary semantics); draggable handles adjust the
  range; the floating action-mode toolbar offers Copy / Paste / Select all.
  Copy extracts text with ghostty's copy semantics (unwrap + trim); paste is
  encoded through ghostty (control-byte strip, bracketed paste when mode 2004
  is set) with a confirmation dialog for multi-line clipboard content.
  Selection follows scrollback via terminal-tracked references and clears on
  any typed input.

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
