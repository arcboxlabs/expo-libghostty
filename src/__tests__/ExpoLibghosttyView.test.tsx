import * as React from 'react';
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { TerminalResizeEvent, TerminalViewRef } from '../ExpoLibghostty.types';
import TerminalView from '../ExpoLibghosttyView';

(globalThis as Record<string, unknown>).IS_REACT_ACT_ENVIRONMENT = true;

const native = vi.hoisted(() => ({
  write: vi.fn<(base64: string) => Promise<void>>(),
  writeText: vi.fn<(text: string) => Promise<void>>(),
  finish: vi.fn<(exitCode: number) => Promise<void>>(),
  onResize: undefined as ((event: { nativeEvent: TerminalResizeEvent }) => void) | undefined,
}));

vi.mock('expo', async () => {
  const { useImperativeHandle } = await import('react');
  return {
    requireNativeView: () =>
      function MockNativeView(props: {
        ref?: React.Ref<TerminalViewRef>;
        onResize?: (event: { nativeEvent: TerminalResizeEvent }) => void;
      }) {
        native.onResize = props.onResize;
        useImperativeHandle(props.ref, () => ({
          write: native.write,
          writeText: native.writeText,
          finish: native.finish,
        }));
        return null;
      },
  };
});

function render(props: React.ComponentProps<typeof TerminalView> = {}) {
  const ref = React.createRef<TerminalViewRef>();
  const root = createRoot(document.createElement('div'));
  act(() => {
    root.render(<TerminalView ref={ref} {...props} />);
  });
  return { ref, root };
}

function fireResize(cols = 80, rows = 24) {
  act(() => {
    native.onResize?.({ nativeEvent: { cols, rows } });
  });
}

const roots: { unmount(): void }[] = [];

beforeEach(() => {
  vi.clearAllMocks();
  native.onResize = undefined;
  native.write.mockResolvedValue();
  native.writeText.mockResolvedValue();
  native.finish.mockResolvedValue();
});

afterEach(() => {
  act(() => {
    for (const root of roots.splice(0)) root.unmount();
  });
});

describe('TerminalView imperative queue', () => {
  it('holds imperative calls until the first resize, then flushes in order', async () => {
    const { ref, root } = render();
    roots.push(root);

    const write = ref.current!.write('AAAA');
    const writeText = ref.current!.writeText('hello');
    const finish = ref.current!.finish(0);
    expect(native.write).not.toHaveBeenCalled();
    expect(native.writeText).not.toHaveBeenCalled();
    expect(native.finish).not.toHaveBeenCalled();

    fireResize();
    await Promise.all([write, writeText, finish]);
    expect(native.write).toHaveBeenCalledWith('AAAA');
    expect(native.writeText).toHaveBeenCalledWith('hello');
    expect(native.finish).toHaveBeenCalledWith(0);
    expect(native.write.mock.invocationCallOrder[0]).toBeLessThan(
      native.writeText.mock.invocationCallOrder[0]
    );
    expect(native.writeText.mock.invocationCallOrder[0]).toBeLessThan(
      native.finish.mock.invocationCallOrder[0]
    );
  });

  it('dispatches directly once ready', async () => {
    const { ref, root } = render();
    roots.push(root);
    fireResize();

    await ref.current!.write('BBBB');
    expect(native.write).toHaveBeenCalledTimes(1);
    expect(native.write).toHaveBeenCalledWith('BBBB');
  });

  it('propagates rejections from flushed calls', async () => {
    const { ref, root } = render();
    roots.push(root);
    const failure = new Error('native write failed');
    native.write.mockRejectedValueOnce(failure);

    const pending = ref.current!.write('CCCC');
    fireResize();
    await expect(pending).rejects.toBe(failure);
  });

  it('still forwards resize events to the consumer handler', () => {
    const onResize = vi.fn();
    const { root } = render({ onResize });
    roots.push(root);

    fireResize(120, 40);
    expect(onResize).toHaveBeenCalledWith({ nativeEvent: { cols: 120, rows: 40 } });
  });

  it('does not replay the queue on later resizes', async () => {
    const { ref, root } = render();
    roots.push(root);

    const pending = ref.current!.write('DDDD');
    fireResize();
    await pending;
    fireResize(100, 30);
    expect(native.write).toHaveBeenCalledTimes(1);
  });
});
