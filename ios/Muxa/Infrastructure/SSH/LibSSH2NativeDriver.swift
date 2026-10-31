import CLibSSH2
import Darwin
import Foundation

enum SSHAuthentication: Sendable {
    case password(String)
    case publicKey(publicKey: String?, privateKey: String, passphrase: String?)
}

enum LibSSH2DriverError: Error, Equatable {
    case alreadyConnected
    case addressResolution(Int32)
    case connectionFailed(Int32)
    case libraryInitialization(Int32)
    case sessionInitialization
    case operation(name: String, code: Int32)
    case operationTimedOut(String)
    case hostKeyUnavailable
    case hostKeyRejected(SSHObservedHostKey)
    case ptyAlreadyOpen
    case ptyNotOpen
}

final class LibSSH2NativeDriver: LibSSH2SessionDriving, @unchecked Sendable {
    typealias HostKeyVerifier = @Sendable (_ sha256Fingerprint: String, _ keyType: Int32) -> Bool

    private let authentication: SSHAuthentication
    private let hostKeyVerifier: HostKeyVerifier
    private let timeoutMilliseconds: UInt

    var socketDescriptor: Int32 = -1
    var session: OpaquePointer?
    var ptyChannel: OpaquePointer?
    private var libraryInitialized = false
    init(
        authentication: SSHAuthentication,
        timeoutMilliseconds: UInt = 15000,
        hostKeyVerifier: @escaping HostKeyVerifier
    ) {
        self.authentication = authentication
        self.timeoutMilliseconds = timeoutMilliseconds
        self.hostKeyVerifier = hostKeyVerifier
    }

    func connect(_ request: SSHConnectionRequest) throws {
        guard session == nil, socketDescriptor == -1 else {
            throw LibSSH2DriverError.alreadyConnected
        }

        do {
            let initialization = libssh2_init(0)
            guard initialization == 0 else {
                throw LibSSH2DriverError.libraryInitialization(initialization)
            }
            libraryInitialized = true

            socketDescriptor = try Self.openSocket(
                hostname: request.hostname,
                port: request.port,
                timeoutMilliseconds: timeoutMilliseconds
            )
            guard let newSession = libssh2_session_init_ex(nil, nil, nil, nil) else {
                throw LibSSH2DriverError.sessionInitialization
            }
            session = newSession
            libssh2_session_set_blocking(newSession, 1)
            libssh2_session_set_timeout(newSession, Int(timeoutMilliseconds))
            try check(
                libssh2_session_method_pref(
                    newSession,
                    1,
                    "ssh-ed25519,ecdsa-sha2-nistp256,rsa-sha2-512,rsa-sha2-256"
                ),
                operation: "host-key-preference"
            )

            try check(
                libssh2_session_handshake(newSession, socketDescriptor),
                operation: "handshake"
            )
            let hostKey = try hostKeyFingerprint(session: newSession)
            let observedHostKey = SSHObservedHostKey(
                fingerprint: hostKey.fingerprint,
                keyType: SSHKeyTypeName.name(for: hostKey.type)
            )
            guard hostKeyVerifier(hostKey.fingerprint, hostKey.type) else {
                throw LibSSH2DriverError.hostKeyRejected(observedHostKey)
            }
            try authenticate(session: newSession, username: request.username)
            libssh2_session_set_blocking(newSession, 0)
        } catch {
            close()
            throw error
        }
    }

    func execute(_ command: String) throws -> SSHExecResult {
        let channel = try openSessionChannel()
        do {
            try perform(operation: "exec") {
                command.withCString { commandPointer in
                    libssh2_channel_process_startup(
                        channel,
                        "exec",
                        4,
                        commandPointer,
                        UInt32(command.utf8.count)
                    )
                }
            }

            let output = try readCommandOutput(channel: channel)
            try perform(operation: "exec-wait-eof") {
                libssh2_channel_wait_eof(channel)
            }
            try perform(operation: "exec-close") {
                libssh2_channel_close(channel)
            }
            try perform(operation: "exec-wait-closed") {
                libssh2_channel_wait_closed(channel)
            }
            let exitStatus = Int32(libssh2_channel_get_exit_status(channel))
            try perform(operation: "exec-free") {
                libssh2_channel_free(channel)
            }
            return SSHExecResult(
                stdout: output.stdout,
                stderr: output.stderr,
                exitStatus: exitStatus
            )
        } catch {
            closeChannel(channel)
            throw error
        }
    }

