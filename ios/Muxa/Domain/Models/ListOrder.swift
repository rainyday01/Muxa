import Foundation

enum ListOrder {
    static func merge(saved: [String], all: [String]) -> [String] {
        var seen = Set<String>()
        var result = [String]()
        for value in saved where all.contains(value) && seen.insert(value).inserted {
            result.append(value)
        }
        for value in all where seen.insert(value).inserted {
            result.append(value)
        }
        return result
    }

    static func move(_ values: [String], id: String, movingUp: Bool) -> [String] {
        guard let index = values.firstIndex(of: id) else { return values }
        let neighbor = movingUp ? index - 1 : index + 1
        guard values.indices.contains(neighbor) else { return values }
        var next = values
        next.swapAt(index, neighbor)
        return next
    }

    static func sessionKey(hostID: String, sessionName: String) -> String {
        "\(hostID)\t\(sessionName)"
    }
}
