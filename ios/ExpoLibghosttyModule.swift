import ExpoModulesCore

public class ExpoLibghosttyModule: Module {
  public func definition() -> ModuleDefinition {
    Name("ExpoLibghostty")

    View(ExpoLibghosttyView.self) {
      Events("onInput", "onResize")

      // PTY output bytes (base64) → terminal grid.
      AsyncFunction("write") { (view: ExpoLibghosttyView, base64: String) in
        guard let data = Data(base64Encoded: base64) else {
          throw InvalidBase64Exception()
        }
        view.write(data)
      }.runOnQueue(.main)

      // PTY output as UTF-8 text → terminal grid.
      AsyncFunction("writeText") { (view: ExpoLibghosttyView, text: String) in
        view.write(text)
      }.runOnQueue(.main)

      // PTY exited.
      AsyncFunction("finish") { (view: ExpoLibghosttyView, exitCode: UInt32) in
        view.finish(exitCode: exitCode)
      }.runOnQueue(.main)
    }
  }
}

internal final class InvalidBase64Exception: Exception {
  override var reason: String {
    "expected base64-encoded terminal output"
  }
}
