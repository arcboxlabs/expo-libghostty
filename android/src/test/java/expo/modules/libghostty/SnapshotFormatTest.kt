package expo.modules.libghostty

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotFormatTest {
  // Mirrors the writer in ghostty_jni.cpp: 13×i32, little-endian.
  private fun headerBuffer(vararg fields: Int): ByteBuffer {
    val buf = ByteBuffer.allocate(HEADER_BYTES + 8).order(ByteOrder.LITTLE_ENDIAN)
    for (field in fields) buf.putInt(field)
    buf.position(0)
    return buf
  }

  @Test
  fun `header size matches the declared v2 layout`() {
    assertEquals(13 * Int.SIZE_BYTES, HEADER_BYTES)
  }

  @Test
  fun `read decodes every header field and consumes exactly the header`() {
    val buf = headerBuffer(
      2, // version
      DIRTY_FULL,
      80, // cols
      24, // rows
      0xFF1E1E2E.toInt(), // background
      0xFFCDD6F4.toInt(), // foreground
      12, // cursorX
      3, // cursorY
      CURSOR_STYLE_BLOCK,
      1, // cursorVisible
      24, // rowCount
      1, // cursorBlinks
      0xFFF5C2E7.toInt(), // cursorColor
    )
    val header = SnapshotHeader.read(buf)
    assertEquals(2, header.version)
    assertEquals(DIRTY_FULL, header.dirtyKind)
    assertEquals(80, header.cols)
    assertEquals(24, header.rows)
    assertEquals(0xFF1E1E2E.toInt(), header.background)
    assertEquals(0xFFCDD6F4.toInt(), header.foreground)
    assertEquals(12, header.cursorX)
    assertEquals(3, header.cursorY)
    assertEquals(CURSOR_STYLE_BLOCK, header.cursorStyle)
    assertTrue(header.cursorVisible)
    assertEquals(24, header.rowCount)
    assertTrue(header.cursorBlinks)
    assertEquals(0xFFF5C2E7.toInt(), header.cursorColor)
    assertEquals(HEADER_BYTES, buf.position())
  }

  @Test
  fun `zero int fields decode as false and unconfigured cursor color`() {
    val buf = headerBuffer(2, 0, 80, 24, 0, 0, 0, 0, CURSOR_STYLE_BAR, 0, 0, 0, 0)
    val header = SnapshotHeader.read(buf)
    assertFalse(header.cursorVisible)
    assertFalse(header.cursorBlinks)
    assertEquals(0, header.cursorColor)
  }
}

class ResolveCellColorsTest {
  private val fg = 0xFFAA0000.toInt()
  private val bg = 0xFF00AA00.toInt()
  private val defaultFg = 0xFFEEEEEE.toInt()
  private val defaultBg = 0xFF111111.toInt()
  private val themeFg = 0xFF101010.toInt()
  private val themeBg = 0xFFF0F0F0.toInt()

  private fun resolve(
    flags: Int = 0,
    selected: Boolean = false,
    selectionFg: Int? = null,
    selectionBg: Int? = null,
  ) = resolveCellColors(fg, bg, flags, selected, defaultFg, defaultBg, selectionFg, selectionBg)

  private fun assertColors(expectedFg: Int, expectedBg: Int, packed: Long) {
    assertEquals(expectedFg, packedFg(packed))
    assertEquals(expectedBg, packedBg(packed))
  }

  @Test
  fun `explicit colors pass through`() {
    assertColors(fg, bg, resolve())
  }

  @Test
  fun `default flags substitute the terminal defaults`() {
    assertColors(defaultFg, bg, resolve(flags = FLAG_FG_DEFAULT))
    assertColors(fg, defaultBg, resolve(flags = FLAG_BG_NONE))
    assertColors(defaultFg, defaultBg, resolve(flags = FLAG_FG_DEFAULT or FLAG_BG_NONE))
  }

  @Test
  fun `inverse swaps the resolved colors`() {
    assertColors(bg, fg, resolve(flags = FLAG_INVERSE))
    // Defaults resolve first, then swap.
    assertColors(defaultBg, defaultFg, resolve(flags = FLAG_FG_DEFAULT or FLAG_BG_NONE or FLAG_INVERSE))
  }

  @Test
  fun `selection without a theme swaps like inverse`() {
    assertColors(bg, fg, resolve(selected = true))
  }

  @Test
  fun `selection on an inverse cell swaps back to the original colors`() {
    assertColors(fg, bg, resolve(flags = FLAG_INVERSE, selected = true))
  }

  @Test
  fun `themed selection background keeps the cell foreground`() {
    assertColors(fg, themeBg, resolve(selected = true, selectionBg = themeBg))
  }

  @Test
  fun `themed selection foreground applies only with a themed background`() {
    assertColors(themeFg, themeBg, resolve(selected = true, selectionFg = themeFg, selectionBg = themeBg))
    // Foreground alone falls back to the classic swap.
    assertColors(bg, fg, resolve(selected = true, selectionFg = themeFg))
  }

  @Test
  fun `themed selection overrides an inverse cell`() {
    assertColors(themeFg, themeBg, resolve(flags = FLAG_INVERSE, selected = true, selectionFg = themeFg, selectionBg = themeBg))
  }

  @Test
  fun `unselected cells ignore the selection theme`() {
    assertColors(fg, bg, resolve(selectionFg = themeFg, selectionBg = themeBg))
  }

  @Test
  fun `packing round-trips negative ARGB ints`() {
    val packed = resolveCellColors(
      0x80000000.toInt(), 0xFFFFFFFF.toInt(), 0, false,
      defaultFg, defaultBg, null, null
    )
    assertColors(0x80000000.toInt(), 0xFFFFFFFF.toInt(), packed)
  }
}
