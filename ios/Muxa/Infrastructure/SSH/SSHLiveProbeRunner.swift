import Foundation
import OSLog

#if DEBUG
    enum SSHLiveProbeRunner {
        private static let logger = Logger(subsystem: "app.mtmux.ios", category: "ssh-live-probe")

        static func runIfConfigured() {
            guard let configuration = SSHLiveProbeConfiguration(environment: ProcessInfo.processInfo.environment) else {
                return
            }
            Task.detached {
                await run(configuration)
            }
        }

        private static func run(_ configuration: SSHLiveProbeConfiguration) async {
            let driver = LibSSH2NativeDriver(authentication: configuration.authentication) { actual, _ in
                configuration.acceptFirstHostKey || actual == configuration.fingerprint
            }
            let adapter = LibSSH2SessionAdapter(driver: driver)
            do {
                try await adapter.connect(configuration.connection)
                if let operation = configuration.expectedFailureOperation {
                    throw SSHLiveProbeError.expectedFailureDidNotOccur(operation)
                }
                logStage("connected", probeCase: configuration.probeCase)
                let result = try await adapter.execute("printf muxa-ssh-probe")
                logStage("exec", probeCase: configuration.probeCase)
                guard result.stdout == Data("muxa-ssh-probe".utf8), result.exitStatus == 0 else {
                    throw SSHLiveProbeError.unexpectedExecResult
                }
                try await adapter.openPTY(probePTYRequest)
                logStage("pty", probeCase: configuration.probeCase)
                try await adapter.resizePTY(columns: 120, rows: 40, pixelWidth: 1200, pixelHeight: 800)
                logStage("resized", probeCase: configuration.probeCase)
                try await requirePTYMarker("MUXA_TUI_READY", adapter: adapter)
                logStage("tui-ready", probeCase: configuration.probeCase)
                try await adapter.writePTY(Data("muxa-tui-input\n".utf8))
                try await requirePTYMarker("MUXA_TUI_ECHO:muxa-tui-input", adapter: adapter)
                logStage("tui-input-output", probeCase: configuration.probeCase)
                await adapter.close()
                logger.notice("MUXA_SSH_PROBE_OK case=\(configuration.probeCase, privacy: .public)")
            } catch {
                await adapter.close()
                log(error: error, configuration: configuration)
            }
        }

        private static var probePTYRequest: SSHPTYRequest {
            SSHPTYRequest(
                terminal: "xterm-256color",
                columns: 80,
                rows: 24,
                pixelWidth: 800,
                pixelHeight: 480,
                command: [
                    "printf '\\033[2J\\033[HMUXA_TUI_READY\\n'",
                    "IFS= read -r input",
                    "printf '\\nMUXA_TUI_ECHO:%s\\n' \"$input\"",
                    "sleep 1"
                ].joined(separator: "; ")
            )
        }

        private static func requirePTYMarker(
            _ marker: String,
            adapter: LibSSH2SessionAdapter,
            timeout: Duration = .seconds(10)
        ) async throws {
            let deadline = ContinuousClock.now.advanced(by: timeout)
            var received = Data()
            while ContinuousClock.now < deadline {
                if let data = try await adapter.readPTY(maxBytes: 16 * 1024), !data.isEmpty {
                    received.append(data)
                    if received.range(of: Data(marker.utf8)) != nil {
                        return
                    }
                }
                try await Task.sleep(for: .milliseconds(50))
            }
            throw SSHLiveProbeError.ptyMarkerTimedOut(marker)
        }

        private static func logStage(_ stage: String, probeCase: String) {
            logger.debug(
                "MUXA_SSH_PROBE_STAGE case=\(probeCase, privacy: .public) stage=\(stage, privacy: .public)"
            )
        }

        private static func log(error: Error, configuration: SSHLiveProbeConfiguration) {
            if isExpected(error: error, operation: configuration.expectedFailureOperation) {
                let probeCase = configuration.probeCase
                let operation = configuration.expectedFailureOperation ?? ""
                logger.notice("MUXA_NEG_OK case=\(probeCase, privacy: .public) op=\(operation, privacy: .public)")
            } else {
                let probeCase = configuration.probeCase
                let description = String(describing: error)
                logger.error("MUXA_SSH_FAIL case=\(probeCase, privacy: .public) error=\(description, privacy: .public)")
            }
        }

        private static func isExpected(error: Error, operation: String?) -> Bool {
            if operation == "host-key-rejected", case LibSSH2DriverError.hostKeyRejected = error {
                return true
            }
            guard case let LibSSH2DriverError.operation(name, _) = error else { return false }
            return name == operation
        }
    }

    private struct SSHLiveProbeConfiguration: Sendable {
        let connection: SSHConnectionRequest
        let fingerprint: String?
        let authentication: SSHAuthentication
        let probeCase: String
        let expectedFailureOperation: String?
        let acceptFirstHostKey: Bool

        init?(environment: [String: String]) {
            guard environment["MUXA_SSH_PROBE"] == "1",
                  let hostname = environment["MUXA_SSH_HOST"],
                  let username = environment["MUXA_SSH_USER"],
                  let authentication = Self.authentication(environment: environment)
            else { return nil }
            let acceptFirstHostKey = environment["MUXA_SSH_ACCEPT_FIRST_HOST_KEY"] == "1"
            let fingerprint = environment["MUXA_SSH_HOST_KEY_SHA256"]
            guard acceptFirstHostKey || fingerprint != nil else { return nil }
            connection = SSHConnectionRequest(hostname: hostname, port: 22, username: username)
            self.fingerprint = fingerprint
            self.authentication = authentication
            probeCase = environment["MUXA_SSH_PROBE_CASE"] ?? "unspecified"
            expectedFailureOperation = environment["MUXA_SSH_EXPECT_FAILURE_OPERATION"]
            self.acceptFirstHostKey = acceptFirstHostKey
        }

        private static func authentication(environment: [String: String]) -> SSHAuthentication? {
            if let path = environment["MUXA_SSH_PRIVATE_KEY_PATH"] {
                guard let privateKey = try? String(contentsOfFile: path, encoding: .utf8) else { return nil }
                let passphrase = environment["MUXA_SSH_PASSPHRASE_PATH"].flatMap(readPassphrase)
                return .publicKey(publicKey: nil, privateKey: privateKey, passphrase: passphrase)
            }
            return environment["MUXA_SSH_PASSWORD"].map(SSHAuthentication.password)
        }

        private static func readPassphrase(path: String) -> String? {
            try? String(contentsOfFile: path, encoding: .utf8).trimmingCharacters(in: .newlines)
        }
    }

    private enum SSHLiveProbeError: Error {
        case unexpectedExecResult
        case expectedFailureDidNotOccur(String)
        case ptyMarkerTimedOut(String)
    }
#endif
