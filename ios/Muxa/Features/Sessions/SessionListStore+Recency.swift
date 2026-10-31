import Foundation

extension SessionListStore {
    func recentDate(for item: TmuxSessionCacheItem) -> Date {
        let key = ListOrder.sessionKey(hostID: item.session.hostID, sessionName: item.session.name)
        return [
            lastOpened[key],
            item.session.lastActivityAt,
            item.session.snapshotUpdatedAt
        ]
        .compactMap(\.self)
        .max() ?? .distantPast
    }

    func isMoreRecent(_ lhs: TmuxSessionCacheItem, than rhs: TmuxSessionCacheItem) -> Bool {
        let leftDate = recentDate(for: lhs)
        let rightDate = recentDate(for: rhs)
        if leftDate != rightDate {
            return leftDate > rightDate
        }
        let leftActivity = lhs.session.lastActivityAt ?? .distantPast
        let rightActivity = rhs.session.lastActivityAt ?? .distantPast
        if leftActivity != rightActivity {
            return leftActivity > rightActivity
        }
        return lhs.session.name.localizedCaseInsensitiveCompare(rhs.session.name) == .orderedAscending
    }
}
