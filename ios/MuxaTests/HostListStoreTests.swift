@testable import Muxa
import XCTest

private actor HostListRepositoryFixture: HostRepository {
    var hosts: [Host]

    init(hosts: [Host]) {
        self.hosts = hosts
    }

    func observeAll() -> AsyncStream<[Host]> {
        let hosts = hosts
        return AsyncStream { continuation in
            continuation.yield(hosts)
            continuation.finish()
        }
    }

    func fetchAll() -> [Host] {
        hosts
    }

    func fetch(id: String) -> Host? {
        hosts.first { $0.id == id }
    }

    func save(_ host: Host) {
        hosts = [host]
    }

    func updateResources(hostID: String, resources: HostResources) {
        guard !resources.isEmpty, let index = hosts.firstIndex(where: { $0.id == hostID }) else { return }
        hosts[index] = hosts[index].applying(resources: resources)
    }

    func delete(id: String) {
        hosts.removeAll { $0.id == id }
    }

    func deleteAll() {
        hosts.removeAll()
    }
}

@MainActor
final class HostListStoreTests: XCTestCase {
    func testLoadsHostsFromRepository() async {
        let host = Host(id: "host", alias: "Lab", hostname: "lab.invalid", username: "dev")
        let store = HostListStore(repository: HostListRepositoryFixture(hosts: [host]))

        await store.reload()

        XCTAssertEqual(store.hosts, [host])
        XCTAssertFalse(store.isLoading)
        XCTAssertNil(store.errorMessage)
    }
}
