@testable import Muxa
import XCTest

final class HostFieldSanitizerTests: XCTestCase {
    func testSplitsPasswordGluedToIPv4() {
        let parsed = HostFieldSanitizer.parse("203.0.113.32Secret~")
        XCTAssertEqual(parsed.host, "203.0.113.32")
        XCTAssertEqual(parsed.password, "Secret~")
    }

    func testParsesMultilineHostAndPassword() {
        let parsed = HostFieldSanitizer.parse("203.0.113.32\nSecret~")
        XCTAssertEqual(parsed.host, "203.0.113.32")
        XCTAssertEqual(parsed.password, "Secret~")
        XCTAssertTrue(HostFieldSanitizer.isPlausibleHost(parsed.host))
    }

    func testParsesCredentialsAndPort() {
        let parsed = HostFieldSanitizer.parse("root:secret@example.invalid:2222")
        XCTAssertEqual(parsed.host, "example.invalid")
        XCTAssertEqual(parsed.username, "root")
        XCTAssertEqual(parsed.password, "secret")
        XCTAssertEqual(parsed.port, 2222)
    }

    func testParsesSSHURLWithIPv6() {
        let parsed = HostFieldSanitizer.parse("ssh://dev:secret@[2001:db8::1]:2200/home")
        XCTAssertEqual(parsed.host, "2001:db8::1")
        XCTAssertEqual(parsed.username, "dev")
        XCTAssertEqual(parsed.password, "secret")
        XCTAssertEqual(parsed.port, 2200)
        XCTAssertTrue(HostFieldSanitizer.isPlausibleHost(parsed.host))
    }

    func testParsesCommonSSHCommand() {
        let parsed = HostFieldSanitizer.parse("ssh -p 2222 -l deploy example.invalid")
        XCTAssertEqual(parsed.host, "example.invalid")
        XCTAssertEqual(parsed.username, "deploy")
        XCTAssertEqual(parsed.port, 2222)
    }

    func testRejectsInvalidHosts() {
        XCTAssertFalse(HostFieldSanitizer.isPlausibleHost("256.1.1.1"))
        XCTAssertFalse(HostFieldSanitizer.isPlausibleHost("bad host"))
        XCTAssertFalse(HostFieldSanitizer.isPlausibleHost("-bad.example"))
        XCTAssertFalse(HostFieldSanitizer.isPlausibleHost("example.invalid/command"))
    }

    func testAcceptsHostnameIPv4AndIPv6() {
        XCTAssertTrue(HostFieldSanitizer.isPlausibleHost("example.invalid"))
        XCTAssertTrue(HostFieldSanitizer.isPlausibleHost("192.0.2.1"))
        XCTAssertTrue(HostFieldSanitizer.isPlausibleHost("2001:db8::1"))
    }
}
