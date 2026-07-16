package expo.modules.libghostty

import java.nio.ByteBuffer

/**
 * JNI bindings for libghostty-vt (see src/main/cpp/ghostty_jni.cpp).
 *
 * All functions must be called from the main thread; the native side keeps
 * per-handle state without locking.
 */
internal object GhosttyVt {
  init {
    System.loadLibrary("expolibghostty")
  }

  /** Create a terminal session. Returns 0 on failure. */
  external fun nativeCreate(cols: Int, rows: Int, maxScrollback: Long): Long

  external fun nativeFree(handle: Long)

  /**
   * Feed PTY output bytes through the VT parser. Returns query-response bytes
   * the terminal wants written back to the PTY (DSR etc.), or null.
   */
  external fun nativeWrite(handle: Long, data: ByteArray): ByteArray?

  external fun nativeResize(handle: Long, cols: Int, rows: Int, cellWidthPx: Int, cellHeightPx: Int)

  /** Scroll the viewport by [deltaRows]; negative is up (into scrollback). */
  external fun nativeScroll(handle: Long, deltaRows: Int)

  /**
   * Update the render state and flatten dirty rows into [buffer] (direct,
   * little-endian; layout documented in ghostty_jni.cpp). Returns the number
   * of row records written, or -1 on error/too-small buffer.
   */
  external fun nativeSnapshot(handle: Long, buffer: ByteBuffer): Int

  /**
   * Encode a key event ([action]: 0 release / 1 press / 2 repeat) into the
   * escape-sequence bytes to send to the PTY, or null when the key encodes
   * to nothing under the terminal's active modes.
   */
  external fun nativeEncodeKey(
    handle: Long,
    keyCode: Int,
    action: Int,
    metaState: Int,
    unshiftedCodepoint: Int,
    utf8: ByteArray?
  ): ByteArray?
}
