import { requireNativeView } from 'expo';
import * as React from 'react';

import type { TerminalViewProps } from './ExpoLibghostty.types';

const NativeView: React.ComponentType<TerminalViewProps> = requireNativeView('ExpoLibghostty');

export default function TerminalView(props: TerminalViewProps) {
  return <NativeView {...props} />;
}
