package expo.modules.libghostty

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Sticky-modifier key row shown above the soft keyboard (Esc, Ctrl, Alt, Tab,
 * arrows). Plain keys fire [onKey] with the sticky modifier meta applied;
 * Ctrl/Alt toggle and stay lit until the next key (from the bar or the IME)
 * consumes them.
 */
internal class TerminalAccessoryBar(context: Context) : HorizontalScrollView(context) {
  var onKey: ((keyCode: Int) -> Unit)? = null

  private val row = LinearLayout(context).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
  }
  private var ctrlActive = false
  private var altActive = false
  private lateinit var ctrlButton: TextView
  private lateinit var altButton: TextView

  init {
    setBackgroundColor(BAR_BACKGROUND)
    isHorizontalScrollBarEnabled = false
    isFillViewport = true
    addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

    addKey("esc") { onKey?.invoke(KeyEvent.KEYCODE_ESCAPE) }
    ctrlButton = addKey("ctrl") { toggleCtrl() }
    altButton = addKey("alt") { toggleAlt() }
    addKey("tab") { onKey?.invoke(KeyEvent.KEYCODE_TAB) }
    addKey("←") { onKey?.invoke(KeyEvent.KEYCODE_DPAD_LEFT) }
    addKey("↓") { onKey?.invoke(KeyEvent.KEYCODE_DPAD_DOWN) }
    addKey("↑") { onKey?.invoke(KeyEvent.KEYCODE_DPAD_UP) }
    addKey("→") { onKey?.invoke(KeyEvent.KEYCODE_DPAD_RIGHT) }
    addKey("home") { onKey?.invoke(KeyEvent.KEYCODE_MOVE_HOME) }
    addKey("end") { onKey?.invoke(KeyEvent.KEYCODE_MOVE_END) }
    addKey("pgup") { onKey?.invoke(KeyEvent.KEYCODE_PAGE_UP) }
    addKey("pgdn") { onKey?.invoke(KeyEvent.KEYCODE_PAGE_DOWN) }
  }

  /** KeyEvent meta bits for the currently lit sticky modifiers. */
  fun stickyMetaState(): Int {
    var meta = 0
    if (ctrlActive) meta = meta or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
    if (altActive) meta = meta or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
    return meta
  }

  /** Unlatch the sticky modifiers after a key consumed them. */
  fun consumeSticky() {
    if (!ctrlActive && !altActive) return
    ctrlActive = false
    altActive = false
    syncToggleVisuals()
  }

  private fun toggleCtrl() {
    ctrlActive = !ctrlActive
    syncToggleVisuals()
  }

  private fun toggleAlt() {
    altActive = !altActive
    syncToggleVisuals()
  }

  private fun syncToggleVisuals() {
    ctrlButton.setBackgroundColor(if (ctrlActive) KEY_ACTIVE_BACKGROUND else Color.TRANSPARENT)
    altButton.setBackgroundColor(if (altActive) KEY_ACTIVE_BACKGROUND else Color.TRANSPARENT)
  }

  private fun addKey(label: String, onTap: () -> Unit): TextView {
    val key = TextView(context)
    key.text = label
    key.typeface = Typeface.MONOSPACE
    key.textSize = KEY_TEXT_SIZE_SP
    key.setTextColor(KEY_TEXT_COLOR)
    key.gravity = Gravity.CENTER
    val horizontal = (KEY_H_PADDING_DP * resources.displayMetrics.density).toInt()
    val vertical = (KEY_V_PADDING_DP * resources.displayMetrics.density).toInt()
    key.setPadding(horizontal, vertical, horizontal, vertical)
    key.setOnClickListener {
      it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
      onTap()
    }
    row.addView(
      key,
      LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
    )
    return key
  }

  private companion object {
    const val BAR_BACKGROUND = 0xFF1C1C1E.toInt()
    const val KEY_ACTIVE_BACKGROUND = 0x554C8DF5
    const val KEY_TEXT_COLOR = 0xFFD8D8D8.toInt()
    const val KEY_TEXT_SIZE_SP = 13f
    const val KEY_H_PADDING_DP = 10f
    const val KEY_V_PADDING_DP = 10f
  }
}