    func openPTY(_ request: SSHPTYRequest) throws {
        guard ptyChannel == nil else { throw LibSSH2DriverError.ptyAlreadyOpen }
        let channel = try openSessionChannel()
        do {
            try perform(operation: "request-pty") {
                request.terminal.withCString { terminalPointer in
                    libssh2_channel_request_pty_ex(
                        channel,
                        terminalPointer,
                        UInt32(request.terminal.utf8.count),
                        nil,
                        0,
                        request.columns,
                        request.rows,
                        request.pixelWidth,
                        request.pixelHeight
                    )
                }
            }

            if let command = request.command {
                try perform(operation: "pty-exec") {
                    command.withCString { commandPointer in
                        libssh2_channel_process_startup(
                            channel,
                            "exec",
                            4,
                            commandPointer,
                            UInt32(command.utf8.count)
                        )
                    }
                }
            } else {
                try perform(operation: "shell") {
                    libssh2_channel_process_startup(channel, "shell", 5, nil, 0)
                }
            }
            ptyChannel = channel
        } catch {
            closeChannel(channel)
            throw error
        }
    }

    func resizePTY(columns: Int32, rows: Int32, pixelWidth: Int32, pixelHeight: Int32) throws {
        guard let ptyChannel else { throw LibSSH2DriverError.ptyNotOpen }
        try perform(operation: "window-change") {
            libssh2_channel_request_pty_size_ex(
                ptyChannel,
                columns,
                rows,
                pixelWidth,
                pixelHeight
            )
        }
    }

    func close() {
        if let ptyChannel {
            closeChannel(ptyChannel)
            self.ptyChannel = nil
        }
        if let session {
            libssh2_session_set_blocking(session, 0)
            try? perform(operation: "disconnect", timeoutMilliseconds: 500) {
                libssh2_session_disconnect_ex(
                    session,
                    Int32(SSH_DISCONNECT_BY_APPLICATION),
                    "Muxa closing",
                    ""
                )
            }
            try? perform(operation: "session-free", timeoutMilliseconds: 500) {
                libssh2_session_free(session)
            }
            self.session = nil
        }
        if socketDescriptor >= 0 {
            Darwin.close(socketDescriptor)
            socketDescriptor = -1
        }
        if libraryInitialized {
            libssh2_exit()
            libraryInitialized = false
        }
    }
}

extension LibSSH2NativeDriver {
    private func authenticate(session: OpaquePointer, username: String) throws {
        switch authentication {
        case let .password(password):
            let result = passwordAuthentication(session: session, username: username, password: password)
            try check(result, operation: "password-authentication")
        case let .publicKey(publicKey, privateKey, passphrase):
            let result = publicKeyAuthentication(
                session: session,
                username: username,
                publicKey: publicKey,
                privateKey: privateKey,
                passphrase: passphrase
            )
            try check(result, operation: "public-key-authentication")
        }
    }

    private func passwordAuthentication(session: OpaquePointer, username: String, password: String) -> Int32 {
        username.withCString { usernamePointer in
            password.withCString { passwordPointer in
                libssh2_userauth_password_ex(
                    session,
                    usernamePointer,
                    UInt32(username.utf8.count),
                    passwordPointer,
                    UInt32(password.utf8.count),
                    nil
                )
            }
        }
    }

