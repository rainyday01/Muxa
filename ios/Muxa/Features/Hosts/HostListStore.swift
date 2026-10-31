import Foundation
import Observation

@MainActor
@Observable
final class HostListStore {
    private let repository: any HostRepository
    private var observationTask: Task<Void, Never>?

    private(set) var hosts = [Host]()
    private(set) var isLoading = true
    private(set) var errorMessage: String?

    init(repository: any HostRepository) {
        self.repository = repository
    }

    func start() async {
        guard observationTask == nil else { return }
        observationTask = Task { [weak self, repository] in
            do {
                let stream = try await repository.observeAll()
                for await hosts in stream {
                    guard let self else { return }
                    self.hosts = hosts
                    isLoading = false
                    errorMessage = nil
                }
            } catch {
                guard let self else { return }
                isLoading = false
                errorMessage = "Hosts could not be loaded."
            }
        }
    }

    func reload() async {
        do {
            hosts = try await repository.fetchAll()
            errorMessage = nil
        } catch {
            errorMessage = "Hosts could not be refreshed."
        }
        isLoading = false
    }
}
