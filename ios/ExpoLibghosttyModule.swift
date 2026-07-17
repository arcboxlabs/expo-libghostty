import ExpoModulesCore

public class ExpoLibghosttyModule: Module {
  public func definition() -> ModuleDefinition {
    Name("ExpoLibghostty")

    View(ExpoLibghosttyView.self) {
      Events("onInput", "onResize")

      // Base font size in points (default 14). Changing it after mount
      // rebuilds the surface and resets the grid.
      Prop("fontSize") { (view: ExpoLibghosttyView, size: Float?) in
        view.fontSize = size
      }

      // Theme colors (ghostty config syntax); nil clears to the defaults.
      // App-wide on iOS: applied through the shared controller's config.
      Prop("theme") { (view: ExpoLibghosttyView, theme: TerminalThemeRecord?) in
        view.applyTheme(theme)
      }

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

internal struct TerminalThemeRecord: Record {
  @Field var background: String?
  @Field var foreground: String?
  @Field var cursorColor: String?
  @Field var selectionBackground: String?
  @Field var selectionForeground: String?

  /// Palette overrides by index (0-255) on top of ghostty's default palette.
  @Field var palette: [String?]?
}
