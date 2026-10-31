import SwiftUI

struct HostRow: View {
    let host: Host
    let onEdit: () -> Void
    let onTest: () -> Void
    let onProbe: () -> Void
    let onDelete: () -> Void
    var onInstall: () -> Void = {}

    var body: some View {
        HStack(spacing: 12) {
            Button(action: onEdit) {
                HStack(spacing: 12) {
                    Image(systemName: statusSymbol)
                        .foregroundStyle(statusColor)
                        .frame(width: 24)
                        .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(host.alias)
                            .font(.body.weight(.medium))
                        Text(endpoint)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                    Spacer(minLength: 8)
                    VStack(alignment: .trailing, spacing: 4) {
                        if let latency = host.lastLatencyMilliseconds {
                            Text("\(latency) ms")
                                .font(.caption.monospacedDigit())
                                .foregroundStyle(.secondary)
                        }
                        capabilityStatus
                    }
                }
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("hosts.edit.\(host.id)")
            Spacer(minLength: 0)
            Button(action: onTest) {
                Image(systemName: "ellipsis.circle")
                    .foregroundStyle(.secondary)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel("Test Connection")
            .accessibilityIdentifier("hosts.actions.\(host.id)")
            Button(action: onProbe) {
                Image(systemName: "arrow.triangle.2.circlepath")
                    .foregroundStyle(.secondary)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel("Probe Host")
            .accessibilityIdentifier("hosts.probe.\(host.id)")
            Button(action: onDelete) {
                Image(systemName: "trash")
                    .foregroundStyle(.secondary)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel("Delete Host")
            .accessibilityIdentifier("hosts.remove.\(host.id)")
            if host.tmuxInstalled == false {
                Button(action: onInstall) {
                    Image(systemName: "shippingbox")
                        .foregroundStyle(.orange)
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.borderless)
                .accessibilityLabel("Install Environment")
                .accessibilityIdentifier("hosts.install.\(host.id)")
            }
        }
        .padding(.vertical, 3)
    }

    private var endpoint: String {
        let port = host.port == 22 ? "" : ":\(host.port)"
        return "\(host.username)@\(host.hostname)\(port)"
    }

    private var statusSymbol: String {
        if host.tmuxInstalled == true {
            return "circle.fill"
        }
        if host.tmuxInstalled == false {
            return "exclamationmark.circle.fill"
        }
        return "circle.dotted"
    }

    private var statusColor: Color {
        if host.tmuxInstalled == true {
            return .green
        }
        if host.tmuxInstalled == false {
            return .orange
        }
        return .secondary
    }

    @ViewBuilder
    private var capabilityStatus: some View {
        if host.tmuxInstalled == false {
            Text("tmux unavailable")
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
    }
}
