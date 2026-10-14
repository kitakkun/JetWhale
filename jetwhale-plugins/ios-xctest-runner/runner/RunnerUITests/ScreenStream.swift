import Foundation
import Network
import UIKit
import XCTest

/// Sends the screen to one connection as MJPEG, the way WebDriverAgent's MJPEG server does: a
/// `multipart/x-mixed-replace` response with one JPEG per part, at most `fps` parts a second.
///
/// One screenshot is asked for at a time. A frame that is ready while the previous one is still being
/// written is dropped, so a reader that falls behind gets the newest frame next rather than a queue.
/// A frame byte for byte the same as the last one sent is not sent again until a second has passed
/// since that one, so a still screen costs the reader a frame a second, and a reader waiting for a
/// frame never waits longer. Once the screen has stayed still for `idleAfterNanos`, screenshots slow
/// to one every `idleIntervalNanos`, which spares testmanagerd; the first one that differs, or any
/// input command, brings back the requested rate at once.
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

    /// The longest the stream goes without sending a frame, unchanged or not.
    private static let maxSilenceNanos: UInt64 = 1_000_000_000

    /// How long the screen stays unchanged before screenshots slow down. A list scrolled without a
    /// break never stays still this long.
    private static let idleAfterNanos: UInt64 = 500_000_000

    /// The interval between screenshots of a still screen. Each screenshot a second costs testmanagerd
    /// about 1.4 % of a core, so a still screen costs about 13 % instead of 44 % at 60 fps; a change
    /// no input command caused, such as an animation an app starts by itself, shows at most this much
    /// later.
    private static let idleIntervalNanos: UInt64 = 100_000_000

    /// The streams that have started sending, for `resumeFullRate()`.
    private static let startedStreams = NSHashTable<ScreenStream>.weakObjects()
    private static let startedStreamsLock = NSLock()

    private static let activityLock = NSLock()
    private static var lastSentAt = Date.distantPast

    /// When any stream last sent a frame. A runner whose screen is watched is in use, as one taking
    /// commands is, so this holds off its stop when idle.
    static var lastFrameSentAt: Date { activityLock.withLock { lastSentAt } }

    /// Brings every stream back to its requested rate now: input usually changes the screen next.
    static func resumeFullRate() {
        let streams = startedStreamsLock.withLock { startedStreams.allObjects }
        for stream in streams {
            stream.queue.async { stream.resumeOwnFullRate() }
        }
    }

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
    private var isCaptureInFlight = false
    /// When a screenshot last differed from the last frame sent, or input last arrived, in uptime
    /// nanoseconds.
    private var lastChangeNanos: UInt64 = 0
    private var lastSentJpeg: Data?
    /// When the last frame was handed to the connection, in uptime nanoseconds.
    private var lastSentNanos: UInt64 = 0

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
                self.lastChangeNanos = self.dueNanos
                Self.startedStreamsLock.withLock { Self.startedStreams.add(self) }
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
        let activeIntervalNanos = switch source {
        case .daemon: requestedIntervalNanos
        case .publicScreenshot: max(requestedIntervalNanos, Self.publicScreenshotIntervalNanos)
        }
        let isScreenStill = DispatchTime.now().uptimeNanoseconds - lastChangeNanos >= Self.idleAfterNanos
        return isScreenStill ? max(activeIntervalNanos, Self.idleIntervalNanos) : activeIntervalNanos
    }

    /// On `queue`: takes the next screenshot now unless one is under way, whose end schedules the
    /// next at the requested rate.
    private func resumeOwnFullRate() {
        guard !isStopped else { return }
        lastChangeNanos = DispatchTime.now().uptimeNanoseconds
        guard !isCaptureInFlight else { return }
        dueNanos = lastChangeNanos
        timer.schedule(deadline: .now(), leeway: .nanoseconds(0))
    }

    /// On `queue`: asks for one screenshot.
    private func capture() {
        guard !isStopped else { return }
        isCaptureInFlight = true
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
        isCaptureInFlight = false
        if let jpeg {
            daemonFailuresInARow = 0
            if jpeg != lastSentJpeg {
                lastChangeNanos = DispatchTime.now().uptimeNanoseconds
                send(jpeg)
            } else if isRepeatDue() {
                send(jpeg)
            }
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

    /// Whether an unchanged frame has to go now, since the next capture would come after the stream
    /// has been silent for `maxSilenceNanos`.
    private func isRepeatDue() -> Bool {
        DispatchTime.now().uptimeNanoseconds + intervalNanos >= lastSentNanos + Self.maxSilenceNanos
    }

    private func send(_ jpeg: Data) {
        guard !isSending else { return }
        isSending = true
        lastSentJpeg = jpeg
        lastSentNanos = DispatchTime.now().uptimeNanoseconds
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
        Self.startedStreamsLock.withLock { Self.startedStreams.remove(self) }
        timer.setEventHandler(handler: nil)
        timer.cancel()
        connection.stateUpdateHandler = nil
        connection.cancel()
    }
}
