# expo-libghostty

Ghostty terminal view for Expo / React Native, powered by
[libghostty](https://ghostty.org) via
[Lakr233/libghostty-spm](https://github.com/Lakr233/libghostty-spm).

- Real VT parsing and GPU (Metal) rendering from ghostty's prebuilt core
- Full iOS text input: CJK IME, keyboard accessory bar with sticky modifiers,
  touch selection, pinch-to-zoom font size
- Bring-your-own PTY: the view only renders bytes and reports input/resizes —
  transport and session lifecycle stay on your side

**Platforms:** iOS 16.4+ (Expo SDK 57). Android and web are not supported yet.

## Install

```sh
npx expo install expo-libghostty
```

The prebuilt `GhosttyKit.xcframework` (~50 MB) is downloaded checksum-pinned
by a `postinstall` script. pnpm blocks dependency build scripts by default —
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

`example/` contains a runnable local-echo demo (`pnpm --dir example ios`).

## Vendoring

Expo autolinking cannot consume Swift packages, so the pure-Swift layers of
libghostty-spm (`GhosttyKit`, `GhosttyTerminal`) and `MSDisplayLink` are
vendored under `ios/vendor/` as CocoaPods mirroring the upstream SPM products
(all MIT, licenses included). `vendor-manifest.json` pins the upstream tags
and the XCFramework checksum; `pnpm sync-vendor` re-syncs. Once React Native
supports SPM dependencies this layer disappears in favor of the upstream
package.
