// Streams the screen of an iPhone connected by USB as H.264, the way QuickTime Player reads it.
//
// Device Mirror builds this file on the user's Mac with `xcrun swiftc` and starts one run of it per
// iPhone. Its stdout is H.264 in Annex B form with an access unit delimiter after every access unit,
// so a decoder finishes each frame as soon as it arrives. Its stderr carries one JSON object per
// line for Device Mirror (`started`, `format`, `error`), between lines of plain text for people.

import AVFoundation
import CoreMediaIO
import Foundation
import IOKit
import VideoToolbox

enum HelperExit: Int32 {
    case ended = 0
    case usage = 2
    case deviceNotFound = 3
    case permissionDenied = 4
    case captureFailed = 5

    var reason: String {
        switch self {
        case .ended: return "ended"
        case .usage: return "usage"
        case .deviceNotFound: return "deviceNotFound"
        case .permissionDenied: return "permissionDenied"
        case .captureFailed: return "captureFailed"
        }
    }
}

let usage = """
    Usage: jetwhale-iphone-capture --udid <udid> [--name <name>] [--wait-seconds <seconds>] [--dry-run]

    Streams the screen of an iPhone connected by USB to stdout as H.264 in Annex B form, with an
    access unit delimiter after every access unit. Reports on stderr, one JSON object per line among
    lines of plain text. Ends when stdin closes or on SIGTERM. A line `keyframe` on stdin asks for a
    key frame of the current screen.

      --udid <udid>            The iPhone's UDID, as Xcode and devicectl show it.
      --name <name>            The iPhone's name, matched against the capture devices' names when
                               none carries the UDID or the USB serial number.
      --wait-seconds <seconds> How long to wait for the iPhone's screen to appear among the capture
                               devices (default 15).
      --dry-run                Print how the iPhone would be matched and exit, without asking for the
                               Camera or touching capture devices.
      --help                   Print this and exit.

    Exit codes: 0 ended, 2 bad arguments, 3 iPhone not found, 4 Camera access denied, 5 capture failed.

    """

struct Arguments {
    var udid: String
    var name: String?
    var waitSeconds: Double
    var dryRun: Bool

    /// The arguments in [arguments], or nil when they ask for the usage.
    static func parse(_ arguments: [String]) throws -> Arguments? {
        var udid: String?
        var name: String?
        // The first capture after the iPhone is plugged in waits seconds for its USB connection to
        // switch over to the configuration that carries the screen.
        var waitSeconds = 15.0
        var dryRun = false
        var remaining = arguments[...]
        func value(of option: String) throws -> String {
            guard let value = remaining.popFirst(), !value.isEmpty else { throw UsageError("\(option) needs a value") }
            return value
        }
        while let argument = remaining.popFirst() {
            switch argument {
            case "--udid": udid = try value(of: argument)
            case "--name": name = try value(of: argument)
            case "--wait-seconds":
                let text = try value(of: argument)
                guard let seconds = Double(text), seconds > 0, seconds <= 600 else { throw UsageError("--wait-seconds takes more than 0 and at most 600 seconds, not \(text)") }
                waitSeconds = seconds
            case "--dry-run": dryRun = true
            case "--help", "-h": return nil
            default: throw UsageError("unknown argument \(argument)")
            }
        }
        guard let udid else { throw UsageError("--udid is required") }
        return Arguments(udid: udid, name: name, waitSeconds: waitSeconds, dryRun: dryRun)
    }

    /// The USB serial number the iPhone reports: its UDID without the hyphen that usbmuxd inserts
    /// after the eighth digit of a 24-digit serial. A 40-digit UDID is the serial as it is.
    var usbSerialNumber: String { udid.replacingOccurrences(of: "-", with: "") }
}

struct UsageError: Error {
    let message: String

    init(_ message: String) {
        self.message = message
    }
}

