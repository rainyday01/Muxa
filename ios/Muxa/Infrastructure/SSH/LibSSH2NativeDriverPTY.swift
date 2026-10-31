import CLibSSH2
import Foundation

extension LibSSH2NativeDriver {
    func readPTY(maxBytes: Int) throws -> Data? {
        guard let ptyChannel else { throw LibSSH2DriverError.ptyNotOpen }
        guard maxBytes > 0 else { return Data() }
        if libssh2_channel_eof(ptyChannel) == 1 {
            return nil
        }

        var buffer = [CChar](repeating: 0, count: maxBytes)
        let count = libssh2_channel_read_ex(ptyChannel, 0, &buffer, buffer.count)
        if count > 0 {
            return buffer.withUnsafeBytes { bytes in
                guard let baseAddress = bytes.baseAddress else { return Data() }
                return Data(bytes: baseAddress, count: count)
            }
        }
        if count == 0 {
            return libssh2_channel_eof(ptyChannel) == 1 ? nil : Data()
        }
        if count == Int(LIBSSH2_ERROR_EAGAIN) {
            return Data()
        }
        throw LibSSH2DriverError.operation(name: "read-pty", code: Int32(count))
    }

    func writePTY(_ data: Data) throws {
        guard let ptyChannel else { throw LibSSH2DriverError.ptyNotOpen }
        guard !data.isEmpty else { return }

        var offset = 0
        let deadline = makeDeadline()
        while offset < data.count {
            let count = data.withUnsafeBytes { bytes in
                let typedBytes = bytes.bindMemory(to: CChar.self)
                guard let baseAddress = typedBytes.baseAddress else { return 0 }
                let remaining = typedBytes.count - offset
                return libssh2_channel_write_ex(
                    ptyChannel,
                    0,
                    baseAddress.advanced(by: offset),
                    remaining
                )
            }
            if count > 0 {
                offset += count
                continue
            }
            guard count == Int(LIBSSH2_ERROR_EAGAIN) || count == 0 else {
                throw LibSSH2DriverError.operation(name: "write-pty", code: Int32(count))
            }
            try waitForSocket(operation: "write-pty", deadline: deadline)
        }
    }
}
