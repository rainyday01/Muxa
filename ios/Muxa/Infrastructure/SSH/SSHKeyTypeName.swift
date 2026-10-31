import CLibSSH2
import Foundation

enum SSHKeyTypeName {
    static func name(for type: Int32) -> String {
        switch type {
        case Int32(LIBSSH2_HOSTKEY_TYPE_RSA):
            "ssh-rsa"
        case Int32(LIBSSH2_HOSTKEY_TYPE_ECDSA_256):
            "ecdsa-sha2-nistp256"
        case Int32(LIBSSH2_HOSTKEY_TYPE_ECDSA_384):
            "ecdsa-sha2-nistp384"
        case Int32(LIBSSH2_HOSTKEY_TYPE_ECDSA_521):
            "ecdsa-sha2-nistp521"
        case Int32(LIBSSH2_HOSTKEY_TYPE_ED25519):
            "ssh-ed25519"
        default:
            "unknown"
        }
    }
}
