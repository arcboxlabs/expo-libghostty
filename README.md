# expo-libghostty

Ghostty terminal view for Expo / React Native, powered by
[libghostty](https://ghostty.org) — on iOS via
[Lakr233/libghostty-spm](https://github.com/Lakr233/libghostty-spm), on
Android via [libghostty-vt](https://github.com/ghostty-org/ghostty) with a
Canvas renderer.

- Real VT parsing from ghostty's core on both platforms
- iOS: GPU (Metal) rendering, CJK IME, keyboard accessory bar with sticky
  modifiers, touch selection, pinch-to-zoom font size
- Android: libghostty-vt state machine + JNI, dirty-row Canvas rendering
  (system font fallback covers CJK/emoji), IME text input, hardware keys via
  ghostty's key encoder, scrollback gestures
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
  - expo-libghostty
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

## Vendoring

Expo autolinking cannot consume Swift packages, so the pure-Swift layers of
libghostty-spm (`GhosttyKit`, `GhosttyTerminal`) and `MSDisplayLink` are
vendored under `ios/vendor/` as CocoaPods mirroring the upstream SPM products
(all MIT, licenses included). `vendor-manifest.json` pins the upstream tags
and the XCFramework checksum; `pnpm sync-vendor` re-syncs. Once React Native
supports SPM dependencies this layer disappears in favor of the upstream
package.

On Android, `android/vendor/` holds per-ABI `libghostty-vt.a` static
libraries plus the matching C headers, cross-compiled from a pinned ghostty
commit (Zig 0.15.2 + NDK r27); `vendor-manifest.json` pins the tarball
checksum. A thin JNI shim (`android/src/main/cpp/ghostty_jni.cpp`) exposes
the terminal + render-state loop to Kotlin, which paints the grid with
Canvas/Skia (`GhosttyTerminalView.kt`).
