import SwiftUI

// The UI-test bundle needs a target application to be built and installed with; the runner never
// launches it.
@main
struct RunnerHostApp: App {
    var body: some Scene {
        WindowGroup { Text("JetWhale XCTest runner") }
    }
}
