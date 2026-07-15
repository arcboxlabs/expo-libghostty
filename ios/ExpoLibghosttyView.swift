import ExpoModulesCore
import GhosttyTerminal

// Ghostty-backed terminal view. The JS side owns transport and session
// lifecycle; this view only renders bytes pushed via `write` and reports
// user input / grid resizes back through events.
class ExpoLibghosttyView: ExpoView {
  private let terminalView = TerminalView(frame: .zero)
  private var session: InMemoryTerminalSession?

  let onInput = EventDispatcher()
  let onResize = EventDispatcher()

  required init(appContext: AppContext? = nil) {
    super.init(appContext: appContext)
    clipsToBounds = true

    let session = InMemoryTerminalSession(
      write: { [weak self] data in
        self?.onInput(["data": data.base64EncodedString()])
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
    addSubview(terminalView)
  }

  /// Feed PTY output (terminal.output on the wire) into the grid.
  func write(_ data: Data) {
    session?.receive(data)
  }

  /// Mark the underlying PTY as exited (terminal.exit on the wire).
  func finish(exitCode: UInt32) {
    session?.finish(exitCode: exitCode, runtimeMilliseconds: 0)
  }
}
