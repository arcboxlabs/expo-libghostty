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

export type TerminalViewRef = {
  /** Feed base64-encoded PTY output into the terminal grid. */
  write(base64: string): Promise<void>;
  /** Feed PTY output as UTF-8 text, for string-based wires. */
  writeText(text: string): Promise<void>;
  /** Mark the underlying PTY as exited. */
  finish(exitCode: number): Promise<void>;
};

export type TerminalViewProps = {
  /**
   * Base font size in density-independent units (default 14, clamped to
   * 4–64); pinch-to-zoom steps from this value. Android applies changes
   * live (the grid reflows in place); on iOS a change after mount rebuilds
   * the terminal surface, resetting the grid — set it before mounting.
   */
  fontSize?: number;
  /** User keyboard/IME input to forward to the PTY. */
  onInput?: (event: { nativeEvent: TerminalInputEvent }) => void;
  /** Grid resized (layout, rotation, font change); forward to the PTY. */
  onResize?: (event: { nativeEvent: TerminalResizeEvent }) => void;
  ref?: Ref<TerminalViewRef>;
  style?: StyleProp<ViewStyle>;
};
