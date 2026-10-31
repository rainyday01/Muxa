import Foundation

enum InstallCommands {
    static func command(for packageManager: RemotePackageManager?) -> String {
        switch packageManager {
        case .apt:
            "sudo apt-get update && sudo apt-get install -y tmux"
        case .dnf:
            "sudo dnf install -y tmux"
        case .yum:
            "sudo yum install -y tmux"
        case .pacman:
            "sudo pacman -S --noconfirm tmux"
        case .apk:
            "sudo apk add tmux"
        case .brew:
            "brew install tmux"
        case .unknown, nil:
            "# Install tmux with your package manager, then probe again."
        }
    }

    /// Lines for ~/.zshenv so a Mac Terminal and SSH share Muxa's tmux server.
    static let desktopTakeoverSnippet = """
    # Muxa: one tmux server for Terminal, SSH, and the app.
    export TMUX_TMPDIR="${TMUX_TMPDIR:-/tmp}"
    """

    static func tip(tmuxInstalled: Bool) -> String {
        if tmuxInstalled {
            return "tmux is ready. Muxa connects over SSH."
        }
        return "Open the install terminal, review the command, press return, and enter sudo yourself."
    }
}
