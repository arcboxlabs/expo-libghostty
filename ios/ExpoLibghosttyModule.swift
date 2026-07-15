import ExpoModulesCore

public class ExpoLibghosttyModule: Module {
  public func definition() -> ModuleDefinition {
    Name("ExpoLibghostty")

    View(ExpoLibghosttyView.self) {
      Events("onTap")
    }
  }
}
