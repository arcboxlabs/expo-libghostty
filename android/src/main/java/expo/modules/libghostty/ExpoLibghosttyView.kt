package expo.modules.libghostty

import android.content.Context
import android.util.Base64
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.viewevent.EventDispatcher
import expo.modules.kotlin.views.ExpoView

// Ghostty-backed terminal view. The JS side owns transport and session
// lifecycle; this view only renders bytes pushed via `write` and reports
// user input / grid resizes back through events.
class ExpoLibghosttyView(context: Context, appContext: AppContext) :
  ExpoView(context, appContext) {
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
    addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
  }

  /** Feed PTY output (terminal.output on the wire) into the grid. */
  fun write(data: ByteArray) = terminal.write(data)

  /** Mark the underlying PTY as exited (terminal.exit on the wire). */
  fun finish(exitCode: Int) = terminal.finish(exitCode)

  internal fun destroy() = terminal.destroy()
}
