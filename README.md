# expo-libghostty

[![npm](https://img.shields.io/npm/v/expo-libghostty)](https://www.npmjs.com/package/expo-libghostty)
[![Release](https://github.com/arcboxlabs/expo-libghostty/actions/workflows/release.yml/badge.svg)](https://github.com/arcboxlabs/expo-libghostty/actions/workflows/release.yml)
[![license](https://img.shields.io/npm/l/expo-libghostty)](./LICENSE)
![platforms](https://img.shields.io/badge/platforms-iOS%2016.4%2B%20%7C%20Android-4630EB)

Ghostty terminal view for Expo / React Native, powered by
[libghostty](https://ghostty.org) — on iOS via
[Lakr233/libghostty-spm](https://github.com/Lakr233/libghostty-spm), on
Android via [libghostty-vt](https://github.com/ghostty-org/ghostty) with a
Canvas renderer.

- Real VT parsing from ghostty's core on both platforms
- Theming (background/foreground/cursor/selection + 256-color palette) and
  font size, with pinch-to-zoom
- iOS: GPU (Metal) rendering, CJK IME, keyboard accessory bar with sticky
  modifiers, touch selection
- Android: libghostty-vt state machine + JNI, dirty-row Canvas rendering
  (system font fallback covers CJK/emoji; bundled Symbols Nerd Font covers
  private-use glyphs), IME text input, hardware keys via ghostty's key
  encoder, keyboard accessory bar with sticky modifiers, touch selection
  with clipboard, inertial scrollback with indicator, cursor blink
- Bring-your-own PTY: the view only renders bytes and reports input/resizes —
  transport and session lifecycle stay on your side

**Platforms:** iOS 16.4+ and Android (arm64-v8a / x86_64; Expo SDK 57).
Web is not supported.

## Install

```sh
npx expo install expo-libghostty
```

The prebuilt `GhosttyKit.xcframework` (~50 MB, iOS) and Android
`libghostty-vt` static libraries are downloaded checksum-pinned by a
`postinstall` script. pnpm blocks dependency build scripts by default —
allow it in `pnpm-workspace.yaml`:

```yaml
allowBuilds:
  expo-libghostty: true
```

## Usage

All byte payloads are base64 strings.

```tsx
import { TerminalView, TerminalViewRef } from 'expo-libghostty';
import { useRef } from 'react';

export function Terminal({ pty }) {
  const terminal = useRef<TerminalViewRef>(null);

  // PTY output -> grid: terminal.current?.write(base64Chunk)
  // PTY exited -> terminal.current?.finish(exitCode)

  return (
    <TerminalView
      ref={terminal}
      style={{ flex: 1 }}
      onInput={({ nativeEvent }) => pty.write(nativeEvent.data)}
      onResize={({ nativeEvent }) => pty.resize(nativeEvent.cols, nativeEvent.rows)}
    />
  );
}
```

`example/` contains a runnable local-echo demo (`pnpm --dir example ios` /
`pnpm --dir example android`).

### API

| `<TerminalView>` prop | Description                                                                                                                         |
| --------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| `fontSize`            | Base font size in density-independent units (default 14, clamped 4–64); pinch-to-zoom steps from it. Android applies changes live (the grid reflows); iOS rebuilds the surface on change, resetting the grid — set it before mounting. |
| `theme`               | Terminal colors: `background`, `foreground`, `cursorColor`, `selectionBackground`, `selectionForeground`, and `palette` (overrides by index, 0–255). Values use ghostty config syntax (hex or X11 names); invalid values are ignored with a warning. Android applies the theme per view; on iOS it is app-wide. |
| `onInput`             | User keyboard/IME input to forward to the PTY. `nativeEvent.data` is base64 bytes; `nativeEvent.text` is the same decoded as UTF-8. |
| `onResize`            | Grid resized (layout, rotation, font change). Forward `nativeEvent.cols` / `nativeEvent.rows` to the PTY.                           |
| `onBell`              | BEL received (0x07).                                                                                                                |
| `onTitleChange`       | Title set via OSC 0/2; `nativeEvent.title`.                                                                                          |
| `onDirectoryChange`   | Working directory reported via OSC 7/9/1337; `nativeEvent.path` is passed through as sent (OSC 7 is a `file://` URI). Android only for now — the upstream iOS in-memory surface does not emit pwd actions. |
| `ref`                 | Imperative handle (`TerminalViewRef`), methods below.                                                                               |

| Ref method         | Description                                            |
| ------------------ | ------------------------------------------------------ |
| `write(base64)`    | Feed base64-encoded PTY output into the terminal grid. |
| `writeText(text)`  | Feed PTY output as UTF-8 text, for string-based wires. |
| `finish(exitCode)` | Mark the underlying PTY as exited.                     |

### Platform parity

The JS contract is identical on both platforms; native behavior differs
where the platforms do:

| Behavior                          | iOS (GhosttyKit)         | Android (libghostty-vt + Canvas)              |
| --------------------------------- | ------------------------ | --------------------------------------------- |
| Rendering                         | Metal                    | Canvas/Skia, dirty-row bitmap patching         |
| CJK / emoji                       | bundled font stack       | system font fallback (Minikin)                 |
| Nerd Font private-use glyphs      | bundled font stack       | bundled Symbols Nerd Font Mono                 |
| IME text input                    | ✅                        | ✅ (commitText; sticky Ctrl/Alt compose chords) |
| Keyboard accessory bar            | ✅ sticky modifiers       | ✅ Esc/Ctrl/Alt/Tab/arrows/nav, sticky Ctrl/Alt |
| Hardware keys (DECCKM/kitty)      | ✅                        | ✅ via `ghostty_key_encoder`                    |
| Touch selection + clipboard       | ✅                        | ✅ long-press word, drag handles, magnifier, floating Copy/Paste/Select all; bracketed paste with unsafe-paste confirm |
| Scrollback                        | ✅                        | ✅ inertial fling, fading indicator, jump-to-bottom chip |
| Cursor blink (DECSCUSR)           | ✅                        | ✅ (holds solid on I/O, honors animations-off)  |
| Pinch-to-zoom font size           | ✅                        | ✅ (same 0.1-scale → ±1 steps, 4–64 bounds)     |
| Theme colors (`theme` prop)       | ✅ app-wide (controller config) | ✅ per view (terminal default colors)     |
| Terminal events (bell/title)      | ✅ (surface delegate)     | ✅ (vt effect callbacks)                        |
| `onDirectoryChange` (OSC 7/9/1337)| ❌ (surface emits no pwd actions) | ✅                                      |

## Vendoring

Expo autolinking cannot consume Swift packages, so the pure-Swift layers of
libghostty-spm (`GhosttyKit`, `GhosttyTerminal`) and `MSDisplayLink` are
vendored under `ios/vendor/` as CocoaPods mirroring the upstream SPM products
(all MIT, licenses included). `vendor-manifest.json` pins the upstream tags
and the XCFramework checksum; `pnpm sync-vendor` re-syncs. A daily
`vendor-watch` workflow compares every pin against upstream and keeps a
drift issue open while any is behind. Once React Native supports SPM
dependencies this layer disappears in favor of the upstream package.

On Android, `android/vendor/` holds per-ABI `libghostty-vt.a` static
libraries plus the matching C headers, cross-compiled from a pinned ghostty
commit (Zig 0.15.2 + NDK r27); `vendor-manifest.json` pins the tarball
checksum. A thin JNI shim (`android/src/main/cpp/ghostty_jni.cpp`) exposes
the terminal + render-state loop to Kotlin, which paints the grid with
Canvas/Skia (`GhosttyTerminalView.kt`).

## Troubleshooting

**`pod install` fails on a missing `GhosttyKit.xcframework`, or the Android
build can't find `libghostty-vt.a`.** The `postinstall` script that fetches
them didn't run — pnpm blocks dependency build scripts unless
`expo-libghostty` is in `allowBuilds:` (see Install), and `--ignore-scripts`
skips it under any package manager. Fix the config and reinstall, or run
`node node_modules/expo-libghostty/scripts/download-xcframework.mjs` and
`…/download-android-libs.mjs` by hand.

**Install fails with `download failed: HTTP …` or `checksum mismatch`.**
The binaries come from GitHub release assets, so the install machine needs
access to `github.com` (and `objects.githubusercontent.com`). A checksum
mismatch means the download was corrupted or rewritten in transit
(intercepting proxy) — the script fails loudly rather than installing an
unverified binary. Both scripts skip the download when the files are already
present, so air-gapped setups can pre-seed `ios/vendor/Frameworks/` and
`android/vendor/{arm64-v8a,x86_64}/`.

**Android crashes with `UnsatisfiedLinkError`.** Only `arm64-v8a` and
`x86_64` ship (see Platforms); 32-bit ABIs are not supported.

**The terminal stays black in release builds (< 0.8.1).** `write()` issued
from a mount effect used to race native view registration and was silently
dropped. Fixed in 0.8.1, where imperative calls queue until the view reports
its first grid size — upgrade.

**iOS: the grid resets when `fontSize` changes after mount.** A font-size
change rebuilds the terminal surface on iOS; set it before mounting. Android
applies it live (the grid reflows in place).

**Contributing: the example iOS build dies with `Could not resolve package
dependencies`.** Expo SDK 57's `expo-modules-jsi` is a
`swift-tools-version: 6.2` package — building the example needs the
Xcode 26 toolchain (CI uses `macos-26`).

## License

MIT © [ArcBox, Inc.](./LICENSE) Vendored components (libghostty,
libghostty-spm, MSDisplayLink) are MIT-licensed by their respective authors;
their licenses ship alongside the vendored sources.
