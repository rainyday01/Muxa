# App Store Connect — Notes for Review draft

> Paste the reviewed, current version of this document into App Store Connect
> Notes for Review. Replace the bracketed values through the secure App Store
> Connect fields; do not put passwords, PATs, private keys, or server secrets
> in this repository.

## What Muxa does

Muxa is an SSH and tmux client for iPhone and iPad. The reviewer can add an SSH
host, confirm its host fingerprint on first connection, refresh the host's tmux
sessions, and open an interactive terminal. Host credentials are stored in the
device Keychain. Host records, snapshots, command history, snippets, and
keyboard layouts are stored locally on the device.

## Review access

- Review host: `[host supplied in App Store Connect review field]`
- Review username: `[username supplied in App Store Connect review field]`
- Review password/key: `[supplied only through the secure review field]`
- Suggested path: Hosts → add the review host → Test Connection → Verify host
  key → Trust and connect → Sessions → Refresh → open `review`.
- If the host fingerprint changes, Muxa blocks the connection and requires an
  explicit replacement confirmation.

## Remote commands and session templates

Session creation runs on the SSH host selected by the user. The app does not
silently run commands, download an installer, or download/execute code that
changes the iOS app's functionality.

- **Empty shell** starts a shell inside a tmux session.
- **Claude**, **Codex**, **Grok**, and **Antigravity** are convenience templates
  for commands expected to already exist on the user's SSH host. Muxa does not
  bundle their CLIs, include their SDKs, or provide their services.
- **Custom command** is typed by the user and sent only to the selected SSH
  host after the user creates the session.
- The optional install flow only displays a package-manager command for tmux in
  a user-opened terminal. The user reviews the command, presses Return, and
  enters `sudo` credentials themselves.
- **Skip permission checks** and **Full auto-approve** are off by default. If
  explicitly enabled, they append the corresponding documented flag to the
  remote CLI command; they do not grant Muxa additional iOS permissions.

## Third-party names and affiliation

The names Claude, Codex, Grok, and Antigravity are used descriptively to label
shortcuts for user-installed command-line tools on a user's own SSH host. Muxa
does not claim sponsorship, endorsement, partnership, or technical affiliation
with the respective projects or companies, and does not use their logos or ship
their SDKs. The release owner must confirm that this nominative compatibility
wording and the selected store metadata are appropriate for the jurisdictions
and trademarks involved before submission.

## Privacy and deletion

Muxa has no analytics SDK. Diagnostics stay on device unless the user taps
Upload log, which sends a redacted package (local log, app version, device
identifier, and an optional note). Passwords and private keys are removed
before upload. “Delete all data on this device” removes Muxa's local
hosts, credentials, known-host records, session snapshots, command history,
custom snippets, custom keyboard layouts, diagnostic entries, and preferences;
built-in keyboard layouts remain available. It does not delete data on an SSH
server.
