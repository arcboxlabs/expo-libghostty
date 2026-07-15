import type { Ref } from 'react';
import type { StyleProp, ViewStyle } from 'react-native';

export type TerminalInputEvent = {
  /** Base64-encoded bytes typed by the user; forward to the PTY. */
  data: string;
};

export type TerminalResizeEvent = {
  /** New grid size in cells. */
  cols: number;
  rows: number;
};

export type TerminalViewRef = {
  /** Feed base64-encoded PTY output into the terminal grid. */
  write(base64: string): Promise<void>;
  /** Mark the underlying PTY as exited. */
  finish(exitCode: number): Promise<void>;
};

export type TerminalViewProps = {
  /** User keyboard/IME input to forward to the PTY. */
  onInput?: (event: { nativeEvent: TerminalInputEvent }) => void;
  /** Grid resized (layout, rotation, font change); forward to the PTY. */
  onResize?: (event: { nativeEvent: TerminalResizeEvent }) => void;
  ref?: Ref<TerminalViewRef>;
  style?: StyleProp<ViewStyle>;
};