/// Writes all of [data] to [descriptor]; false once its reader has gone. FileHandle would raise an
/// exception there instead, which ends the process before the capture session is stopped.
@discardableResult
func writeAll(_ data: Data, to descriptor: Int32) -> Bool {
    data.withUnsafeBytes { raw -> Bool in
        guard let base = raw.baseAddress else { return true }
        var offset = 0
        while offset < raw.count {
            let written = Darwin.write(descriptor, base + offset, raw.count - offset)
            if written < 0 {
                if errno == EINTR { continue }
                return false
            }
            offset += written
        }
        return true
    }
}

/// Writes one JSON object as a line on stderr, for Device Mirror.
func reportEvent(_ fields: [String: Any]) {
    guard var line = try? JSONSerialization.data(withJSONObject: fields, options: [.sortedKeys, .withoutEscapingSlashes]) else { return }
    line.append(0x0A)
    writeAll(line, to: STDERR_FILENO)
}

/// Writes a line of plain text on stderr, for people reading the helper's log.
func log(_ message: String) {
    writeAll(Data("jetwhale-iphone-capture: \(message)\n".utf8), to: STDERR_FILENO)
}

/// One of this Mac's capture devices that shows a cabled iOS device's screen.
struct CaptureCandidate {
    let uniqueID: String
    let name: String
}

enum CaptureChoice: Equatable {
    case found(index: Int, matchedBy: String)
    case ambiguousName(count: Int)
    case none
}

/// Which capture device shows the iPhone.
///
/// Up to macOS 15 a capture device's unique ID was the iPhone's UDID. From macOS 26 it is not, so the
/// device is also looked for by the USB serial number, by the USB location that serial number is
/// plugged into, and last by name, which two iPhones may share.
func chooseCaptureDevice(_ candidates: [CaptureCandidate], udid: String, usbSerialNumber: String, usbLocationIDs: [UInt32], name: String?) -> CaptureChoice {
    if let index = candidates.firstIndex(where: { $0.uniqueID.caseInsensitiveCompare(udid) == .orderedSame }) {
        return .found(index: index, matchedBy: "udid")
    }
    if let index = candidates.firstIndex(where: { $0.uniqueID.range(of: usbSerialNumber, options: .caseInsensitive) != nil }) {
        return .found(index: index, matchedBy: "usbSerialNumber")
    }
    // A USB camera's unique ID reads 0x<location ID><vendor ID><product ID>; 05ac is Apple's vendor ID.
    let locationPrefixes = usbLocationIDs.flatMap { [String(format: "0x%08x05ac", $0), String(format: "0x%x05ac", $0)] }
    if let index = candidates.firstIndex(where: { candidate in locationPrefixes.contains { candidate.uniqueID.lowercased().hasPrefix($0) } }) {
        return .found(index: index, matchedBy: "usbLocation")
    }
    guard let name else { return .none }
    let named = candidates.indices.filter { candidates[$0].name == name }
    switch named.count {
    case 0: return .none
    case 1: return .found(index: named[0], matchedBy: "name")
    default: return .ambiguousName(count: named.count)
    }
}

/// The USB location IDs of the devices plugged in with [serialNumber], read from the I/O Registry.
func findUsbLocationIDs(serialNumber: String) -> [UInt32] {
    var iterator: io_iterator_t = 0
    guard IOServiceGetMatchingServices(kIOMainPortDefault, IOServiceMatching("IOUSBHostDevice"), &iterator) == KERN_SUCCESS else { return [] }
    defer { IOObjectRelease(iterator) }
    var locationIDs: [UInt32] = []
    while case let service = IOIteratorNext(iterator), service != 0 {
        defer { IOObjectRelease(service) }
        let serial = IORegistryEntryCreateCFProperty(service, "USB Serial Number" as CFString, kCFAllocatorDefault, 0)?.takeRetainedValue() as? String
        guard serial?.caseInsensitiveCompare(serialNumber) == .orderedSame,
              let locationID = IORegistryEntryCreateCFProperty(service, "locationID" as CFString, kCFAllocatorDefault, 0)?.takeRetainedValue() as? NSNumber
        else { continue }
        locationIDs.append(locationID.uint32Value)
    }
    return locationIDs
}

