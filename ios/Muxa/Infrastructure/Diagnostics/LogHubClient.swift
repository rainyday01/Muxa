import Foundation

enum LogHubClientError: Error, Equatable {
    case rejected(status: Int)
    case transport
    case cooldown(seconds: Int)

    var userMessage: String {
        switch self {
        case let .cooldown(seconds):
            "Please wait \(seconds) s before sending another log."
        case .rejected(status: 429):
            "Too many uploads. Please try again later."
        case .rejected(status: 503):
            "The log service is unavailable right now. Please try again later."
        case let .rejected(status):
            "Log upload failed (HTTP \(status))."
        case .transport:
            "Log upload failed."
        }
    }
}

enum LogHubClient {
    struct Payload: Sendable {
        var logText: String
        var note: String
        var appVersion: String
        var deviceID: String
        var osVersion: String
        var model: String
    }

    struct UploadResult: Equatable, Sendable {
        var id: String
        var status: String
    }

    static let maximumLogCharacters = 400_000
    /// Server limits: 10 MB per package (multipart overhead left out), 20 uploads/hour per device.
    static let maximumArchiveBytes = 9_500_000
    static let uploadCooldown: TimeInterval = 60
    private static let lastUploadKey = "muxa.loghub.lastUploadAt"

    /// LogHub upload token (role=upload). Not in source: the repository is public.
    /// Official builds set `MuxaLogHubUploadToken` in the Info.plist at build time;
    /// without it uploads fail and feedback goes through GitHub issues.
    private static var uploadToken: String {
        (Bundle.main.object(forInfoDictionaryKey: "MuxaLogHubUploadToken") as? String) ?? ""
    }

    private static let endpoint: URL = {
        guard let url = URL(string: "https://loginfo.xcodeuk.com/api/v1/projects/muxa/logs") else {
            preconditionFailure("LogHub URL is invalid")
        }
        return url
    }()

    static func archive(_ payload: Payload) -> Data {
        let clipped: String
        if payload.logText.count > maximumLogCharacters {
            let tail = String(payload.logText.suffix(maximumLogCharacters))
            clipped = tail + "\n…(truncated, last \(maximumLogCharacters) chars)…\n"
        } else {
            clipped = payload.logText
        }
        let logText = DiagnosticRedactor.redact(message: clipped)
        let note = DiagnosticRedactor.redact(message: payload.note)
        let device: [String: String] = [
            "app_version": payload.appVersion,
            "device_id": payload.deviceID,
            "model": payload.model,
            "os": "ios",
            "os_version": payload.osVersion
        ]
        let deviceJSON = (try? JSONSerialization.data(
            withJSONObject: device,
            options: [.prettyPrinted, .sortedKeys]
        )) ?? Data("{\"os\":\"ios\"}".utf8)
        let noteText = note.isEmpty ? "(no description)" : note
        return StoredZip.archive([
            ("app.log", Data(logText.utf8)),
            ("device.json", deviceJSON),
            ("note.txt", Data(noteText.utf8))
        ])
    }

    /// Drops the oldest half of the log until the package fits `maximumBytes`.
    static func archiveWithinLimit(
        _ payload: Payload,
        maximumBytes: Int = maximumArchiveBytes
    ) -> Data {
        var trimmed = payload
        var zip = archive(trimmed)
        while zip.count > maximumBytes, !trimmed.logText.isEmpty {
            trimmed.logText = String(trimmed.logText.suffix(trimmed.logText.count / 2))
            zip = archive(trimmed)
        }
        return zip
    }

    /// Seconds left before another upload; 0 if none, or if the clock moved backwards.
    static func cooldownSeconds(lastUpload: TimeInterval, now: TimeInterval) -> Int {
        let left = lastUpload + uploadCooldown - now
        return left <= 0 || left > uploadCooldown ? 0 : Int(left.rounded(.up))
    }

    /// One upload a minute. Retries once on 502/503/timeout, never on 429, which only
    /// more requests would make worse.
    static func upload(_ payload: Payload) async throws -> UploadResult {
        let defaults = UserDefaults.standard
        let wait = cooldownSeconds(
            lastUpload: defaults.double(forKey: lastUploadKey),
            now: Date().timeIntervalSince1970
        )
        if wait > 0 {
            throw LogHubClientError.cooldown(seconds: wait)
        }
        let zip = archiveWithinLimit(payload)
        var lastError = LogHubClientError.transport
        for attempt in 0 ..< 2 {
            do {
                let (status, body) = try await post(zip: zip, payload: payload)
                if (200 ..< 300).contains(status) {
                    defaults.set(Date().timeIntervalSince1970, forKey: lastUploadKey)
                    return parse(body)
                }
                if status == 429 {
                    defaults.set(Date().timeIntervalSince1970, forKey: lastUploadKey)
                }
                lastError = .rejected(status: status)
                if status != 502, status != 503 {
                    throw lastError
                }
            } catch LogHubClientError.transport {
                lastError = .transport
            }
            if attempt == 0 {
                try await Task.sleep(nanoseconds: 1_000_000_000)
            }
        }
        throw lastError
    }

