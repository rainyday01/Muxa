# Muxa (m-tmux)

**Mobile client for remote tmux sessions** — jump into Claude Code / Codex / Grok CLI and other TUI workflows on your VPS without the usual SSH → `tmux ls` → attach dance.

> **Not affiliated with the [tmux](https://github.com/tmux/tmux) project.**  
> “m-tmux” means *mobile tmux client*; see [docs/naming-and-brand.md](docs/naming-and-brand.md).

| | |
|---|---|
| **Platform (v1)** | Android (native Kotlin + Jetpack Compose) |
| **Package** | `app.mtmux.android` |
| **Transport** | SSH (sshj) today · Mosh planned |
| **Accounts** | None — keys stay on device (Android Keystore / encrypted prefs) |

---

## Why

Typical path on a phone:

```text
open SSH app → pick host → login → tmux ls / attach → finally see your session
```

**m-tmux** treats **tmux sessions as first-class citizens**: session list, snapshots, one-tap enter, Termius-style extra keys, and a path toward Mosh + guided `tmux`/`mosh-server` setup.

---

## Features (v0.1 / main)

- **Session hub** — list remote `tmux` sessions, open / create (`new-session -A`)
- **SSH** — password, private key (PEM), key passphrase
- **Host key checks** — unknown / changed fingerprint confirmation UI
- **Host management** — add / edit / delete hosts; probe `tmux` / `mosh-server`
- **Terminal** — interactive PTY, attach to session, basic VT/SGR rendering
- **Keyboard** — Termius-like bar (Esc/Ctrl/arrows/…) + expand more keys + system IME
- **Themes** — system dark/light (Sequoia / Big Sur style) + optional personal themes
- **No login / no cloud vault required**

### Planned / partial

- Full VT (truecolor, better TUI fidelity), scrollback gestures
- Mosh transport
- Interactive install wizard for missing `tmux` / `mosh-server` (prefilled PTY)
- Command autocomplete + snippets (engine started)
- Desktop “push to computer” companion — **API stub only** (UI hidden)
- iOS after Android is solid

Roadmap detail: [docs/TODO-TRACKER.md](docs/TODO-TRACKER.md), [docs/android-full-plan-v0.3.1.md](docs/android-full-plan-v0.3.1.md).

---

## Screenshots / UI lab

Open [docs/ui-styles.html](docs/ui-styles.html) in a browser for the theme system mock (Sequoia / Big Sur + personal themes).

---

## Build (Android)

**Requirements**

- JDK 17+
- Android SDK (API 35 recommended)
- Android Studio or command-line tools

```bash
git clone git@github.com:rainyday01/Muxa.git
cd Muxa/android

# Create local SDK pointer (do not commit this file)
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

More notes: [android/README.md](android/README.md).

---

## Project layout

```text
Muxa/
├── android/                 # Native Android multi-module app
│   ├── app/                 # Shell, navigation, DI
│   ├── core/                # model, ui themes, Room + secrets
│   ├── ssh/                 # sshj engine, known_hosts, PTY, tmux control
│   ├── mosh/                # Mosh client (UDP + AES-OCB)
│   ├── termux-emulator/     # VT emulator (vendored Termux, GPLv3)
│   ├── termux-view/         # Terminal view (vendored Termux, GPLv3)
│   ├── autocomplete/        # Completion engine
│   ├── desktop-push-api/    # Reserved NoOp API
│   ├── command-palette-api/ # Reserved NoOp API
│   └── feature/             # sessions / hosts / settings / terminal UI
├── docs/                    # Product plans, TODO tracker, UI lab
├── protocols/               # Theme / keyboard / snippet JSON schemas
├── LICENSE
└── README.md
```

---

## Security notes

- Private keys and passwords are stored with **EncryptedSharedPreferences** + Android Keystore patterns — never log secrets.
- Host keys are verified; first connect and key change require explicit user consent.
- This repository must **not** contain real host credentials, `local.properties`, signing keystores, or personal integration tokens. See `.gitignore`.

---

## Contributing

1. Prefer small, reviewable PRs.  
2. Follow the TODO tracker order for large features when possible.  
3. Keep secrets out of commits (keys, tokens, machine-local paths).  
4. UI: system themes Sequoia (dark) / Big Sur (light) by default.

---

## License

[GNU GPLv3](LICENSE), with an additional permission for app-store distribution of the official binaries — see [APPSTORE-EXCEPTION.md](APPSTORE-EXCEPTION.md).

Bundled third-party code keeps its own license (Termux terminal modules: see their `NOTICE` files; iOS: [ios/THIRD_PARTY_LICENSES.md](ios/THIRD_PARTY_LICENSES.md)).

tmux is a separate project under its own license. m-tmux is an independent client.

---

## Disclaimer

m-tmux is not an official tmux product. Server-side requirements are a normal OpenSSH `sshd` and (preferably) `tmux`. Optional `mosh-server` will be used when Mosh support lands.