/// The helper's stdout. Once closed, what is written goes to /dev/null, so a write racing the close
/// cannot reach a file that took over the descriptor.
final class StreamOutput {
    private let lock = NSLock()
    private var closed = false

    /// Writes [data]; false once the reader has gone.
    func write(_ data: Data) -> Bool {
        lock.lock()
        let isClosed = closed
        lock.unlock()
        if isClosed { return true }
        return writeAll(data, to: STDOUT_FILENO)
    }

    /// Ends the stream for its reader at once, while the capture session may still take a moment to stop.
    func close() {
        lock.lock()
        defer { lock.unlock() }
        if closed { return }
        closed = true
        let devNull = open("/dev/null", O_WRONLY)
        if devNull >= 0 {
            dup2(devNull, STDOUT_FILENO)
            Darwin.close(devNull)
        }
    }
}

let startCode: [UInt8] = [0, 0, 0, 1]

/// An access unit delimiter, written after each access unit rather than before the next: a decoder
/// reading Annex B holds a frame back until the next NAL unit starts, which on a still screen may
/// be seconds away.
let accessUnitDelimiter: [UInt8] = [0, 0, 0, 1, 0x09, 0xF0]

/// One frame of H.264 in Annex B form, followed by an access unit delimiter.
struct AccessUnit {
    let bytes: Data
    let isKeyFrame: Bool
    let width: Int32
    let height: Int32

    /// The H.264 in [sample], whose NAL units carry big-endian length prefixes; a key frame is
    /// preceded by the parameter sets of its format, so a decoder can start from it. Nil when
    /// [sample] holds no H.264 this can read.
    init?(_ sample: CMSampleBuffer) {
        guard let format = CMSampleBufferGetFormatDescription(sample),
              CMFormatDescriptionGetMediaSubType(format) == kCMVideoCodecType_H264,
              let block = CMSampleBufferGetDataBuffer(sample)
        else { return nil }
        var parameterSetCount = 0
        var lengthPrefixSize: Int32 = 0
        guard CMVideoFormatDescriptionGetH264ParameterSetAtIndex(format, parameterSetIndex: 0, parameterSetPointerOut: nil, parameterSetSizeOut: nil, parameterSetCountOut: &parameterSetCount, nalUnitHeaderLengthOut: &lengthPrefixSize) == noErr,
              (1...4).contains(lengthPrefixSize)
        else { return nil }
        let length = CMBlockBufferGetDataLength(block)
        var data = [UInt8](repeating: 0, count: length)
        guard CMBlockBufferCopyDataBytes(block, atOffset: 0, dataLength: length, destination: &data) == noErr else { return nil }

        var nalUnits: [ArraySlice<UInt8>] = []
        var offset = 0
        let prefixSize = Int(lengthPrefixSize)
        while offset + prefixSize <= length {
            let nalLength = data[offset..<offset + prefixSize].reduce(0) { $0 << 8 | Int($1) }
            offset += prefixSize
            guard nalLength > 0, offset + nalLength <= length else { break }
            nalUnits.append(data[offset..<offset + nalLength])
            offset += nalLength
        }
        let types = nalUnits.map { $0[$0.startIndex] & 0x1F }
        let isKeyFrame = types.contains(5)

        var bytes = Data()
        if isKeyFrame {
            for index in 0..<parameterSetCount {
                var pointer: UnsafePointer<UInt8>?
                var size = 0
                guard CMVideoFormatDescriptionGetH264ParameterSetAtIndex(format, parameterSetIndex: index, parameterSetPointerOut: &pointer, parameterSetSizeOut: &size, parameterSetCountOut: nil, nalUnitHeaderLengthOut: nil) == noErr,
                      let pointer
                else { return nil }
                bytes.append(contentsOf: startCode)
                bytes.append(pointer, count: size)
            }
        }
        for (nalUnit, type) in zip(nalUnits, types) where type != 9 {
            bytes.append(contentsOf: startCode)
            bytes.append(contentsOf: nalUnit)
        }
        bytes.append(contentsOf: accessUnitDelimiter)

        let dimensions = CMVideoFormatDescriptionGetDimensions(format)
        self.bytes = bytes
        self.isKeyFrame = isKeyFrame
        self.width = dimensions.width
        self.height = dimensions.height
    }
}

