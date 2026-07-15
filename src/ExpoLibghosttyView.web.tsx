import type { TerminalViewProps } from './ExpoLibghostty.types';

// TerminalView is not available on the web platform.
export default function TerminalView(_props: TerminalViewProps) {
  throw new Error('TerminalView is not available on the web platform.');
}
