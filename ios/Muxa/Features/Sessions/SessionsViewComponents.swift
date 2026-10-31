import SwiftUI

struct ModernSessionModeMenuChip: View {
    @Environment(\.muxaTheme) private var theme
    @Binding var selection: SessionListStore.ViewMode

    var body: some View {
        Menu {
            Button {
                selection = .byHost
            } label: {
                Label("By Host", systemImage: "rectangle.stack")
            }
            Button {
                selection = .recent
            } label: {
                Label("Recent", systemImage: "list.bullet")
            }
        } label: {
            HStack(spacing: 6) {
                Image(systemName: selection == .byHost ? "rectangle.stack" : "list.bullet")
                Text(selection.title)
            }
            .font(.system(size: 13, weight: .medium))
            .foregroundStyle(theme.onSurface)
            .padding(.horizontal, 13)
            .padding(.vertical, 8)
            .background(theme.isDark ? theme.card : Color(hex: 0xEADDFF), in: Capsule())
            .overlay(Capsule().stroke(theme.outline.opacity(0.28), lineWidth: 0.7))
        }
        .buttonStyle(.plain)
    }
}

struct ModernSessionModeChip: View {
    @Environment(\.muxaTheme) private var theme
    let title: String
    let systemImage: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: systemImage)
                Text(title)
            }
            .font(.system(size: 13, weight: .medium))
            .foregroundStyle(isSelected ? theme.onSurface : theme.onSurfaceMuted)
            .padding(.horizontal, 13)
            .padding(.vertical, 8)
            .background(
                isSelected ? (theme.isDark ? Color(hex: 0x4F378B) : Color(hex: 0xEADDFF)) : .clear,
                in: Capsule()
            )
            .overlay(Capsule().stroke(theme.outline.opacity(isSelected ? 0.18 : 0.55), lineWidth: 0.8))
        }
        .buttonStyle(.plain)
    }
}

struct ModernHostGroupHeader: View {
    @Environment(\.muxaTheme) private var theme
    let title: String
    let resources: HostResources?

    var body: some View {
        HStack(spacing: 8) {
            Text(title.uppercased())
                .font(.system(size: 12, weight: .semibold))
                .tracking(1.0)
                .foregroundStyle(theme.primary)
            Spacer(minLength: 8)
            ModernHostResourceChips(resources: resources)
        }
    }
}

struct ModernHostResourceChips: View {
    @Environment(\.muxaTheme) private var theme
    let resources: HostResources?

    var body: some View {
        if let resources, !resources.isEmpty {
            HStack(spacing: 8) {
                if let mem = resources.memAvailableKB {
                    Text("RAM \(HostResourceText.formatKB(mem))")
                        .foregroundStyle(
                            HostResourceText.isMemoryLow(availableKB: mem, totalKB: resources.memTotalKB)
                                ? Color.orange : theme.onSurfaceMuted
                        )
                }
                if let disk = resources.diskAvailableKB {
                    Text("Disk \(HostResourceText.formatKB(disk))")
                        .foregroundStyle(
                            HostResourceText.isDiskLow(availableKB: disk, totalKB: resources.diskTotalKB)
                                ? Color.orange : theme.onSurfaceMuted
                        )
                }
            }
            .font(.system(size: 11, weight: .regular, design: .monospaced))
        }
    }
}

struct ModernSessionCard: View {
    @Environment(\.muxaTheme) private var theme
    let item: TmuxSessionCacheItem
    let isRecent: Bool
    let resources: HostResources?
    let onSelect: () -> Void
    let onEnter: () -> Void
    let onMore: () -> Void
    let recencyLabel: String

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            cardHeader
                .contentShape(Rectangle())
                .onTapGesture(perform: onSelect)

            if isRecent {
                HStack(spacing: 8) {
                    if let alias = item.session.hostAlias {
                        Text(alias.uppercased())
                            .font(.system(size: 11, weight: .medium))
                            .tracking(0.5)
                            .foregroundStyle(theme.primary)
                            .lineLimit(1)
                    }
                    Spacer(minLength: 8)
                    ModernHostResourceChips(resources: resources)
                }
                .padding(.top, 8)
            }

            if let snapshot = SnapshotText.forCard(item.session.snapshotText) {
                Text(snapshot)
                    .font(.system(size: 11, weight: .regular, design: .monospaced))
                    .lineSpacing(1)
                    .foregroundStyle(terminalForeground)
                    .lineLimit(SnapshotText.cardLines)
                    .truncationMode(.tail)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 5)
                    .background(terminalBackground, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                    .padding(.top, 10)
                    .accessibilityIdentifier("sessions.snapshot.\(item.session.id)")
            }

            HStack(spacing: 8) {
                Button("Enter", action: onEnter)
                    .buttonStyle(.borderedProminent)
                    .controlSize(.small)
                    .tint(theme.primary)
                    .accessibilityIdentifier("sessions.enter.\(item.session.id)")
                Button("More", action: onMore)
                    .buttonStyle(.borderless)
                    .foregroundStyle(theme.primary)
                    .accessibilityIdentifier("sessions.more.\(item.session.id)")
            }
            .padding(.top, 8)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(theme.card, in: RoundedRectangle(cornerRadius: theme.cardRadius, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: theme.cardRadius, style: .continuous)
                .stroke(theme.outline.opacity(theme.isDark ? 0.35 : 0.18), lineWidth: 0.7)
        }
        .shadow(color: .black.opacity(theme.isDark ? 0.24 : 0.10), radius: 5, y: 2)
        .padding(.horizontal, 16)
        .padding(.bottom, 10)
        .accessibilityIdentifier("sessions.row.\(item.session.id)")
    }

    private var cardHeader: some View {
        HStack(spacing: 10) {
            Circle()
                .fill(item.isStale ? Color.orange : Color.green)
                .frame(width: 10, height: 10)
                .accessibilityLabel(item.isStale ? "Cached session" : "Fresh session")

            VStack(alignment: .leading, spacing: 3) {
                Text(item.session.name)
                    .font(.system(size: 17, weight: .medium))
                    .foregroundStyle(theme.onSurface)
                    .lineLimit(1)
                Text("\(item.session.windowCount) windows")
                    .font(.system(size: 12))
                    .foregroundStyle(theme.onSurfaceMuted)
            }
            Spacer(minLength: 4)
            if !recencyLabel.isEmpty {
                Text(recencyLabel)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(theme.onSurfaceMuted)
                    .lineLimit(1)
            }
        }
    }

    private var terminalBackground: Color {
        theme.isDark ? Color(hex: 0x0E0E12) : Color(hex: 0x1A1B26)
    }

    private var terminalForeground: Color {
        theme.isDark ? theme.onSurface : Color(hex: 0xA9B1D6)
    }
}
