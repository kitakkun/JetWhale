import Foundation
import Network

/// A minimal HTTP/1.1 server on the loopback interface. Each request is a `POST` whose path names a
/// command and whose body is a JSON object; the handler runs on the main thread, where XCTest must be
/// called, one request at a time, and its result is the JSON response. A `GET /stream` instead turns
/// its connection into a `ScreenStream` of the screen, at the `fps` its query asks for.
final class CommandServer {
    typealias Handler = @MainActor (_ path: String, _ body: [String: Any]) -> [String: Any]

    private static let tokenHeader = "x-jetwhale-runner-token"

    private let listener: NWListener
    private let queue = DispatchQueue(label: "jetwhale.runner.server")
    private let token: String
    private let screenSource: ScreenStream.Source
    private let handler: Handler

    init(port: UInt16, token: String, screenSource: ScreenStream.Source, handler: @escaping Handler) throws {
        let parameters = NWParameters.tcp
        // Loopback only: on a simulator this is the Mac's loopback, and on a device usbmux forwards
        // to it. Nothing on the network can reach the runner.
        parameters.requiredLocalEndpoint = .hostPort(host: "127.0.0.1", port: NWEndpoint.Port(rawValue: port)!)
        parameters.allowLocalEndpointReuse = true
        listener = try NWListener(using: parameters)
        self.token = token
        self.screenSource = screenSource
        self.handler = handler
    }

    func start() {
        listener.newConnectionHandler = { [weak self] connection in
            guard let self else { return }
            connection.start(queue: self.queue)
            self.receive(on: connection, buffered: Data())
        }
        listener.start(queue: queue)
    }

    func stop() {
        listener.cancel()
    }

    private func receive(on connection: NWConnection, buffered: Data) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 1 << 20) { [weak self] data, _, isComplete, error in
            guard let self else { return }
            var buffered = buffered
            if let data { buffered.append(data) }
            switch HttpRequest.parse(buffered) {
            case .complete(let request, let rest):
                self.respond(to: request, on: connection) { self.receive(on: connection, buffered: rest) }
            case .malformed(let reason):
                self.send(status: "400 Bad Request", body: ["ok": false, "error": reason], on: connection) { connection.cancel() }
            case .incomplete where isComplete || error != nil:
                connection.cancel()
            case .incomplete:
                self.receive(on: connection, buffered: buffered)
            }
        }
    }

    private func respond(to request: HttpRequest, on connection: NWConnection, then next: @escaping () -> Void) {
        guard request.headers[Self.tokenHeader] == token else {
            send(status: "401 Unauthorized", body: ["ok": false, "error": "wrong or missing runner token"], on: connection, then: next)
            return
        }
        if request.method == "GET" && request.path == "/stream" {
            guard let fps = request.query["fps"].flatMap(Double.init), (1...60).contains(fps) else {
                send(status: "400 Bad Request", body: ["ok": false, "error": "fps must be 1 to 60"], on: connection) { connection.cancel() }
                return
            }
            ScreenStream(connection: connection, source: screenSource, fps: fps).start()
            return
        }
        DispatchQueue.main.async {
            let body = MainActor.assumeIsolated { self.handler(request.path, request.json) }
            self.send(status: "200 OK", body: body, on: connection, then: next)
        }
    }

    private func send(status: String, body: [String: Any], on connection: NWConnection, then next: @escaping () -> Void) {
        let payload = (try? JSONSerialization.data(withJSONObject: body)) ?? Data("{}".utf8)
        var response = Data("HTTP/1.1 \(status)\r\nContent-Type: application/json\r\nContent-Length: \(payload.count)\r\n\r\n".utf8)
        response.append(payload)
        connection.send(content: response, completion: .contentProcessed { _ in self.queue.async(execute: next) })
    }
}

struct HttpRequest {
    enum Parsed {
        case complete(HttpRequest, rest: Data)
        case incomplete
        case malformed(String)
    }

    /// Larger than any command's headers or body; more is refused rather than buffered.
    private static let maxHeaderBytes = 16 * 1024
    private static let maxBodyBytes = 1 << 20

    let method: String
    let path: String
    let query: [String: String]
    let headers: [String: String]
    let json: [String: Any]

    /// The first request in [data] and the bytes after it, once all of it has arrived.
    static func parse(_ data: Data) -> Parsed {
        guard let headerEnd = data.range(of: Data("\r\n\r\n".utf8)) else {
            return data.count > maxHeaderBytes ? .malformed("the request's headers are too long") : .incomplete
        }
        guard headerEnd.lowerBound - data.startIndex <= maxHeaderBytes else { return .malformed("the request's headers are too long") }
        let lines = String(decoding: data[data.startIndex..<headerEnd.lowerBound], as: UTF8.self).components(separatedBy: "\r\n")
        let requestLine = lines.first?.split(separator: " ") ?? []
        guard requestLine.count >= 2 else { return .malformed("the request line is not METHOD PATH VERSION") }
        var headers: [String: String] = [:]
        for line in lines.dropFirst() {
            let pair = line.split(separator: ":", maxSplits: 1)
            if pair.count == 2 { headers[pair[0].lowercased()] = pair[1].trimmingCharacters(in: .whitespaces) }
        }
        var length = 0
        if let declared = headers["content-length"] {
            guard let parsed = Int(declared), (0...maxBodyBytes).contains(parsed) else { return .malformed("Content-Length must be 0 to \(maxBodyBytes)") }
            length = parsed
        }
        let bodyStart = headerEnd.upperBound
        guard data.endIndex - bodyStart >= length else { return .incomplete }
        let body = data[bodyStart..<(bodyStart + length)]
        let json: [String: Any]
        if body.isEmpty {
            json = [:]
        } else if let object = (try? JSONSerialization.jsonObject(with: body)) as? [String: Any] {
            json = object
        } else {
            return .malformed("the body is not a JSON object")
        }
        guard let target = URLComponents(string: String(requestLine[1])) else { return .malformed("the request's path is not a URL path") }
        let query = Dictionary((target.queryItems ?? []).map { ($0.name, $0.value ?? "") }) { _, last in last }
        let request = HttpRequest(method: String(requestLine[0]), path: target.path, query: query, headers: headers, json: json)
        return .complete(request, rest: Data(data[(bodyStart + length)...]))
    }
}
