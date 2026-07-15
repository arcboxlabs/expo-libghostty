import { requireNativeView } from 'expo';
import * as React from 'react';

import { ExpoLibghosttyViewProps } from './ExpoLibghostty.types';

const NativeView: React.ComponentType<ExpoLibghosttyViewProps> =
  requireNativeView('ExpoLibghostty');

export default function ExpoLibghosttyView(props: ExpoLibghosttyViewProps) {
  return <NativeView {...props} />;
}
