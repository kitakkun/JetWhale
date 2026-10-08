import Foundation
import Network

/// A minimal HTTP/1.1 server on the loopback interface. Each request is a `POST` whose path names a
/// command and whose body is a JSON object; the handler runs on the main thread, where XCTest must be
/// called, one request at a time, and its result is the JSON response.
final class CommandServer {
    typealias Handler = @MainActor (_ path: String, _ body: [String: Any]) -> [String: Any]

    private static let tokenHeader = "x-jetwhale-runner-token"

    private let listener: NWListener
    private let queue = DispatchQueue(label: "jetwhale.runner.server")
    private let token: String
    private let handler: Handler

    init(port: UInt16, token: String, handler: @escaping Handler) throws {
        let parameters = NWParameters.tcp
        // Loopback only: on a simulator this is the Mac's loopback, and on a device usbmux forwards
        // to it. Nothing on the network can reach the runner.
        parameters.requiredLocalEndpoint = .hostPort(host: "127.0.0.1", port: NWEndpoint.Port(rawValue: port)!)
        parameters.allowLocalEndpointReuse = true
        listener = try NWListener(using: parameters)
        self.token = token
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
            if let (request, rest) = HttpRequest.parse(buffered) {
                self.respond(to: request, on: connection) { self.receive(on: connection, buffered: rest) }
            } else if isComplete || error != nil {
                connection.cancel()
            } else {
                self.receive(on: connection, buffered: buffered)
            }
        }
    }

    private func respond(to request: HttpRequest, on connection: NWConnection, then next: @escaping () -> Void) {
        guard request.headers[Self.tokenHeader] == token else {
            send(status: "401 Unauthorized", body: ["ok": false, "error": "wrong or missing runner token"], on: connection, then: next)
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
    let path: String
    let headers: [String: String]
    let json: [String: Any]

    /// The first complete request in [data] and the bytes after it, or nil while it is incomplete.
    static func parse(_ data: Data) -> (HttpRequest, Data)? {
        guard let headerEnd = data.range(of: Data("\r\n\r\n".utf8)) else { return nil }
        let lines = String(decoding: data[data.startIndex..<headerEnd.lowerBound], as: UTF8.self).components(separatedBy: "\r\n")
        let requestLine = lines.first?.split(separator: " ") ?? []
        guard requestLine.count >= 2 else { return nil }
        var headers: [String: String] = [:]
        for line in lines.dropFirst() {
            let pair = line.split(separator: ":", maxSplits: 1)
            if pair.count == 2 { headers[pair[0].lowercased()] = pair[1].trimmingCharacters(in: .whitespaces) }
        }
        let length = headers["content-length"].flatMap(Int.init) ?? 0
        let bodyStart = headerEnd.upperBound
        guard data.endIndex - bodyStart >= length else { return nil }
        let body = data[bodyStart..<(bodyStart + length)]
        let json = (try? JSONSerialization.jsonObject(with: body)) as? [String: Any] ?? [:]
        return (HttpRequest(path: String(requestLine[1]), headers: headers, json: json), Data(data[(bodyStart + length)...]))
    }
}