    private static func post(zip: Data, payload: Payload) async throws -> (Int, Data) {
        let boundary = "----MuxaLogHub" + UUID().uuidString.replacingOccurrences(of: "-", with: "")
        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request.timeoutInterval = 90
        request.setValue("Bearer \(uploadToken)", forHTTPHeaderField: "Authorization")
        request.setValue(
            "multipart/form-data; boundary=\(boundary)",
            forHTTPHeaderField: "Content-Type"
        )
        request.httpBody = multipart(zip: zip, payload: payload, boundary: boundary)
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            let status = (response as? HTTPURLResponse)?.statusCode ?? 0
            return (status, data)
        } catch let error as LogHubClientError {
            throw error
        } catch {
            throw LogHubClientError.transport
        }
    }

    private static func parse(_ body: Data) -> UploadResult {
        guard let json = try? JSONSerialization.jsonObject(with: body) as? [String: Any] else {
            return UploadResult(id: "", status: "")
        }
        return UploadResult(
            id: json["id"] as? String ?? "",
            status: json["status"] as? String ?? ""
        )
    }

    private static func multipart(zip: Data, payload: Payload, boundary: String) -> Data {
        var body = Data()
        func field(_ name: String, _ value: String) {
            body.appendUTF8("--\(boundary)\r\n")
            body.appendUTF8("Content-Disposition: form-data; name=\"\(name)\"\r\n\r\n")
            body.appendUTF8(value)
            body.appendUTF8("\r\n")
        }
        field("device_id", payload.deviceID)
        field("app_version", payload.appVersion)
        field("os", "ios")
        field("os_version", payload.osVersion)
        let note = DiagnosticRedactor.redact(message: payload.note)
        if !note.isEmpty {
            field("note", String(note.prefix(2000)))
        }
        body.appendUTF8("--\(boundary)\r\n")
        body.appendUTF8(
            "Content-Disposition: form-data; name=\"file\"; filename=\"muxa-diag.zip\"\r\n"
        )
        body.appendUTF8("Content-Type: application/zip\r\n\r\n")
        body.append(zip)
        body.appendUTF8("\r\n--\(boundary)--\r\n")
        return body
    }
}

private enum StoredZip {
    static func archive(_ files: [(String, Data)]) -> Data {
        var local = Data()
        var central = Data()
        var offset: UInt32 = 0
        for (name, contents) in files {
            let nameData = Data(name.utf8)
            let crc = CRC32.checksum(contents)
            let size = UInt32(contents.count)
            var header = Data()
            header.appendLE(UInt32(0x0403_4B50))
            header.appendLE(UInt16(20))
            header.appendLE(UInt16(0))
            header.appendLE(UInt16(0))
            header.appendLE(UInt16(0))
            header.appendLE(UInt16(0))
            header.appendLE(crc)
            header.appendLE(size)
            header.appendLE(size)
            header.appendLE(UInt16(nameData.count))
            header.appendLE(UInt16(0))
            header.append(nameData)

            var directory = Data()
            directory.appendLE(UInt32(0x0201_4B50))
            directory.appendLE(UInt16(20))
            directory.appendLE(UInt16(20))
            directory.appendLE(UInt16(0))
            directory.appendLE(UInt16(0))
            directory.appendLE(UInt16(0))
            directory.appendLE(UInt16(0))
            directory.appendLE(crc)
            directory.appendLE(size)
            directory.appendLE(size)
            directory.appendLE(UInt16(nameData.count))
            directory.appendLE(UInt16(0))
            directory.appendLE(UInt16(0))
            directory.appendLE(UInt16(0))
            directory.appendLE(UInt16(0))
            directory.appendLE(UInt32(0))
            directory.appendLE(offset)
            directory.append(nameData)

            local.append(header)
            local.append(contents)
            central.append(directory)
            offset += UInt32(header.count + contents.count)
        }

        var end = Data()
        end.appendLE(UInt32(0x0605_4B50))
        end.appendLE(UInt16(0))
        end.appendLE(UInt16(0))
        end.appendLE(UInt16(files.count))
        end.appendLE(UInt16(files.count))
        end.appendLE(UInt32(central.count))
        end.appendLE(UInt32(local.count))
        end.appendLE(UInt16(0))
        local.append(central)
        local.append(end)
        return local
    }
}

private enum CRC32 {
    static func checksum(_ data: Data) -> UInt32 {
        var crc: UInt32 = 0xFFFF_FFFF
        for byte in data {
            let index = Int((crc ^ UInt32(byte)) & 0xFF)
            crc = table[index] ^ (crc >> 8)
        }
        return crc ^ 0xFFFF_FFFF
    }

    private static let table: [UInt32] = (0 ..< 256).map { index in
        var value = UInt32(index)
        for _ in 0 ..< 8 {
            if value & 1 == 1 {
                value = 0xEDB8_8320 ^ (value >> 1)
            } else {
                value >>= 1
            }
        }
        return value
    }
}

private extension Data {
    mutating func appendLE(_ value: UInt16) {
        var little = value.littleEndian
        Swift.withUnsafeBytes(of: &little) { append(contentsOf: $0) }
    }

    mutating func appendLE(_ value: UInt32) {
        var little = value.littleEndian
        Swift.withUnsafeBytes(of: &little) { append(contentsOf: $0) }
    }

    mutating func appendUTF8(_ text: String) {
        append(Data(text.utf8))
    }
}