/// Writes access units to the helper's stdout, and reports the frames' size whenever it changes.
final class AccessUnitWriter {
    private let output: StreamOutput
    private let onReaderGone: () -> Void
    private let lock = NSLock()
    private var size: (Int32, Int32)?
    private var passingThrough: Bool?

    init(output: StreamOutput, onReaderGone: @escaping () -> Void) {
        self.output = output
        self.onReaderGone = onReaderGone
    }

    /// Writes [unit]. Frames come from the capture queue and from the encoder's thread, one source
    /// at a time, and the lock keeps a frame whole however they meet.
    func write(_ unit: AccessUnit, passedThrough: Bool) {
        lock.lock()
        defer { lock.unlock() }
        let isNewSize = size.map { $0 != (unit.width, unit.height) } ?? true
        size = (unit.width, unit.height)
        let switched = passingThrough.map { $0 != passedThrough } ?? false
        passingThrough = passedThrough
        if isNewSize { reportEvent(["event": "format", "width": unit.width, "height": unit.height, "passthrough": passedThrough]) }
        if switched { log(passedThrough ? "passing the iPhone's H.264 through again from its key frame" : "encoding frames to answer a key frame request") }
        if !output.write(unit.bytes) { onReaderGone() }
    }
}

/// Decodes the iPhone's frames as they pass through, keeping the latest picture, so a key frame
/// can be encoded from it when one is asked for between the iPhone's own key frames.
final class LatestPicture {
    private var session: VTDecompressionSession?
    private var format: CMFormatDescription?
    private let lock = NSLock()
    private var picture: CVImageBuffer?

    var latest: CVImageBuffer? {
        lock.lock()
        defer { lock.unlock() }
        return picture
    }

    func keep(_ picture: CVImageBuffer) {
        lock.lock()
        self.picture = picture
        lock.unlock()
    }

    func decode(_ sample: CMSampleBuffer) {
        guard let sampleFormat = CMSampleBufferGetFormatDescription(sample) else { return }
        if session == nil || format.map({ !CMFormatDescriptionEqual($0, otherFormatDescription: sampleFormat) }) ?? true {
            if let session { VTDecompressionSessionInvalidate(session) }
            session = nil
            var created: VTDecompressionSession?
            let status = VTDecompressionSessionCreate(allocator: nil, formatDescription: sampleFormat, decoderSpecification: nil, imageBufferAttributes: nil, outputCallback: nil, decompressionSessionOut: &created)
            guard status == noErr, let created else {
                log("the iPhone's frames cannot be decoded (\(status)), so a key frame asked for waits for the iPhone's own")
                format = nil
                return
            }
            session = created
            format = sampleFormat
        }
        guard let session else { return }
        VTDecompressionSessionDecodeFrame(session, sampleBuffer: sample, flags: [], infoFlagsOut: nil) { [weak self] status, _, imageBuffer, _, _ in
            if status == noErr, let imageBuffer { self?.keep(imageBuffer) }
        }
        VTDecompressionSessionWaitForAsynchronousFrames(session)
    }
}

/// Encodes pictures as H.264 in real time, with a key frame wherever one is asked for.
final class PictureEncoder {
    private var session: VTCompressionSession?
    private var size: (Int, Int)?

