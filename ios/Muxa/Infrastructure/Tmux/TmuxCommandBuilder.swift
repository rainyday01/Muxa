import Foundation

struct TmuxCommandBuilder: Sendable {
    let executable: String

    init(executable: String = "tmux") {
        self.executable = executable
    }

    func listSessions(socketPath: String? = nil) -> String {
        let format = Self.sessionListFormat
        if let socketPath, !socketPath.isEmpty {
            return command(
                socketPath: socketPath,
                arguments: ["list-sessions", "-F", format]
            )
        }
        // Walk every tmux-$uid server. A missing server is an empty list, not a
        // failed refresh. The socket path is appended so each row stays attached
        // to the server that owns it.
        let helper = TmuxSocketDiscovery().shellHelper
        return """
        \(helper)
        _mtmux_socks | while read -r _s; do
          tmux -S "$_s" list-sessions -F "\(format)\t${_s}" || true
        done
        if [ -z "$(_mtmux_socks | head -n 1)" ]; then
          tmux list-sessions -F "\(format)" || true
        fi
        true
        """
    }

    static let sessionListFormat =
        "#{session_name}\t#{session_windows}\t#{session_attached}\t#{session_created}\t#{session_activity}"

    func listWindows(target: TmuxAttachTarget) -> String {
        command(
            socketPath: target.socketPath,
            arguments: [
                "list-windows",
                "-t",
                target.sessionName,
                "-F",
                "#{window_index}\t#{window_name}\t#{window_active}\t#{window_layout}\t#{window_panes}"
            ]
        )
    }

    func listPanes(target: TmuxAttachTarget, windowIndex: Int? = nil) -> String {
        let targetName = windowIndex.map { "\(target.sessionName):\($0)" } ?? target.sessionName
        return command(
            socketPath: target.socketPath,
            arguments: [
                "list-panes",
                "-t",
                targetName,
                "-F",
                "#{pane_index}\t#{pane_active}\t#{pane_title}\t#{pane_current_command}\t" +
                    "#{pane_current_path}\t#{pane_width}\t#{pane_height}\t#{pane_pid}"
            ]
        )
    }

    func capturePane(target: TmuxAttachTarget, lineLimit _: Int = 0) -> String {
        command(
            socketPath: target.socketPath,
            arguments: [
                "capture-pane",
                "-p",
                "-J",
                "-t",
                target.sessionName
            ]
        )
    }

    func paneScrollMetadata(target: TmuxAttachTarget) -> String {
        command(
            socketPath: target.socketPath,
            arguments: [
                "display-message",
                "-p",
                "-t",
                target.sessionName,
                "#{alternate_on} #{history_size} #{pane_height}"
            ]
        )
    }

    func pageHistory(target: TmuxAttachTarget, scrollUp: Bool, lines: Int) -> String {
        let count = min(80, max(1, lines))
        let direction = scrollUp ? "scroll-up" : "scroll-down"
        let enterCopyMode = command(
            socketPath: target.socketPath,
            arguments: ["copy-mode", "-t", target.sessionName]
        )
        let page = command(
            socketPath: target.socketPath,
            arguments: [
                "send-keys",
                "-t",
                target.sessionName,
                "-X",
                "-N",
                String(count),
                direction
            ]
        )
        return "\(enterCopyMode) >/dev/null 2>&1 || true; \(page) >/dev/null 2>&1 || true"
    }

    func cancelCopyMode(target: TmuxAttachTarget) -> String {
        command(
            socketPath: target.socketPath,
            arguments: [
                "send-keys",
                "-t",
                target.sessionName,
                "-X",
                "cancel"
            ]
        )
    }

    func newSession(
        socketPath: String?,
        sessionName: String,
        workingDirectory: String?,
        initialCommand: String?
    ) -> String {
        var arguments = ["new-session", "-d", "-s", sessionName]
        if let workingDirectory {
            arguments += ["-c", workingDirectory]
        }
        if let initialCommand, !initialCommand.isEmpty {
            arguments.append(initialCommand)
        }
        if let socketPath, !socketPath.isEmpty {
            return command(socketPath: socketPath, arguments: arguments)
        }
        let helper = TmuxSocketDiscovery().shellHelper
        let quoted = arguments.map(ShellQuoting.singleQuote).joined(separator: " ")
        return """
        \(helper)
        _base=$(_mtmux_canonical_base)
        export TMUX_TMPDIR="$_base"
        unset TMPDIR
        mkdir -p "$TMUX_TMPDIR"
        tmux \(quoted)
        printf 'MUXA_SOCK %s/tmux-%s/default\\n' "$TMUX_TMPDIR" "$(id -u)"
        """
    }

    static func socketPrinted(in output: String) -> String? {
        output.split(whereSeparator: \.isNewline).reversed().lazy.compactMap { line -> String? in
            let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
            let prefix = "MUXA_SOCK "
            guard trimmed.hasPrefix(prefix) else { return nil }
            let path = String(trimmed.dropFirst(prefix.count))
            return path.isEmpty ? nil : path
        }.first
    }

    func renameSession(target: TmuxAttachTarget, name: String) -> String {
        command(
            socketPath: target.socketPath,
            arguments: ["rename-session", "-t", target.sessionName, name]
        )
    }

    func killSession(target: TmuxAttachTarget) -> String {
        command(
            socketPath: target.socketPath,
            arguments: ["kill-session", "-t", target.sessionName]
        )
    }

    func attach(target: TmuxAttachTarget) -> String {
        command(
            socketPath: target.socketPath,
            arguments: ["attach-session", "-t", target.sessionName]
        )
    }

    private func command(socketPath: String?, arguments: [String]) -> String {
        var parts = [executable]
        if let socketPath, !socketPath.isEmpty {
            parts += ["-S", socketPath]
        }
        parts += arguments
        return parts.map(ShellQuoting.singleQuote).joined(separator: " ")
    }
}
