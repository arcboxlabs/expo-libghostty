import ExpoModulesCore
import GhosttyTerminal

// Ghostty-backed terminal view. The JS side owns transport and session
// lifecycle; this view only renders bytes pushed via `write` and reports
// user input / grid resizes back through events.
class ExpoLibghosttyView: ExpoView {
  private let terminalView = TerminalView(frame: .zero)
  private var session: InMemoryTerminalSession?

  /// Base font size in points (nil → ghostty's default, 14). Changing it on a
  /// mounted view rebuilds the surface, which resets the grid — set it before
  /// mounting. Pinch-to-zoom steps from this value.
  var fontSize: Float? {
    didSet {
      guard fontSize != oldValue, let session else { return }
      terminalView.configuration = TerminalSurfaceOptions(
        backend: .inMemory(session),
        fontSize: fontSize
      )
    }
  }

  /// Theme colors, applied through the shared controller's ghostty config —
  /// on iOS the theme is app-wide (every terminal view of the controller),
  /// unlike Android's per-view application. Colors accept ghostty config
  /// syntax; nil clears back to the defaults.
  func applyTheme(_ theme: TerminalThemeRecord?) {
    var config = TerminalConfiguration()
    if let theme {
      if let value = theme.background { config = config.background(value) }
      if let value = theme.foreground { config = config.foreground(value) }
      if let value = theme.cursorColor { config = config.cursorColor(value) }
      if let value = theme.selectionBackground { config = config.selectionBackground(value) }
      if let value = theme.selectionForeground { config = config.selectionForeground(value) }
      if let palette = theme.palette {
        for (index, color) in palette.enumerated() where index < 256 {
          if let color { config = config.palette(index, color: color) }
        }
      }
    }
    _ = TerminalController.shared.setTheme(TerminalTheme(light: config, dark: config))
  }

  let onInput = EventDispatcher()
  let onResize = EventDispatcher()

  required init(appContext: AppContext? = nil) {
    super.init(appContext: appContext)
    clipsToBounds = true

    let session = InMemoryTerminalSession(
      write: { [weak self] data in
        self?.onInput([
          "data": data.base64EncodedString(),
          "text": String(decoding: data, as: UTF8.self),
        ])
      },
      resize: { [weak self] viewport in
        self?.onResize(["cols": viewport.columns, "rows": viewport.rows])
      }
    )
    self.session = session
    // Without a controller the coordinator never builds a surface
    // ("surface rebuild skipped: missing controller").
    terminalView.controller = TerminalController.shared
    terminalView.configuration = TerminalSurfaceOptions(backend: .inMemory(session))
    terminalView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
    // VoiceOver: one focusable element; direct interaction keeps touches
    // (taps, selection) flowing to the terminal instead of being swallowed.
    terminalView.isAccessibilityElement = true
    terminalView.accessibilityLabel = "Terminal"
    terminalView.accessibilityTraits = [.allowsDirectInteraction]
    addSubview(terminalView)
  }

  /// Feed PTY output (terminal.output on the wire) into the grid.
  func write(_ data: Data) {
    session?.receive(data)
  }

  /// Feed PTY output as UTF-8 text, for string-based wires.
  func write(_ text: String) {
    session?.receive(text)
  }

  /// Mark the underlying PTY as exited (terminal.exit on the wire).
  func finish(exitCode: UInt32) {
    session?.finish(exitCode: exitCode, runtimeMilliseconds: 0)
  }
}