    func encode(_ picture: CVImageBuffer, forceKeyFrame: Bool, output: @escaping (CMSampleBuffer) -> Void) {
        let width = CVPixelBufferGetWidth(picture)
        let height = CVPixelBufferGetHeight(picture)
        if session == nil || size.map({ $0 != (width, height) }) ?? true {
            finishEncodedFrames()
            if let session { VTCompressionSessionInvalidate(session) }
            session = nil
            var created: VTCompressionSession?
            let status = VTCompressionSessionCreate(allocator: nil, width: Int32(width), height: Int32(height), codecType: kCMVideoCodecType_H264, encoderSpecification: nil, imageBufferAttributes: nil, compressedDataAllocator: nil, outputCallback: nil, refcon: nil, compressionSessionOut: &created)
            guard status == noErr, let created else {
                log("a \(width)x\(height) picture cannot be encoded (\(status))")
                return
            }
            VTSessionSetProperty(created, key: kVTCompressionPropertyKey_RealTime, value: kCFBooleanTrue)
            VTSessionSetProperty(created, key: kVTCompressionPropertyKey_AllowFrameReordering, value: kCFBooleanFalse)
            VTSessionSetProperty(created, key: kVTCompressionPropertyKey_ProfileLevel, value: kVTProfileLevel_H264_High_AutoLevel)
            // About what the iPhone sends for a scrolling screen; the pipe to Device Mirror is local.
            VTSessionSetProperty(created, key: kVTCompressionPropertyKey_AverageBitRate, value: 12_000_000 as CFNumber)
            VTSessionSetProperty(created, key: kVTCompressionPropertyKey_MaxKeyFrameIntervalDuration, value: 2 as CFNumber)
            VTCompressionSessionPrepareToEncodeFrames(created)
            session = created
            size = (width, height)
        }
        guard let session else { return }
        // Pictures are stamped when they are encoded: a picture may be encoded twice, and the
        // encoder refuses a time that does not move forward.
        let time = CMClockGetTime(CMClockGetHostTimeClock())
        let properties = forceKeyFrame ? [kVTEncodeFrameOptionKey_ForceKeyFrame: kCFBooleanTrue] as CFDictionary : nil
        VTCompressionSessionEncodeFrame(session, imageBuffer: picture, presentationTimeStamp: time, duration: .invalid, frameProperties: properties, infoFlagsOut: nil) { status, _, sample in
            if status == noErr, let sample { output(sample) }
        }
    }

    /// Waits until every picture given so far has come out of the encoder.
    func finishEncodedFrames() {
        if let session { VTCompressionSessionCompleteFrames(session, untilPresentationTimeStamp: .invalid) }
    }
}

/// The iPhone's frames on their way to stdout.
///
/// The iPhone's own H.264 is passed through as it is. A reader that joins mid-stream needs a key
/// frame, and the iPhone sends one only when it chooses, so a key frame asked for is encoded from
/// the latest picture, and the frames after it are encoded too, until the iPhone's next key frame
/// lets the stream pass through again. A capture device that delivers pictures rather than H.264 is
/// encoded throughout.
final class FramePipeline: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    let queue = DispatchQueue(label: "com.kitakkun.jetwhale.iphone-capture.frames")
    private let writer: AccessUnitWriter
    private let pictures = LatestPicture()
    private let encoder = PictureEncoder()
    private var hasKeyFrame = false
    private var encoding = false
    private var keyFrameAsked = false

    init(writer: AccessUnitWriter) {
        self.writer = writer
    }

    func captureOutput(_ output: AVCaptureOutput, didOutput sample: CMSampleBuffer, from connection: AVCaptureConnection) {
        if let unit = AccessUnit(sample) {
            passThrough(unit, sample: sample)
        } else if let picture = CMSampleBufferGetImageBuffer(sample) {
            pictures.keep(picture)
            encode(picture)
        }
    }

    func captureOutput(_ output: AVCaptureOutput, didDrop sample: CMSampleBuffer, from connection: AVCaptureConnection) {
        // A frame missing from the chain spoils every frame after it up to the next key frame.
        if hasKeyFrame { log("a frame was dropped; waiting for the iPhone's next key frame") }
        hasKeyFrame = false
    }

    /// Asks for a key frame of the current screen, at once when a picture has arrived.
    func askForKeyFrame() {
        queue.async { [self] in
            keyFrameAsked = true
            if let latest = pictures.latest { encode(latest) }
        }
    }

    private func passThrough(_ unit: AccessUnit, sample: CMSampleBuffer) {
        if !hasKeyFrame {
            guard unit.isKeyFrame else { return }
            hasKeyFrame = true
        }
        pictures.decode(sample)
        if unit.isKeyFrame {
            if encoding { encoder.finishEncodedFrames() }
            encoding = false
            keyFrameAsked = false
            writer.write(unit, passedThrough: true)
        } else if encoding || keyFrameAsked, let latest = pictures.latest {
            encode(latest)
        } else {
            writer.write(unit, passedThrough: true)
        }
    }

    private func encode(_ picture: CVImageBuffer) {
        encoding = true
        let forceKeyFrame = keyFrameAsked
        keyFrameAsked = false
        encoder.encode(picture, forceKeyFrame: forceKeyFrame) { [writer] sample in
            if let unit = AccessUnit(sample) { writer.write(unit, passedThrough: false) }
        }
    }
}

