# 交互式安装预填命令（DOC-006）

App 打开 PTY 后展示/预填下列命令，由用户回车执行并自行输入 sudo 密码。

| 探测线索 | 命令 |
|----------|------|
| Debian/Ubuntu | `sudo apt-get update && sudo apt-get install -y tmux mosh` |
| Fedora | `sudo dnf install -y tmux mosh` |
| Arch | `sudo pacman -S --noconfirm tmux mosh` |
| Alpine | `sudo apk add tmux mosh` |
| macOS brew | `brew install tmux mosh` |
| 未知 | 提示手动安装 tmux 与 mosh-server，并提供复制通用说明 |
