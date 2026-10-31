@testable import Muxa
import XCTest

final class RemoteEnvironmentProbeTests: XCTestCase {
    func testParsesRemoteCapabilities() throws {
        let output = """
        muxa_os=Linux
        muxa_tmux=tmux 3.4
        muxa_pm=apt
        """

        let snapshot = try SSHRemoteEnvironmentProbe.parse(
            Data(output.utf8),
            checkedAt: Date(timeIntervalSince1970: 10)
        )

        XCTAssertEqual(snapshot.operatingSystem, .linux)
        XCTAssertEqual(snapshot.packageManager, .apt)
        XCTAssertEqual(snapshot.tmuxVersion, "tmux 3.4")
        XCTAssertEqual(snapshot.summary, "tmux ready, 0 ms")
    }

    func testParseSamplesHostResources() throws {
        let output = """
        muxa_os=Linux
        muxa_pm=apt
        muxa_mem_avail_kb=1258291
        muxa_mem_total_kb=16384000
        muxa_disk_avail_kb=50331648
        muxa_disk_total_kb=104857600
        """

        let snapshot = try SSHRemoteEnvironmentProbe.parse(
            Data(output.utf8),
            checkedAt: Date(timeIntervalSince1970: 10)
        )

        XCTAssertEqual(
            snapshot.resources,
            HostResources(
                memAvailableKB: 1_258_291,
                memTotalKB: 16_384_000,
                diskAvailableKB: 50_331_648,
                diskTotalKB: 104_857_600
            )
        )
    }

    func testProbeCommandRequestsHostResources() {
        XCTAssertTrue(SSHRemoteEnvironmentProbe.command.contains("MemAvailable"))
        XCTAssertTrue(SSHRemoteEnvironmentProbe.command.contains("df -Pk /"))
    }

    func testInstallCommandInstallsTmuxOnly() {
        XCTAssertEqual(InstallCommands.command(for: .apt), "sudo apt-get update && sudo apt-get install -y tmux")
        XCTAssertEqual(InstallCommands.tip(tmuxInstalled: true), "tmux is ready. Muxa connects over SSH.")
    }

    func testResourceFormattingAndLowThresholds() {
        XCTAssertEqual(HostResourceText.formatKB(1_258_291), "1.2G")
        XCTAssertEqual(HostResourceText.formatKB(50_331_648), "48G")
        XCTAssertEqual(HostResourceText.formatKB(512 * 1024), "512M")
        XCTAssertEqual(HostResourceText.formatKB(80), "80K")
        XCTAssertEqual(HostResourceText.formatKB(-1), "—")

        XCTAssertTrue(HostResourceText.isMemoryLow(availableKB: 128 * 1024, totalKB: 16 * 1024 * 1024))
        XCTAssertFalse(HostResourceText.isMemoryLow(availableKB: 2 * 1024 * 1024, totalKB: 16 * 1024 * 1024))
        XCTAssertTrue(HostResourceText.isDiskLow(availableKB: 1024 * 1024, totalKB: 100 * 1024 * 1024))
        XCTAssertFalse(HostResourceText.isDiskLow(availableKB: nil, totalKB: nil))

        let resources = HostResourceText.parseKV("MEM_AVAIL_KB=2048\ndisk_avail_kb=4096")
        XCTAssertEqual(resources.memAvailableKB, 2048)
        XCTAssertEqual(resources.diskAvailableKB, 4096)
        XCTAssertFalse(resources.isEmpty)
        XCTAssertTrue(HostResources().isEmpty)
    }

    func testShellQuotingDoesNotAllowSingleQuoteInjection() {
        XCTAssertEqual(
            ShellQuoting.singleQuote("a'b"),
            "'a'\\''b'"
        )
    }
}