/// What stops the helper before or during the capture: which exit it takes, and why.
struct HelperFailure: Error {
    let exit: HelperExit
    let message: String
    let details: [String: Any]

    init(_ exit: HelperExit, _ message: String, details: [String: Any] = [:]) {
        self.exit = exit
        self.message = message
        self.details = details
    }

    static let cameraAccessDenied = HelperFailure(.permissionDenied, "macOS has not allowed Camera access, which reading an iPhone's screen over USB needs")
}

/// Ends the helper once, however many reasons to end arrive.
final class Ending {
    private let queue = DispatchQueue(label: "com.kitakkun.jetwhale.iphone-capture.ending")
    private let output: StreamOutput
    private var ended = false
    var session: AVCaptureSession?

    init(output: StreamOutput) {
        self.output = output
    }

    func end(_ failure: HelperFailure?) {
        queue.async { [self] in
            if ended { return }
            ended = true
            if let failure {
                var fields = failure.details
                fields["event"] = "error"
                fields["reason"] = failure.exit.reason
                fields["message"] = failure.message
                reportEvent(fields)
            }
            output.close()
            if let session {
                // Stopping the session hands the iPhone's USB connection back to its usual
                // configuration; a session that does not stop in time ends with the process.
                let stopped = DispatchSemaphore(value: 0)
                DispatchQueue.global().async {
                    session.stopRunning()
                    stopped.signal()
                }
                _ = stopped.wait(timeout: .now() + 3)
            }
            Darwin.exit(failure?.exit.rawValue ?? HelperExit.ended.rawValue)
        }
    }
}

/// Reads stdin until it closes, which ends the helper, taking each line as a command.
func readCommands(ending: Ending, onKeyFrameAsked: @escaping () -> Void) {
    let thread = Thread {
        var pending = Data()
        var buffer = [UInt8](repeating: 0, count: 1024)
        while true {
            let count = read(STDIN_FILENO, &buffer, buffer.count)
            if count < 0, errno == EINTR { continue }
            if count <= 0 { break }
            pending.append(contentsOf: buffer[0..<count])
            while let newline = pending.firstIndex(of: 0x0A) {
                let line = String(decoding: pending[pending.startIndex..<newline], as: UTF8.self).trimmingCharacters(in: .whitespaces)
                pending.removeSubrange(pending.startIndex...newline)
                if line == "keyframe" { onKeyFrameAsked() } else if !line.isEmpty { log("ignoring the unknown command \(line)") }
            }
        }
        ending.end(nil)
    }
    thread.start()
}

/// Ends the helper on SIGTERM and SIGINT, and lets a write to a closed stdout fail rather than kill it.
func endOnSignals(ending: Ending) -> [DispatchSourceSignal] {
    signal(SIGPIPE, SIG_IGN)
    return [SIGTERM, SIGINT].map { code in
        signal(code, SIG_IGN)
        let source = DispatchSource.makeSignalSource(signal: code, queue: .global())
        source.setEventHandler { ending.end(nil) }
        source.resume()
        return source
    }
}

