package expo.modules.libghostty

import android.content.Context
import android.util.Base64
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.viewevent.EventDispatcher
import expo.modules.kotlin.views.ExpoView

// Ghostty-backed terminal view. The JS side owns transport and session
// lifecycle; this view only renders bytes pushed via `write` and reports
// user input / grid resizes back through events.
class ExpoLibghosttyView(context: Context, appContext: AppContext) :
  ExpoView(context, appContext) {
  // The accessory bar toggles visibility outside React's knowledge; without
  // this, RN never re-runs our LinearLayout pass and the bar stays invisible.
  override val shouldUseAndroidLayout = true

  private val onInput by EventDispatcher<Map<String, Any>>()
  private val onResize by EventDispatcher<Map<String, Any>>()

  private val terminal = GhosttyTerminalView(context).also { view ->
    view.onInputBytes = { bytes ->
      onInput(
        mapOf(
          "data" to Base64.encodeToString(bytes, Base64.NO_WRAP),
          "text" to String(bytes, Charsets.UTF_8)
        )
      )
    }
    view.onGridResize = { cols, rows ->
      onResize(mapOf("cols" to cols, "rows" to rows))
    }
  }

  private val accessoryBar = TerminalAccessoryBar(context).also { bar ->
    bar.onKey = { keyCode -> terminal.sendAccessoryKey(keyCode) }
    bar.onText = { text -> terminal.sendAccessoryText(text) }
    bar.onPaste = { terminal.requestPaste() }
    bar.visibility = GONE
  }

  init {
    orientation = VERTICAL
    terminal.accessoryBar = accessoryBar
    addView(terminal, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    addView(accessoryBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    // The bar rides the soft keyboard: visible only while the IME is up.
    // React Native does not reliably dispatch insets down to nested native
    // views, so also re-check on every size change (the keyboard resizes us).
    ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
      syncAccessoryBar(insets)
      insets
    }
  }

  private fun syncAccessoryBar(insets: WindowInsetsCompat) {
    val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
    accessoryBar.visibility = if (imeVisible) VISIBLE else GONE
    // Edge-to-edge windows don't resize for the keyboard — it just covers us.
    // Pad our bottom by the covered amount so the bar sits above the IME and
    // the terminal shrinks (regaining the hidden rows). Hosts that already
    // handle the keyboard leave no overlap, so the padding stays zero.
    val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
    val location = IntArray(2)
    getLocationInWindow(location)
    val gapBelow = rootView.height - (location[1] + height)
    val overlap = if (imeVisible) (imeBottom - gapBelow).coerceAtLeast(0) else 0
    if (paddingBottom != overlap) {
      setPadding(0, 0, 0, overlap)
      requestLayout()
    }
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    val rootInsets = rootWindowInsets ?: return
    syncAccessoryBar(WindowInsetsCompat.toWindowInsetsCompat(rootInsets, this))
  }

  /** Base font size in dp; applied live (the grid reflows in place). */
  fun setFontSize(dp: Float) = terminal.setFontSize(dp)

  /** Apply theme colors; a null record clears back to the defaults. */
  internal fun setTheme(theme: TerminalThemeRecord?) = terminal.setTheme(
    foreground = theme?.foreground,
    background = theme?.background,
    cursorColor = theme?.cursorColor,
    selectionBackground = theme?.selectionBackground,
    selectionForeground = theme?.selectionForeground,
    palette = theme?.palette?.toTypedArray()
  )

  /** Feed PTY output (terminal.output on the wire) into the grid. */
  fun write(data: ByteArray) = terminal.write(data)

  /** Mark the underlying PTY as exited (terminal.exit on the wire). */
  fun finish(exitCode: Int) = terminal.finish(exitCode)

  internal fun destroy() = terminal.destroy()
}
