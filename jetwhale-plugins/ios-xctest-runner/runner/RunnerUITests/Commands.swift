import XCTest

struct RunnerError: Error, CustomStringConvertible {
    let description: String
}

/// The version of the commands below, which `/status` reports. A client restarts a runner older
/// than itself; commands are only added, so a newer runner serves an older client.
let protocolVersion = 2

/// The longest one `/lease` keeps the runner from stopping when idle. A client that wants longer
/// renews it, so a lease nobody renews ends within this.
let maxLeaseSeconds: TimeInterval = 2 * 60 * 60

/// The commands the host sends. Points are device-native unless a command's `space` is `screen`:
/// device-native points are portrait whatever the interface orientation, the space XCTest's event
/// synthesis takes; screen points follow the interface orientation, the space of screenshots.
@MainActor
final class Commands {
    static var recordedIssue: String?

    private(set) var isShutdownRequested = false

    private(set) var lastCommandAt = Date()

    /// Until when a lease keeps the runner from stopping when idle.
    private(set) var leaseEnd = Date.distantPast

    private let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")

    // The runner process's own UIScreen reports a 320×480-point screen on an iPhone 17 simulator;
    // XCTest's screenshot has the real size. Taking one costs about 100 ms, so it is taken once.
    private lazy var screen: [String: Any] = {
        let screenshot = XCUIScreen.main.screenshot().image
        let width = screenshot.size.width * screenshot.scale
        let height = screenshot.size.height * screenshot.scale
        return ["screenWidthPixels": Int(min(width, height)), "screenHeightPixels": Int(max(width, height)), "scale": Double(screenshot.scale)]
    }()

    func run(_ path: String, _ body: [String: Any]) -> [String: Any] {
        lastCommandAt = Date()
        Self.recordedIssue = nil
        var result: [String: Any] = [:]
        var thrown: Error?
        let exception = ExceptionCatcher.run {
            do { result = try self.perform(path, body) } catch { thrown = error }
        }
        if let failure = exception ?? thrown.map({ "\($0)" }) ?? Self.recordedIssue {
            return ["ok": false, "error": failure]
        }
        result["ok"] = true
        return result
    }

    private func perform(_ path: String, _ body: [String: Any]) throws -> [String: Any] {
        switch path {
        case "/status":
            return screen.merging(["protocolVersion": protocolVersion, "eventSynthesis": EventSynthesis.isAvailable()]) { first, _ in first }

        case "/interfaceScreen":
            return try interfaceScreen()

        case "/lease":
            guard let requested = (body["seconds"] as? NSNumber)?.doubleValue, requested >= 0 else { throw RunnerError(description: "seconds must be 0 or more") }
            let granted = min(requested, maxLeaseSeconds)
            leaseEnd = Date(timeIntervalSinceNow: granted)
            return ["leaseSeconds": granted]

        case "/tap":
            try press(at: devicePoint(body, "x", "y"), forSeconds: 0)

        case "/longPress":
            try press(at: devicePoint(body, "x", "y"), forSeconds: seconds(body, "durationMillis"))

        case "/swipe":
            try swipe(from: devicePoint(body, "fromX", "fromY"), to: devicePoint(body, "toX", "toY"), forSeconds: seconds(body, "durationMillis"))

        case "/typeText":
            guard let text = body["text"] as? String else { throw RunnerError(description: "text is missing") }
            // XCUIElement.typeText needs the app that holds keyboard focus, and XCTest cannot say
            // which app that is; the synthesized text event goes to whatever has focus.
            guard EventSynthesis.isAvailable() else { throw RunnerError(description: "this Xcode's XCTest has no text-input events, so text cannot be typed") }
            try check(EventSynthesis.typeText(text))

        case "/pressButton":
            try pressButton(body["button"] as? String)

        case "/openAppSwitcher":
            guard EventSynthesis.isAvailable(), let height = screen["screenHeightPixels"] as? Int, let scale = screen["scale"] as? Double else {
                throw RunnerError(description: "this Xcode's XCTest cannot synthesize the swipe that opens the app switcher")
            }
            try check(EventSynthesis.openAppSwitcher(onScreenOfHeight: Double(height) / scale))

        case "/activateApp":
            guard let bundleId = body["bundleId"] as? String else { throw RunnerError(description: "bundleId is missing") }
            XCUIApplication(bundleIdentifier: bundleId).activate()

        case "/shutdown":
            isShutdownRequested = true

        default:
            throw RunnerError(description: "unknown command \(path)")
        }
        return [:]
    }