/// Waits until macOS lets this process use the Camera, asking the user when nobody has yet.
///
/// macOS asks on behalf of the responsible app, the one that started the host, and stops a process
/// whose responsible app declares no Camera usage description.
func requireCameraAccess() throws {
    switch AVCaptureDevice.authorizationStatus(for: .video) {
    case .authorized:
        return
    case .notDetermined:
        var granted: Bool?
        AVCaptureDevice.requestAccess(for: .video) { answer in DispatchQueue.main.async { granted = answer } }
        while granted == nil { RunLoop.current.run(until: Date().addingTimeInterval(0.1)) }
        if granted == true { return }
        throw HelperFailure.cameraAccessDenied
    case .denied, .restricted:
        throw HelperFailure.cameraAccessDenied
    @unknown default:
        throw HelperFailure.cameraAccessDenied
    }
}

/// Lets this process see iOS devices' screens among its capture devices, as QuickTime Player does.
/// The iPhone then switches its USB connection to a configuration that carries the screen, which
/// drops the connections other programs had to it. Wireless screen capture stays at its default,
/// off, so only an iPhone on the cable is listed.
func allowScreenCaptureDevices() throws {
    var address = CMIOObjectPropertyAddress(
        mSelector: CMIOObjectPropertySelector(kCMIOHardwarePropertyAllowScreenCaptureDevices),
        mScope: CMIOObjectPropertyScope(kCMIOObjectPropertyScopeGlobal),
        mElement: CMIOObjectPropertyElement(kCMIOObjectPropertyElementMain)
    )
    var allow: UInt32 = 1
    let status = CMIOObjectSetPropertyData(CMIOObjectID(kCMIOObjectSystemObject), &address, 0, nil, UInt32(MemoryLayout<UInt32>.size), &allow)
    if status != noErr { throw HelperFailure(.captureFailed, "screen capture devices could not be enabled (\(status))") }
}

/// The capture device that shows the iPhone, and how it was recognized, waiting up to the
/// arguments' wait for it to appear.
///
/// AVFoundation lists a cabled device's screen only to a lookup made on the main thread, so this
/// runs there.
func findCaptureDevice(_ arguments: Arguments) throws -> (device: AVCaptureDevice, matchedBy: String) {
    let deadline = Date().addingTimeInterval(arguments.waitSeconds)
    while true {
        let devices = AVCaptureDevice.DiscoverySession(deviceTypes: [.external], mediaType: .muxed, position: .unspecified).devices.filter(\.isConnected)
        let candidates = devices.map { CaptureCandidate(uniqueID: $0.uniqueID, name: $0.localizedName) }
        let choice = chooseCaptureDevice(candidates, udid: arguments.udid, usbSerialNumber: arguments.usbSerialNumber, usbLocationIDs: findUsbLocationIDs(serialNumber: arguments.usbSerialNumber), name: arguments.name)
        if case let .found(index, matchedBy) = choice { return (devices[index], matchedBy) }
        if Date() >= deadline {
            let listed = candidates.map { ["uniqueId": $0.uniqueID, "name": $0.name] }
            if case let .ambiguousName(count) = choice {
                throw HelperFailure(.deviceNotFound, "\(count) capture devices are called \(arguments.name ?? ""), and none carries the iPhone's UDID or USB serial number", details: ["candidates": listed])
            }
            throw HelperFailure(.deviceNotFound, "no capture device showed the iPhone within \(Int(arguments.waitSeconds)) s", details: ["candidates": listed])
        }
        RunLoop.current.run(until: Date().addingTimeInterval(0.2))
    }
}

