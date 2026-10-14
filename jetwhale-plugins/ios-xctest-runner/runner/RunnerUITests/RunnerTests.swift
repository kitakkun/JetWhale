import XCTest

/// The runner's only test: it serves commands from the JetWhale host until `/shutdown`.
final class RunnerTests: XCTestCase {
    @MainActor
    func testServe() throws {
        let environment = ProcessInfo.processInfo.environment
        guard let port = environment["JETWHALE_RUNNER_PORT"].flatMap(UInt16.init),
              let token = environment["JETWHALE_RUNNER_TOKEN"],
              let idleSeconds = environment["JETWHALE_RUNNER_IDLE_SECONDS"].flatMap(TimeInterval.init)
        else {
            XCTFail("JETWHALE_RUNNER_PORT, JETWHALE_RUNNER_TOKEN and JETWHALE_RUNNER_IDLE_SECONDS must be set")
            return
        }
        let commands = Commands()
        let screenSource: ScreenStream.Source = ScreenCapture.isAvailable() ? .daemon(screenID: ScreenCapture.mainScreenID()) : .publicScreenshot
        let server = try CommandServer(port: port, token: token, screenSource: screenSource) { path, body in commands.run(path, body) }
        server.start()
        NSLog("JetWhale runner serving on 127.0.0.1:%d", Int(port))
        // A runner nobody talks to or watches, and nobody holds a lease on, ends its test, so it never
        // outlives the hosts that started it.
        while !commands.isShutdownRequested && (Date().timeIntervalSince(max(commands.lastCommandAt, ScreenStream.lastFrameSentAt)) < idleSeconds || Date() < commands.leaseEnd) {
            RunLoop.main.run(mode: .default, before: Date(timeIntervalSinceNow: 0.5))
        }
        server.stop()
    }

    // A recorded issue fails the test, and XCTest then ends it, taking the server down. A failing
    // command reports the issue as its error instead.
    override func record(_ issue: XCTIssue) {
        NSLog("JetWhale runner: %@", issue.compactDescription)
        MainActor.assumeIsolated { Commands.recordedIssue = issue.compactDescription }
    }
}
