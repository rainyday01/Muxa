# Terminal typeface & scheme catalog (0.1.26)

Termius-style **Select scheme & font size** page with live preview.

## Typefaces (10)

| id | Name | Source / license | Why included |
|----|------|------------------|--------------|
| source_code_pro | Source Code Pro Medium | Adobe OFL | Termius default |
| jetbrains_mono | JetBrains Mono | OFL | Most popular coding mono |
| fira_code | Fira Code | OFL | Ligatures / editor crowd |
| cascadia_mono | Cascadia Mono | Microsoft OFL | Windows Terminal default |
| dejavu_sans_mono | DejaVu Sans Mono | free | Classic Linux terminal |
| ubuntu_mono | Ubuntu Mono | UFL | Termius list + Ubuntu |
| hack | Hack | MIT | DejaVu-derived, powerline-friendly |
| ibm_plex_mono | IBM Plex Mono | OFL | Highly readable corporate mono |
| roboto_mono | Roboto Mono | Apache 2.0 | Android / Material ecosystem |
| inconsolata | Inconsolata | OFL | Long-standing monospaced classic |

Fonts live in `android/app/src/main/assets/fonts/`.

## Schemes (11)

| id | Name | Notes |
|----|------|-------|
| pulse_mono | Pulse Mono | **Default** — black/gray mono (UI proposal A terminal) |
| termius_dark | Termius Dark | Green-on-navy classic (optional in settings) |
| termius_light | Termius Light | Light chrome terminal |
| dracula | Dracula | Most ported dark theme |
| nord | Nord | Arctic Polar Night palette |
| solarized_dark | Solarized Dark | Ethan Schoonover classic |
| gruvbox_dark | Gruvbox Dark | Retro groove |
| kanagawa_wave | Kanagawa Wave | Termius + neovim hit |
| everforest_dark | Everforest Dark | Soft green dark |
| night_owl | Night Owl | Sarah Drasner night theme |
| flexoki_dark | Flexoki Dark | Termius catalog / ink palette |

Each scheme defines: `foreground`, `background`, `cursor`, ANSI `color0`–`color15` (ARGB), applied via Termux `TerminalColorScheme.updateWith(Properties)`.

## UI

Settings → **字体 · 配色 · 字号…** → draft changes → ✓ save / ✕ cancel. Preview uses man-page sample text like Termius.
