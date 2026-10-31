@testable import Muxa
import XCTest

final class NativeSSHBuildProbeTests: XCTestCase {
    func testPinnedNativeLibrariesCompileAndLink() {
        XCTAssertEqual(NativeSSHBuildProbe.libssh2Version, "1.11.1")
        XCTAssertTrue(NativeSSHBuildProbe.canInitializeCryptoBackend())
    }
}
