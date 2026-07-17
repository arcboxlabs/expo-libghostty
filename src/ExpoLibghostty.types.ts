import type { Ref } from 'react';
import type { StyleProp, ViewStyle } from 'react-native';

export type TerminalInputEvent = {
  /** Base64-encoded bytes typed by the user; forward to the PTY. */
  data: string;
  /** The same bytes decoded as UTF-8, for string-based wires. */
  text: string;
};

export type TerminalResizeEvent = {
  /** New grid size in cells. */
  cols: number;
  rows: number;
};

export type TerminalTitleEvent = {
  /** Title set by the running program via OSC 0/2. */
  title: string;
};

export type TerminalDirectoryEvent = {
  /**
   * Working directory reported via OSC 7 / OSC 9;9 / OSC 1337 CurrentDir,
   * passed through as sent — OSC 7 delivers a `file://` URI, the others a
   * bare path.
   */
  path: string;
};

export type TerminalViewRef = {
  /** Feed base64-encoded PTY output into the terminal grid. */
  write(base64: string): Promise<void>;
  /** Feed PTY output as UTF-8 text, for string-based wires. */
  writeText(text: string): Promise<void>;
  /** Mark the underlying PTY as exited. */
  finish(exitCode: number): Promise<void>;
};

export type TerminalTheme = {
  /**
   * Colors accept ghostty config syntax: 3/6-digit hex (with or without a
   * leading `#`) or X11 color names. Invalid values are ignored with a
   * native warning, matching ghostty's handling of bad config values.
   */
  background?: string;
  foreground?: string;
  cursorColor?: string;
  /** When unset, selected cells render with the classic fg/bg swap. */
  selectionBackground?: string;
  selectionForeground?: string;
  /**
   * Palette overrides by index (0–255) on top of ghostty's default
   * 256-color palette — pass the first 16 entries to retheme the ANSI
   * colors. Sparse entries (undefined/null) keep the default.
   */
  palette?: (string | null)[];
};

export type TerminalViewProps = {
  /**
   * Base font size in density-independent units (default 14, clamped to
   * 4–64); pinch-to-zoom steps from this value. Android applies changes
   * live (the grid reflows in place); on iOS a change after mount rebuilds
   * the terminal surface, resetting the grid — set it before mounting.
   */
  fontSize?: number;
  /**
   * Theme colors. Android applies them per view; on iOS the theme is
   * app-wide (all terminal views share the controller's config). Omit or
   * pass undefined fields to keep ghostty's defaults.
   */
  theme?: TerminalTheme;
  /** User keyboard/IME input to forward to the PTY. */
  onInput?: (event: { nativeEvent: TerminalInputEvent }) => void;
  /** Grid resized (layout, rotation, font change); forward to the PTY. */
  onResize?: (event: { nativeEvent: TerminalResizeEvent }) => void;
  /** BEL received (0x07). */
  onBell?: (event: { nativeEvent: object }) => void;
  /** Terminal title changed via OSC 0/2. */
  onTitleChange?: (event: { nativeEvent: TerminalTitleEvent }) => void;
  /**
   * Working directory changed via OSC 7/9/1337. Android only for now: the
   * upstream iOS in-memory surface does not emit pwd actions.
   */
  onDirectoryChange?: (event: { nativeEvent: TerminalDirectoryEvent }) => void;
  ref?: Ref<TerminalViewRef>;
  style?: StyleProp<ViewStyle>;
};
