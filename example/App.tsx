import { TerminalView, TerminalViewRef } from 'expo-libghostty';
import { useEffect, useRef } from 'react';
import { View } from 'react-native';

// "\x1B[1;32mexpo-libghostty\x1B[0m — local echo demo\r\n
//  No PTY attached; typed bytes are echoed back.\r\n\r\n$ "
const BANNER =
  'G1sxOzMybWV4cG8tbGliZ2hvc3R0eRtbMG0g4oCUIGxvY2FsIGVjaG8gZGVtbw0KTm8gUFRZIGF0dGFjaGVkOyB0eXBlZCBieXRlcyBhcmUgZWNob2VkIGJhY2suDQoNCiQg';

// Local loopback: user input is echoed straight back into the grid,
// standing in for a real PTY behind the LinkCode wire.
export default function App() {
  const terminal = useRef<TerminalViewRef>(null);

  useEffect(() => {
    terminal.current?.write(BANNER);
    // Exercise the effect events: OSC 2 title, OSC 1337 pwd, BEL.
    terminal.current?.writeText(
      '\x1B]2;expo-libghostty demo\x07\x1B]1337;CurrentDir=/tmp\x07\x07'
    );
  }, []);

  return (
    <View style={{ flex: 1, backgroundColor: '#000' }}>
      <TerminalView
        ref={terminal}
        style={{ flex: 1 }}
        onInput={({ nativeEvent }) => {
          terminal.current?.write(nativeEvent.data);
        }}
        onResize={({ nativeEvent }) => {
          console.log(`resize: ${nativeEvent.cols}x${nativeEvent.rows}`);
        }}
        onBell={() => console.log('bell')}
        onTitleChange={({ nativeEvent }) => console.log(`title: ${nativeEvent.title}`)}
        onDirectoryChange={({ nativeEvent }) => console.log(`pwd: ${nativeEvent.path}`)}
      />
    </View>
  );
}
