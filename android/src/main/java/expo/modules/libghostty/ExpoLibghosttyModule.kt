package expo.modules.libghostty

import android.util.Base64
import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class ExpoLibghosttyModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("ExpoLibghostty")

    View(ExpoLibghosttyView::class) {
      Events("onInput", "onResize")

      // PTY output bytes (base64) → terminal grid.
      AsyncFunction("write") { view: ExpoLibghosttyView, base64: String ->
        val data = try {
          Base64.decode(base64, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
          throw InvalidBase64Exception(e)
        }
        view.write(data)
      }

      // PTY output as UTF-8 text → terminal grid.
      AsyncFunction("writeText") { view: ExpoLibghosttyView, text: String ->
        view.write(text.toByteArray(Charsets.UTF_8))
      }

      // PTY exited.
      AsyncFunction("finish") { view: ExpoLibghosttyView, exitCode: Int ->
        view.finish(exitCode)
      }

      OnViewDestroys { view: ExpoLibghosttyView ->
        view.destroy()
      }
    }
  }
}

internal class InvalidBase64Exception(cause: Throwable) :
  CodedException("expected base64-encoded terminal output", cause)