    private func publicKeyAuthentication(
        session: OpaquePointer,
        username: String,
        publicKey: String?,
        privateKey: String,
        passphrase: String?
    ) -> Int32 {
        let authenticate: (UnsafePointer<CChar>?) -> Int32 = { passphrasePointer in
            username.withCString { usernamePointer in
                privateKey.withCString { privateKeyPointer in
                    if let publicKey {
                        return publicKey.withCString { publicKeyPointer in
                            libssh2_userauth_publickey_frommemory(
                                session,
                                usernamePointer,
                                username.utf8.count,
                                publicKeyPointer,
                                publicKey.utf8.count,
                                privateKeyPointer,
                                privateKey.utf8.count,
                                passphrasePointer
                            )
                        }
                    }
                    return libssh2_userauth_publickey_frommemory(
                        session,
                        usernamePointer,
                        username.utf8.count,
                        nil,
                        0,
                        privateKeyPointer,
                        privateKey.utf8.count,
                        passphrasePointer
                    )
                }
            }
        }
        return if let passphrase {
            passphrase.withCString(authenticate)
        } else {
            authenticate(nil)
        }
    }

    private func openSessionChannel() throws -> OpaquePointer {
        guard let session else {
            throw LibSSH2DriverError.operation(name: "open-channel", code: Int32(LIBSSH2_ERROR_BAD_USE))
        }
        let deadline = makeDeadline()
        while true {
            if let channel = libssh2_channel_open_ex(
                session,
                "session",
                7,
                2 * 1024 * 1024,
                32 * 1024,
                nil,
                0
            ) {
                return channel
            }
            let code = libssh2_session_last_errno(session)
            guard code == Int32(LIBSSH2_ERROR_EAGAIN) else {
                throw LibSSH2DriverError.operation(name: "open-channel", code: code)
            }
            try waitForSocket(operation: "open-channel", deadline: deadline)
        }
    }

    private func readCommandOutput(channel: OpaquePointer) throws -> (stdout: Data, stderr: Data) {
        var stdout = Data()
        var stderr = Data()
        var buffer = [CChar](repeating: 0, count: 32 * 1024)
        let deadline = makeDeadline()
        while true {
            let stdoutCount = libssh2_channel_read_ex(channel, 0, &buffer, buffer.count)
            try appendRead(stdoutCount, buffer: buffer, output: &stdout, operation: "read-stdout")
            let stderrCount = libssh2_channel_read_ex(
                channel,
                Int32(SSH_EXTENDED_DATA_STDERR),
                &buffer,
                buffer.count
            )
            try appendRead(stderrCount, buffer: buffer, output: &stderr, operation: "read-stderr")
            if libssh2_channel_eof(channel) == 1 {
                return (stdout, stderr)
            }
            if stdoutCount <= 0, stderrCount <= 0 {
                try waitForSocket(operation: "read", deadline: deadline)
            }
        }
    }

    private func appendRead(
        _ count: Int,
        buffer: [CChar],
        output: inout Data,
        operation: String
    ) throws {
        if count > 0 {
            buffer.withUnsafeBytes { bytes in
                output.append(contentsOf: bytes.prefix(count))
            }
        } else if count < 0, count != Int(LIBSSH2_ERROR_EAGAIN) {
            throw LibSSH2DriverError.operation(name: operation, code: Int32(count))
        }
    }

    private func perform(
        operation: String,
        timeoutMilliseconds: UInt? = nil,
        _ body: () -> Int32
    ) throws {
        let deadline = makeDeadline(timeoutMilliseconds: timeoutMilliseconds)
        while true {
            let code = body()
            if code == 0 {
                return
            }
            guard code == Int32(LIBSSH2_ERROR_EAGAIN) else {
                throw LibSSH2DriverError.operation(name: operation, code: code)
            }
            try waitForSocket(operation: operation, deadline: deadline)
        }
    }

    func makeDeadline(timeoutMilliseconds override: UInt? = nil) -> UInt64 {
        let duration = override ?? timeoutMilliseconds
        return DispatchTime.now().uptimeNanoseconds + UInt64(duration) * 1_000_000
    }

