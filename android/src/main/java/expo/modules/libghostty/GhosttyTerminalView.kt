package expo.modules.libghostty

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.provider.Settings
import android.text.InputType
import android.view.Choreographer
import android.view.GestureDetector
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Canvas-backed ghostty terminal grid.
 *
 * Owns a libghostty-vt session via [GhosttyVt]. PTY output is fed in with
 * [write]; user input (IME text, key escape sequences, VT query responses)
 * comes back through [onInputBytes]. Rendering pulls flattened dirty-row
 * snapshots from JNI once per Choreographer frame and patches them onto a
 * grid-sized bitmap; [onDraw] blits the bitmap and overlays the cursor.
 */
internal class GhosttyTerminalView(context: Context) : View(context) {
  var onInputBytes: ((ByteArray) -> Unit)? = null
  var onGridResize: ((cols: Int, rows: Int) -> Unit)? = null

  private var handle: Long = GhosttyVt.nativeCreate(INITIAL_COLS, INITIAL_ROWS, MAX_SCROLLBACK)
  private var finished = false

  // ── Cell metrics ──
  private val textPaint = newTextPaint(Typeface.MONOSPACE)
  private val boldPaint = newTextPaint(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD))
  private val italicPaint = newTextPaint(Typeface.create(Typeface.MONOSPACE, Typeface.ITALIC))
  private val boldItalicPaint =
    newTextPaint(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD_ITALIC))

  // Nerd Font private-use glyphs (powerline, devicons, …) are absent from the
  // system fallback chain; route those cells to the bundled symbols-only font.
  private val symbolsPaint: Paint? = try {
    newTextPaint(Typeface.createFromAsset(context.assets, SYMBOLS_FONT_ASSET))
  } catch (e: RuntimeException) {
    null
  }
  private val fillPaint = Paint()
  private val cursorPaint = Paint()
  private val cellWidth = textPaint.measureText("M")
  private val cellHeight = ceil(
    textPaint.fontMetrics.descent - textPaint.fontMetrics.ascent
  ).toInt()
  private val baseline = -textPaint.fontMetrics.ascent
  private val underlineThickness = max(1f, resources.displayMetrics.density)

  // ── Grid state ──
  private var cols = INITIAL_COLS
  private var rows = INITIAL_ROWS
  private var bitmap: Bitmap? = null
  private var bitmapCanvas: Canvas? = null
  private var snapshotBuf: ByteBuffer? = null
  private var rowChars = CharArray(0)
  private var cellFg = IntArray(0)
  private var cellBg = IntArray(0)
  private var cellTextOff = IntArray(0)
  private var cellTextLen = IntArray(0)
  private var cellFlags = IntArray(0)

  private var defaultBg = 0xFF000000.toInt()
  private var defaultFg = 0xFFFFFFFF.toInt()
  private var cursorX = -1
  private var cursorY = -1
  private var cursorStyle = CURSOR_STYLE_BLOCK
  private var cursorVisible = false
  private var cursorBlinks = false

  // The cursor is an onDraw overlay, so blinking only re-blits the cached
  // bitmap; the grid is untouched.
  private var blinkPhaseOn = true
  private var blinkScheduled = false
  private val blinkRunnable = object : Runnable {
    override fun run() {
      if (!blinkScheduled) return
      blinkPhaseOn = !blinkPhaseOn
      invalidate()
      postDelayed(this, BLINK_INTERVAL_MS)
    }
  }

  private var framePending = false
  private val frameCallback = Choreographer.FrameCallback {
    framePending = false
    renderFrame()
  }

  private var scrollAccum = 0f
  private val gestureDetector = GestureDetector(
    context,
    object : GestureDetector.SimpleOnGestureListener() {
      override fun onDown(e: MotionEvent) = true

      override fun onSingleTapUp(e: MotionEvent): Boolean {
        requestFocus()
        context.getSystemService(InputMethodManager::class.java)
          ?.showSoftInput(this@GhosttyTerminalView, 0)
        return true
      }

      override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
      ): Boolean {
        if (handle == 0L) return false
        scrollAccum += distanceY
        val deltaRows = (scrollAccum / cellHeight).toInt()
        if (deltaRows != 0) {
          scrollAccum -= deltaRows * cellHeight
          GhosttyVt.nativeScroll(handle, deltaRows)
          scheduleFrame()
        }
        return true
      }
    }
  )

  init {
    isFocusable = true
    isFocusableInTouchMode = true
  }

  private fun newTextPaint(typeface: Typeface) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.typeface = typeface
    textSize = FONT_SIZE_DP * resources.displayMetrics.density
  }

  // ── Session I/O ──

  /** Feed PTY output into the grid. */
  fun write(data: ByteArray) {
    if (handle == 0L) return
    val response = GhosttyVt.nativeWrite(handle, data)
    if (response != null && response.isNotEmpty() && !finished) {
      onInputBytes?.invoke(response)
    }
    holdBlinkSolid()
    scheduleFrame()
  }

  /** Mark the underlying PTY as exited: stop forwarding input. */
  fun finish(@Suppress("UNUSED_PARAMETER") exitCode: Int) {
    finished = true
  }

  fun destroy() {
    if (framePending) {
      Choreographer.getInstance().removeFrameCallback(frameCallback)
      framePending = false
    }
    blinkScheduled = false
    removeCallbacks(blinkRunnable)
    if (handle != 0L) {
      GhosttyVt.nativeFree(handle)
      handle = 0
    }
  }

  private fun sendBytes(bytes: ByteArray) {
    if (!finished && bytes.isNotEmpty()) {
      holdBlinkSolid()
      onInputBytes?.invoke(bytes)
    }
  }

  private fun sendText(text: CharSequence) {
    if (text.isNotEmpty()) sendBytes(text.toString().toByteArray(Charsets.UTF_8))
  }

  // ── Layout / resize ──

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    if (w <= 0 || h <= 0 || handle == 0L) return
    val newCols = max(2, floor(w / cellWidth).toInt())
    val newRows = max(2, h / cellHeight)
    val changed = newCols != cols || newRows != rows
    cols = newCols
    rows = newRows
    if (changed || bitmap == null) {
      GhosttyVt.nativeResize(handle, cols, rows, cellWidth.roundToInt(), cellHeight)
      allocateGridBuffers()
      onGridResize?.invoke(cols, rows)
    }
    scheduleFrame()
  }

  private fun allocateGridBuffers() {
    val widthPx = max(1, ceil(cols * cellWidth).toInt())
    val heightPx = max(1, rows * cellHeight)
    bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).also {
      bitmapCanvas = Canvas(it)
      bitmapCanvas?.drawColor(defaultBg)
    }
    val capacity = HEADER_BYTES +
      rows * (ROW_HEADER_BYTES + cols * CELL_RECORD_BYTES + cols * MAX_CELL_TEXT_UNITS * 2 + 4)
    snapshotBuf = ByteBuffer.allocateDirect(capacity).order(ByteOrder.LITTLE_ENDIAN)
    rowChars = CharArray(cols * MAX_CELL_TEXT_UNITS)
    cellFg = IntArray(cols)
    cellBg = IntArray(cols)
    cellTextOff = IntArray(cols)
    cellTextLen = IntArray(cols)
    cellFlags = IntArray(cols)
  }

  // ── Rendering ──

  private fun scheduleFrame() {
    if (framePending) return
    framePending = true
    Choreographer.getInstance().postFrameCallback(frameCallback)
  }

  private fun renderFrame() {
    val buf = snapshotBuf ?: return
    val canvas = bitmapCanvas ?: return
    if (handle == 0L) return

    val rowCount = GhosttyVt.nativeSnapshot(handle, buf)
    if (rowCount < 0) return

    buf.position(0)
    buf.int // version
    val dirtyKind = buf.int
    val snapCols = buf.int
    buf.int // rows
    defaultBg = buf.int
    defaultFg = buf.int
    cursorX = buf.int
    cursorY = buf.int
    cursorStyle = buf.int
    cursorVisible = buf.int != 0
    buf.int // row count (== rowCount)
    cursorBlinks = buf.int != 0
    syncBlinkTimer()

    if (dirtyKind == DIRTY_FULL) canvas.drawColor(defaultBg)
    repeat(rowCount) { drawRowRecord(buf, canvas, snapCols) }
    invalidate()
  }

  // ── Cursor blink ──

  private fun wantsBlink(): Boolean {
    return cursorBlinks && cursorVisible && isAttachedToWindow && hasWindowFocus() &&
      Settings.Global.getFloat(
        context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
      ) != 0f
  }

  private fun syncBlinkTimer() {
    val wants = wantsBlink()
    if (wants && !blinkScheduled) {
      blinkScheduled = true
      postDelayed(blinkRunnable, BLINK_INTERVAL_MS)
    } else if (!wants && blinkScheduled) {
      blinkScheduled = false
      removeCallbacks(blinkRunnable)
      if (!blinkPhaseOn) {
        blinkPhaseOn = true
        invalidate()
      }
    }
  }

  /** Show a solid cursor and restart the blink interval (any input/output). */
  private fun holdBlinkSolid() {
    if (!blinkPhaseOn) {
      blinkPhaseOn = true
      invalidate()
    }
    if (blinkScheduled) {
      removeCallbacks(blinkRunnable)
      postDelayed(blinkRunnable, BLINK_INTERVAL_MS)
    }
  }

  override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
    super.onWindowFocusChanged(hasWindowFocus)
    syncBlinkTimer()
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    syncBlinkTimer()
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    syncBlinkTimer()
  }

  private fun drawRowRecord(buf: ByteBuffer, canvas: Canvas, snapCols: Int) {
    val rowIndex = buf.int
    val selStart = buf.int
    val selEnd = buf.int
    val textUnits = buf.int
    val cellsPos = buf.position()
    val textPos = cellsPos + snapCols * CELL_RECORD_BYTES

    buf.position(textPos)
    if (textUnits > rowChars.size) rowChars = CharArray(textUnits)
    for (i in 0 until textUnits) rowChars[i] = buf.char
    val nextRecord = textPos + textUnits * 2 + if (textUnits % 2 != 0) 2 else 0

    buf.position(cellsPos)
    val colCount = minOf(snapCols, cellFg.size)
    for (col in 0 until snapCols) {
      val fg = buf.int
      val bg = buf.int
      val textOff = buf.short.toInt() and 0xFFFF
      val textLen = buf.short.toInt() and 0xFFFF
      val flags = buf.short.toInt() and 0xFFFF
      buf.short // pad
      if (col >= colCount) continue
      val selected = selStart >= 0 && col >= selStart && col <= selEnd
      val swap = (flags and FLAG_INVERSE != 0) != selected
      var effFg = if (flags and FLAG_FG_DEFAULT != 0) defaultFg else fg
      var effBg = if (flags and FLAG_BG_NONE != 0) defaultBg else bg
      if (swap) {
        val tmp = effFg
        effFg = effBg
        effBg = tmp
      }
      cellFg[col] = effFg
      cellBg[col] = effBg
      cellTextOff[col] = textOff
      cellTextLen[col] = textLen
      cellFlags[col] = flags
    }
    buf.position(nextRecord)

    val top = (rowIndex * cellHeight).toFloat()
    val bottom = top + cellHeight

    // Background pass: row clear, then per-cell backgrounds.
    fillPaint.color = defaultBg
    canvas.drawRect(0f, top, canvas.width.toFloat(), bottom, fillPaint)
    for (col in 0 until colCount) {
      val bg = cellBg[col]
      if (bg == defaultBg) continue
      val width = if (cellFlags[col] and FLAG_WIDE != 0) cellWidth * 2 else cellWidth
      val x = col * cellWidth
      fillPaint.color = bg
      canvas.drawRect(x, top, x + width, bottom, fillPaint)
    }

    // Text pass.
    for (col in 0 until colCount) {
      val flags = cellFlags[col]
      val len = cellTextLen[col]
      if (len == 0 || flags and FLAG_SPACER != 0 || flags and FLAG_INVISIBLE != 0) continue
      val codepoint = Character.codePointAt(rowChars, cellTextOff[col])
      val paint = when {
        symbolsPaint != null &&
          (codepoint in PUA_BMP_FIRST..PUA_BMP_LAST || codepoint >= PUA_SUPPLEMENTARY_FIRST) ->
          symbolsPaint
        flags and FLAG_BOLD != 0 && flags and FLAG_ITALIC != 0 -> boldItalicPaint
        flags and FLAG_BOLD != 0 -> boldPaint
        flags and FLAG_ITALIC != 0 -> italicPaint
        else -> textPaint
      }
      paint.color = cellFg[col]
      if (flags and FLAG_FAINT != 0) paint.alpha = 160
      val x = col * cellWidth
      canvas.drawText(rowChars, cellTextOff[col], len, x, top + baseline, paint)
      val runWidth = if (flags and FLAG_WIDE != 0) cellWidth * 2 else cellWidth
      if (flags and FLAG_UNDERLINE != 0) {
        canvas.drawRect(x, bottom - underlineThickness, x + runWidth, bottom, paint)
      }
      if (flags and FLAG_STRIKETHROUGH != 0) {
        val mid = top + cellHeight / 2f
        canvas.drawRect(x, mid - underlineThickness / 2, x + runWidth, mid + underlineThickness / 2, paint)
      }
      paint.alpha = 255
    }
  }

  override fun onDraw(canvas: Canvas) {
    canvas.drawColor(defaultBg)
    bitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
    drawCursor(canvas)
  }

  private fun drawCursor(canvas: Canvas) {
    if (!cursorVisible || cursorX < 0 || cursorY < 0) return
    if (cursorBlinks && blinkScheduled && !blinkPhaseOn) return
    val x = cursorX * cellWidth
    val top = (cursorY * cellHeight).toFloat()
    val bottom = top + cellHeight
    cursorPaint.color = defaultFg
    when (cursorStyle) {
      CURSOR_STYLE_BAR -> {
        cursorPaint.style = Paint.Style.FILL
        canvas.drawRect(x, top, x + underlineThickness * 2, bottom, cursorPaint)
      }
      CURSOR_STYLE_UNDERLINE -> {
        cursorPaint.style = Paint.Style.FILL
        canvas.drawRect(x, bottom - underlineThickness * 2, x + cellWidth, bottom, cursorPaint)
      }
      CURSOR_STYLE_BLOCK_HOLLOW -> {
        cursorPaint.style = Paint.Style.STROKE
        cursorPaint.strokeWidth = underlineThickness
        canvas.drawRect(x, top, x + cellWidth, bottom, cursorPaint)
      }
      else -> {
        cursorPaint.style = Paint.Style.FILL
        cursorPaint.alpha = 170
        canvas.drawRect(x, top, x + cellWidth, bottom, cursorPaint)
        cursorPaint.alpha = 255
      }
    }
  }

  // ── Input ──

  override fun onTouchEvent(event: MotionEvent): Boolean {
    return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
  }

  override fun onCheckIsTextEditor(): Boolean = true

  override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
    // Visible-password + no-suggestions keeps IMEs in send-as-you-type mode
    // (no autocorrect batching) while still allowing CJK composition.
    outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
      InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
      InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    outAttrs.imeOptions = EditorInfo.IME_ACTION_NONE or
      EditorInfo.IME_FLAG_NO_FULLSCREEN or
      EditorInfo.IME_FLAG_NO_EXTRACT_UI
    return TerminalInputConnection()
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
    return handleKeyEvent(event) || super.onKeyDown(keyCode, event)
  }

  override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
    return handleKeyEvent(event) || super.onKeyUp(keyCode, event)
  }

  internal fun handleKeyEvent(event: KeyEvent): Boolean {
    if (handle == 0L || finished) return false
    @Suppress("DEPRECATION")
    if (event.action == KeyEvent.ACTION_MULTIPLE && event.characters != null) {
      sendText(event.characters)
      return true
    }
    val action = when (event.action) {
      KeyEvent.ACTION_DOWN -> if (event.repeatCount > 0) 2 else 1
      KeyEvent.ACTION_UP -> 0
      else -> return false
    }
    val metaState = event.metaState
    val unicode = event.getUnicodeChar(
      metaState and (KeyEvent.META_CTRL_MASK or KeyEvent.META_META_MASK).inv()
    )
    val utf8 = if (unicode != 0 && unicode and KeyCharacterMap.COMBINING_ACCENT == 0) {
      String(Character.toChars(unicode)).toByteArray(Charsets.UTF_8)
    } else {
      null
    }
    val unshifted = event.getUnicodeChar(0)
    val bytes = GhosttyVt.nativeEncodeKey(
      handle, event.keyCode, action, metaState, unshifted, utf8
    ) ?: return false
    if (bytes.isEmpty()) return false
    sendBytes(bytes)
    return true
  }

  private inner class TerminalInputConnection :
    BaseInputConnection(this@GhosttyTerminalView, false) {
    override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
      sendText(text)
      return super.commitText("", newCursorPosition)
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
      repeat(beforeLength) {
        sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
      }
      return true
    }

    override fun performEditorAction(actionCode: Int): Boolean {
      sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
      sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
      return true
    }

    override fun sendKeyEvent(event: KeyEvent): Boolean {
      if (handleKeyEvent(event)) return true
      return super.sendKeyEvent(event)
    }
  }

  private companion object {
    const val FONT_SIZE_DP = 14f
    const val INITIAL_COLS = 80
    const val INITIAL_ROWS = 24
    const val MAX_SCROLLBACK = 10_000L
    const val BLINK_INTERVAL_MS = 600L
    const val SYMBOLS_FONT_ASSET = "SymbolsNerdFontMono-Regular.ttf"

    // Nerd Font glyph ranges: BMP private-use area plus the supplementary
    // private-use planes (material icons live at U+F0000+ since v3).
    const val PUA_BMP_FIRST = 0xE000
    const val PUA_BMP_LAST = 0xF8FF
    const val PUA_SUPPLEMENTARY_FIRST = 0xF0000

    const val HEADER_BYTES = 48
    const val ROW_HEADER_BYTES = 16
    const val CELL_RECORD_BYTES = 16
    const val MAX_CELL_TEXT_UNITS = 32

    const val DIRTY_FULL = 2

    const val CURSOR_STYLE_BAR = 0
    const val CURSOR_STYLE_BLOCK = 1
    const val CURSOR_STYLE_UNDERLINE = 2
    const val CURSOR_STYLE_BLOCK_HOLLOW = 3

    const val FLAG_BOLD = 1 shl 0
    const val FLAG_ITALIC = 1 shl 1
    const val FLAG_FAINT = 1 shl 2
    const val FLAG_UNDERLINE = 1 shl 3
    const val FLAG_STRIKETHROUGH = 1 shl 4
    const val FLAG_INVERSE = 1 shl 5
    const val FLAG_INVISIBLE = 1 shl 6
    const val FLAG_WIDE = 1 shl 7
    const val FLAG_SPACER = 1 shl 8
    const val FLAG_FG_DEFAULT = 1 shl 9
    const val FLAG_BG_NONE = 1 shl 10
  }
}
