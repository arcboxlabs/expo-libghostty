import { requireNativeView } from 'expo';
import * as React from 'react';
import { useImperativeHandle, useRef } from 'react';

import type { TerminalViewProps, TerminalViewRef } from './ExpoLibghostty.types';

const NativeView: React.ComponentType<TerminalViewProps> = requireNativeView('ExpoLibghostty');

export default function TerminalView({ ref, onResize, ...props }: TerminalViewProps) {
  const native = useRef<TerminalViewRef>(null);
  // expo-modules-core registers the native view after the first commit, so
  // imperative calls issued on mount (e.g. write() in an effect) would reject
  // with "unable to find view". Queue them until the view reports its first
  // grid size — the earliest signal that the native side is dispatchable.
  const ready = useRef(false);
  const pending = useRef<(() => void)[]>([]);

  useImperativeHandle(ref, () => {
    const whenReady =
      <A extends unknown[]>(call: (...args: A) => Promise<void>) =>
      (...args: A) => {
        if (ready.current) return call(...args);
        return new Promise<void>((resolve, reject) => {
          pending.current.push(() => call(...args).then(resolve, reject));
        });
      };
    return {
      write: whenReady((base64: string) => native.current!.write(base64)),
      writeText: whenReady((text: string) => native.current!.writeText(text)),
      finish: whenReady((exitCode: number) => native.current!.finish(exitCode)),
    };
  }, []);

  return (
    <NativeView
      {...props}
      ref={native}
      onResize={(event) => {
        if (!ready.current) {
          ready.current = true;
          for (const task of pending.current.splice(0)) task();
        }
        onResize?.(event);
      }}
    />
  );
}
