import CLibSSH2

enum NativeSSHBuildProbe {
    static var libssh2Version: String {
        guard let version = libssh2_version(0) else { return "unavailable" }
        return String(cString: version)
    }

    static func canInitializeCryptoBackend() -> Bool {
        guard libssh2_init(0) == 0 else { return false }
        libssh2_exit()
        return true
    }
}
