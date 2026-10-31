import Foundation

struct TmuxSocketDiscovery: Sendable {
    let maximumDepth: Int

    init(maximumDepth: Int = 3) {
        self.maximumDepth = max(1, maximumDepth)
    }

    /// Shell function `_mtmux_socks` prints tmux server sockets only.
    /// A bare `find -type s` also returns ssh-agent and dbus sockets, and
    /// binding `tmux -S` to one of those makes session creation fail.
    var shellHelper: String {
        let depth = String(maximumDepth)
        return """
        emulate sh 2>/dev/null || true
        setopt NULL_GLOB 2>/dev/null || true
        _mtmux_socks() {
          _uid=$(id -u)
          {
            for _base in ${TMUX_TMPDIR:-} ${TMPDIR:-} /tmp ${XDG_RUNTIME_DIR:-} /run/user/$_uid; do
              [ -n "$_base" ] || continue
              _dir="$_base/tmux-$_uid"
              [ -d "$_dir" ] || continue
              for _s in "$_dir"/*; do
                [ -S "$_s" ] && printf '%s\\n' "$_s"
              done
            done
            if command -v find >/dev/null 2>&1; then
              find /tmp /run/user/$_uid -maxdepth \(depth) -type s -path "*/tmux-$_uid/*" 2>/dev/null
              if [ "$(uname -s)" = Darwin ]; then
                find /var/folders -maxdepth 6 -type s -path "*/tmux-$_uid/*" 2>/dev/null
              fi
            fi
          } | awk 'NF && !seen[$0]++'
        }
        _mtmux_canonical_base() {
          if [ -n "${TMUX_TMPDIR:-}" ]; then printf '%s\\n' "$TMUX_TMPDIR"; return 0; fi
          printf '%s\\n' /tmp
        }
        """
    }

    var command: String {
        """
        \(shellHelper)
        _mtmux_socks
        """
    }

    func parse(_ output: String) -> [TmuxServerSocket] {
        var sockets = [TmuxServerSocket]()
        var seen = Set<String>()
        for rawLine in output.split(whereSeparator: \.isNewline) {
            let fields = rawLine.split(separator: "\t", omittingEmptySubsequences: false)
            let path = String(fields[0]).trimmingCharacters(in: .whitespacesAndNewlines)
            guard !path.isEmpty, seen.insert(path).inserted else { continue }
            let source = fields.count > 1
                ? String(fields[1])
                : source(for: path)
            sockets.append(TmuxServerSocket(path: path, source: source))
        }
        return sockets
    }

    func deduplicated(_ sockets: [TmuxServerSocket]) -> [TmuxServerSocket] {
        var seen = Set<String>()
        return sockets.filter { seen.insert($0.path).inserted }
    }

    private func source(for path: String) -> String {
        let normalized = path.hasSuffix("/") ? String(path.dropLast()) : path
        if normalized.contains("/run/user/") {
            return "XDG_RUNTIME_DIR"
        }
        if normalized.contains("/tmux-") || normalized.contains("/tmux/") {
            return "TMUX_TMPDIR/TMPDIR"
        }
        return "unknown"
    }
}