    func waitForSocket(operation: String, deadline: UInt64) throws {
        guard let session else {
            throw LibSSH2DriverError.operation(name: operation, code: Int32(LIBSSH2_ERROR_BAD_USE))
        }
        let now = DispatchTime.now().uptimeNanoseconds
        guard now < deadline else { throw LibSSH2DriverError.operationTimedOut(operation) }
        let remaining = min(250, max(1, Int32((deadline - now) / 1_000_000)))
        let directions = libssh2_session_block_directions(session)
        var events: Int16 = 0
        if directions & Int32(LIBSSH2_SESSION_BLOCK_INBOUND) != 0 {
            events |= Int16(POLLIN)
        }
        if directions & Int32(LIBSSH2_SESSION_BLOCK_OUTBOUND) != 0 {
            events |= Int16(POLLOUT)
        }
        if events == 0 {
            events = Int16(POLLIN | POLLOUT)
        }
        var descriptor = pollfd(fd: socketDescriptor, events: events, revents: 0)
        let result = Darwin.poll(&descriptor, 1, remaining)
        if result == 0 {
            if DispatchTime.now().uptimeNanoseconds >= deadline {
                throw LibSSH2DriverError.operationTimedOut(operation)
            }
            return
        }
        if result < 0, errno != EINTR {
            throw LibSSH2DriverError.operation(name: operation, code: errno)
        }
    }

    private func hostKeyFingerprint(session: OpaquePointer) throws -> (fingerprint: String, type: Int32) {
        var length = 0
        var type: Int32 = 0
        guard let key = libssh2_session_hostkey(session, &length, &type), length > 0 else {
            throw LibSSH2DriverError.hostKeyUnavailable
        }
        return (HostKeyFingerprint.sha256(Data(bytes: key, count: length)), type)
    }

    private func check(_ code: Int32, operation: String) throws {
        guard code == 0 else {
            throw LibSSH2DriverError.operation(name: operation, code: code)
        }
    }

    private func closeChannel(_ channel: OpaquePointer) {
        try? perform(operation: "channel-close", timeoutMilliseconds: 500) {
            libssh2_channel_close(channel)
        }
        try? perform(operation: "channel-free", timeoutMilliseconds: 500) {
            libssh2_channel_free(channel)
        }
    }

    private static func openSocket(hostname: String, port: UInt16, timeoutMilliseconds: UInt) throws -> Int32 {
        var hints = addrinfo()
        hints.ai_family = AF_UNSPEC
        hints.ai_socktype = SOCK_STREAM
        hints.ai_protocol = IPPROTO_TCP

        var addresses: UnsafeMutablePointer<addrinfo>?
        let resolution = getaddrinfo(hostname, String(port), &hints, &addresses)
        guard resolution == 0 else {
            throw LibSSH2DriverError.addressResolution(resolution)
        }
        defer { freeaddrinfo(addresses) }

        var address = addresses
        var lastError = ECONNREFUSED
        while let current = address?.pointee {
            let descriptor = socket(current.ai_family, current.ai_socktype, current.ai_protocol)
            if descriptor >= 0 {
                var timeout = timeval(
                    tv_sec: Int(timeoutMilliseconds / 1000),
                    tv_usec: Int32((timeoutMilliseconds % 1000) * 1000)
                )
                _ = withUnsafePointer(to: &timeout) { timeoutPointer in
                    setsockopt(
                        descriptor,
                        SOL_SOCKET,
                        SO_RCVTIMEO,
                        timeoutPointer,
                        socklen_t(MemoryLayout<timeval>.size)
                    )
                }
                _ = withUnsafePointer(to: &timeout) { timeoutPointer in
                    setsockopt(
                        descriptor,
                        SOL_SOCKET,
                        SO_SNDTIMEO,
                        timeoutPointer,
                        socklen_t(MemoryLayout<timeval>.size)
                    )
                }
                if Darwin.connect(descriptor, current.ai_addr, current.ai_addrlen) == 0 {
                    return descriptor
                }
                lastError = errno
                Darwin.close(descriptor)
            } else {
                lastError = errno
            }
            address = current.ai_next
        }
        throw LibSSH2DriverError.connectionFailed(lastError)
    }
}
