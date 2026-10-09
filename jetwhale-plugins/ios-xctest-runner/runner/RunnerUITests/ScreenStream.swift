import Foundation
import Network
import UIKit
import XCTest

/// Sends the screen to one connection as MJPEG, the way WebDriverAgent's MJPEG server does: a
/// `multipart/x-mixed-replace` response with one JPEG per part, at most `fps` parts a second.
///
/// One screenshot is asked for at a time. A frame that is ready while the previous one is still being
/// written is dropped, so a reader that falls behind gets the newest frame next rather than a queue.
final class ScreenStream {
    enum Source {
        /// testmanagerd's JPEG of the display with this ID.
        case daemon(screenID: Int64)
        /// `XCUIScreen.main.screenshot()`, encoded here.
        case publicScreenshot
    }

    static let boundary = "jetwhale-frame"

    /// At 0.5 a full-resolution frame of a phone's screen is about 190 KB and its text stays sharp.
    private static let quality = 0.5

    /// The public screenshot holds the main thread for about 100 ms, so it is taken at most five
    /// times a second, which leaves the main thread half its time for commands.
    private static let publicScreenshotIntervalNanos: UInt64 = 200_000_000

    /// testmanagerd failing this many screenshots in a row means it no longer takes them.
    private static let daemonFailuresBeforeFallback = 3

    private static let activityLock = NSLock()
    private static var lastSentAt = Date.distantPast

    /// When any stream last sent a frame. A runner whose screen is watched is in use, as one taking
    /// commands is, so this holds off its stop when idle.
    static var lastFrameSentAt: Date { activityLock.withLock { lastSentAt } }

    private let connection: NWConnection
    private let queue = DispatchQueue(label: "jetwhale.runner.stream")
    private let timer: DispatchSourceTimer
    private let requestedIntervalNanos: UInt64
    private var source: Source
    private var daemonFailuresInARow = 0
    /// When the next screenshot is due, in uptime nanoseconds.
    private var dueNanos: UInt64 = 0
    private var isSending = false
    private var isStopped = false

    init(connection: NWConnection, source: Source, fps: Double) {
        self.connection = connection
        self.source = source
        requestedIntervalNanos = UInt64(1e9 / fps)
        timer = DispatchSource.makeTimerSource(flags: .strict, queue: queue)
    }

    /// Answers the request with the stream's header, then sends frames until the reader goes away.
    /// The stream keeps itself alive through its timer and its connection's handlers until then.
    func start() {
        timer.setEventHandler { self.capture() }
        timer.schedule(deadline: .distantFuture)
        timer.resume()
        connection.stateUpdateHandler = { state in
            switch state {
            case .failed, .cancelled: self.queue.async { self.stop() }
            default: break
            }
        }
        awaitReaderClose()
        let header = "HTTP/1.1 200 OK\r\nContent-Type: multipart/x-mixed-replace; boundary=\(Self.boundary)\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
        connection.send(content: Data(header.utf8), completion: .contentProcessed { error in
            self.queue.async {
                guard error == nil else { return self.stop() }
                self.dueNanos = DispatchTime.now().uptimeNanoseconds
                self.capture()
            }
        })
    }

    /// The reader sends nothing after its request, so anything it does send ends the stream, and so
    /// does its closing the connection.
    private func awaitReaderClose() {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 1024) { _, _, _, _ in
            self.queue.async { self.stop() }
        }
    }

    private var intervalNanos: UInt64 {
        switch source {
        case .daemon: return requestedIntervalNanos
        case .publicScreenshot: return max(requestedIntervalNanos, Self.publicScreenshotIntervalNanos)
        }
    }

    /// On `queue`: asks for one screenshot.
    private func capture() {
        guard !isStopped else { return }
        switch source {
        case .daemon(let screenID):
            ScreenCapture.requestJpeg(ofScreen: screenID, quality: Self.quality) { jpeg, error in
                self.queue.async { self.finishCapture(jpeg, failure: error) }
            }
        case .publicScreenshot:
            DispatchQueue.main.async {
                let image = XCUIScreen.main.screenshot().image
                self.queue.async { self.finishCapture(image.jpegData(compressionQuality: Self.quality), failure: nil) }
            }
        }
    }

    private func finishCapture(_ jpeg: Data?, failure: Error?) {
        guard !isStopped else { return }
        if let jpeg {
            daemonFailuresInARow = 0
            send(jpeg)
        } else if case .daemon = source {
            daemonFailuresInARow += 1
            NSLog("JetWhale runner: testmanagerd took no screenshot: %@", String(describing: failure))
            if daemonFailuresInARow >= Self.daemonFailuresBeforeFallback { source = .publicScreenshot }
        }
        scheduleCapture()
    }

    /// Asks for the next screenshot once it is due. Due times stay on the grid the first frame set,
    /// so a timer that fires a little late does not push every later frame back; otherwise 30 frames
    /// a second came out as 25. A screenshot that takes longer than the interval starts the grid
    /// again from now, rather than catching up with a burst.
    private func scheduleCapture() {
        let now = DispatchTime.now().uptimeNanoseconds
        dueNanos += intervalNanos
        if dueNanos + intervalNanos <= now { dueNanos = now }
        if dueNanos <= now {
            capture()
        } else {
            timer.schedule(deadline: DispatchTime(uptimeNanoseconds: dueNanos), leeway: .nanoseconds(0))
        }
    }

    private func send(_ jpeg: Data) {
        guard !isSending else { return }
        isSending = true
        var part = Data("--\(Self.boundary)\r\nContent-Type: image/jpeg\r\nContent-Length: \(jpeg.count)\r\n\r\n".utf8)
        part.append(jpeg)
        part.append(Data("\r\n".utf8))
        connection.send(content: part, completion: .contentProcessed { error in
            self.queue.async {
                self.isSending = false
                guard error == nil else { return self.stop() }
                Self.activityLock.withLock { Self.lastSentAt = Date() }
            }
        })
    }

    private func stop() {
        guard !isStopped else { return }
        isStopped = true
        timer.setEventHandler(handler: nil)
        timer.cancel()
        connection.stateUpdateHandler = nil
        connection.cancel()
    }
}