    // XCUICoordinate waits for the app to idle around every gesture, about two seconds after a
    // scroll, and takes interface-orientation points; it is only the fallback.
    private func press(at point: CGPoint, forSeconds duration: Double) throws {
        if EventSynthesis.isAvailable() {
            try check(EventSynthesis.press(at: point, duration: max(duration, 0.05)))
        } else if duration > 0 {
            coordinate(point).press(forDuration: duration)
        } else {
            coordinate(point).tap()
        }
    }

    private func swipe(from start: CGPoint, to end: CGPoint, forSeconds duration: Double) throws {
        if EventSynthesis.isAvailable() {
            try check(EventSynthesis.drag(from: start, to: end, duration: max(duration, 0.05)))
        } else {
            let velocity = hypot(end.x - start.x, end.y - start.y) / max(duration, 0.05)
            coordinate(start).press(forDuration: 0.05, thenDragTo: coordinate(end), withVelocity: XCUIGestureVelocity(velocity), thenHoldForDuration: 0)
        }
    }

    private func pressButton(_ name: String?) throws {
        switch name {
        case "home":
            XCUIDevice.shared.press(.home)
        case "lock":
            guard EventSynthesis.pressLockButton() else { throw RunnerError(description: "this Xcode's XCTest cannot press the lock button") }
        #if !targetEnvironment(simulator)
        case "volumeUp":
            XCUIDevice.shared.press(.volumeUp)
        case "volumeDown":
            XCUIDevice.shared.press(.volumeDown)
        #endif
        default:
            throw RunnerError(description: "no button named \(name ?? "nil") on this device")
        }
    }

    private func coordinate(_ point: CGPoint) -> XCUICoordinate {
        springboard.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: point.x, dy: point.y))
    }

    /// The point that `body` gives under the keys `x` and `y`, in device-native points: converted from
    /// screen points when the body's `space` is `screen`.
    private func devicePoint(_ body: [String: Any], _ x: String, _ y: String) throws -> CGPoint {
        let given = try point(body, x, y)
        switch body["space"] as? String ?? "device" {
        case "device":
            return given
        case "screen":
            return coordinate(given).screenPoint
        default:
            throw RunnerError(description: "space must be device or screen")
        }
    }

    /// The screen as the interface shows it: its orientation, and its size in pixels that way round.
    /// Where the interface's origin lands in device-native points says how it is turned.
    private func interfaceScreen() throws -> [String: Any] {
        guard let width = screen["screenWidthPixels"] as? Int, let height = screen["screenHeightPixels"] as? Int, let scale = screen["scale"] as? Double else {
            throw RunnerError(description: "the screen's size is unknown")
        }
        let origin = coordinate(.zero).screenPoint
        let atRight = Double(origin.x) * scale > Double(width) / 2
        let atBottom = Double(origin.y) * scale > Double(height) / 2
        let orientation: String
        switch (atRight, atBottom) {
        case (false, false): orientation = "portrait"
        case (true, false): orientation = "landscapeRight"
        case (false, true): orientation = "landscapeLeft"
        case (true, true): orientation = "portraitUpsideDown"
        }
        let landscape = atRight != atBottom
        return ["orientation": orientation, "widthPixels": landscape ? height : width, "heightPixels": landscape ? width : height, "scale": scale]
    }

    private func point(_ body: [String: Any], _ x: String, _ y: String) throws -> CGPoint {
        guard let px = (body[x] as? NSNumber)?.doubleValue, let py = (body[y] as? NSNumber)?.doubleValue else {
            throw RunnerError(description: "\(x) and \(y) are required")
        }
        return CGPoint(x: px, y: py)
    }

    private func seconds(_ body: [String: Any], _ millis: String) throws -> Double {
        guard let value = (body[millis] as? NSNumber)?.doubleValue else { throw RunnerError(description: "\(millis) is required") }
        return value / 1000
    }

    private func check(_ error: Error?) throws {
        if let error { throw RunnerError(description: error.localizedDescription) }
    }
}