/// Starts a capture session that passes [device]'s frames to [pipeline], and has [ending] stop it
/// and end the helper when the session fails or the device goes away.
func startCaptureSession(of device: AVCaptureDevice, matchedBy: String, into pipeline: FramePipeline, ending: Ending) throws {
    let input: AVCaptureDeviceInput
    do {
        input = try AVCaptureDeviceInput(device: device)
    } catch let error as NSError {
        if error.domain == AVFoundationErrorDomain, error.code == AVError.Code.applicationIsNotAuthorizedToUseDevice.rawValue { throw HelperFailure.cameraAccessDenied }
        throw HelperFailure(.captureFailed, "the iPhone's screen cannot be opened: \(error.localizedDescription)")
    }
    let session = AVCaptureSession()
    let output = AVCaptureVideoDataOutput()
    // An empty dictionary keeps the device's own format: the H.264 the iPhone encoded.
    output.videoSettings = [:]
    // A compressed frame left out spoils the ones after it, so none is discarded for being late.
    output.alwaysDiscardsLateVideoFrames = false
    output.setSampleBufferDelegate(pipeline, queue: pipeline.queue)
    session.beginConfiguration()
    guard session.canAddInput(input), session.canAddOutput(output) else {
        session.commitConfiguration()
        throw HelperFailure(.captureFailed, "the iPhone's screen cannot be read by a capture session")
    }
    session.addInput(input)
    session.addOutput(output)
    session.commitConfiguration()
    ending.session = session

    NotificationCenter.default.addObserver(forName: AVCaptureSession.runtimeErrorNotification, object: session, queue: nil) { notification in
        let error = notification.userInfo?[AVCaptureSessionErrorKey] as? NSError
        ending.end(HelperFailure(.captureFailed, "the capture session failed: \(error?.localizedDescription ?? "unknown error")"))
    }
    NotificationCenter.default.addObserver(forName: AVCaptureDevice.wasDisconnectedNotification, object: device, queue: nil) { _ in
        ending.end(HelperFailure(.deviceNotFound, "the iPhone was disconnected"))
    }
    DispatchQueue.global().async {
        session.startRunning()
        guard session.isRunning else {
            ending.end(HelperFailure(.captureFailed, "the capture session did not start"))
            return
        }
        reportEvent(["event": "started", "uniqueId": device.uniqueID, "name": device.localizedName, "matchedBy": matchedBy])
    }
}

func printDryRun(_ arguments: Arguments) {
    let plan: [String: Any] = [
        "udid": arguments.udid,
        "usbSerialNumber": arguments.usbSerialNumber,
        "usbLocationIds": findUsbLocationIDs(serialNumber: arguments.usbSerialNumber).map { String(format: "0x%08x", $0) },
        "name": arguments.name ?? NSNull(),
        "waitSeconds": arguments.waitSeconds,
        "matchOrder": ["udid", "usbSerialNumber", "usbLocation", "name"],
    ]
    guard var json = try? JSONSerialization.data(withJSONObject: plan, options: [.sortedKeys, .prettyPrinted, .withoutEscapingSlashes]) else { return }
    json.append(0x0A)
    writeAll(json, to: STDOUT_FILENO)
}

@main
enum IphoneScreenCapture {
    static func main() {
        let arguments: Arguments
        do {
            guard let parsed = try Arguments.parse(Array(CommandLine.arguments.dropFirst())) else {
                writeAll(Data(usage.utf8), to: STDOUT_FILENO)
                exit(HelperExit.ended.rawValue)
            }
            arguments = parsed
        } catch {
            let message = (error as? UsageError)?.message ?? "\(error)"
            reportEvent(["event": "error", "reason": HelperExit.usage.reason, "message": message])
            writeAll(Data(usage.utf8), to: STDERR_FILENO)
            exit(HelperExit.usage.rawValue)
        }
        if arguments.dryRun {
            printDryRun(arguments)
            exit(HelperExit.ended.rawValue)
        }

        let output = StreamOutput()
        let ending = Ending(output: output)
        let signalSources = endOnSignals(ending: ending)
        let pipeline = FramePipeline(writer: AccessUnitWriter(output: output, onReaderGone: { ending.end(nil) }))
        readCommands(ending: ending, onKeyFrameAsked: pipeline.askForKeyFrame)
        do {
            // Camera access comes first: the screen capture devices, once allowed, switch the
            // iPhone's USB connection over, which is not worth doing for a capture that cannot run.
            try requireCameraAccess()
            try allowScreenCaptureDevices()
            let (device, matchedBy) = try findCaptureDevice(arguments)
            try startCaptureSession(of: device, matchedBy: matchedBy, into: pipeline, ending: ending)
        } catch {
            ending.end(error as? HelperFailure ?? HelperFailure(.captureFailed, "\(error)"))
        }
        withExtendedLifetime(signalSources) { RunLoop.main.run() }
    }
}
