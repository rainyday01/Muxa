# m-tmux 全量 TODO 跟踪（v0.3.1 · 一项接一项）

> **已锁定：** 展示名 `m-tmux` · 包名 `app.mtmux.android` · **电脑入口完全隐藏** · 按 v0.3.1 全文开工  
> **权威规格：** `android-full-plan-v0.3.1.md`  
> **状态：** `[ ]` 未做 · `[~]` 进行中 · `[x]` 完成 · `[-]` Backlog 本版不做

---

## 0. 锁定决策

| 项 | 值 |
|----|-----|
| 展示名 | m-tmux |
| applicationId | app.mtmux.android |
| 电脑推送 UI | **隐藏**（仅 `DesktopPushPort` NoOp） |
| 命令面板 UI | **隐藏**（仅 Port NoOp） |
| 安装 | 交互预填 only |
| 补全 | 完整做 |

---

## DOC — 设计 / 协议

| 状态 | ID | 任务 |
|------|-----|------|
| [~] | DOC-001 | 高保真关键页（ui-styles 可参考） |
| [x] | DOC-002 | Design Token 6 主题 |
| [x] | DOC-003 | 键盘 schema + 三套 JSON |
| [x] | DOC-004 | 补全/片段 schema |
| [x] | DOC-005 | 免责 Not affiliated with tmux |
| [x] | DOC-006 | 安装预填模板表 |
| [x] | DOC-007 | 命名定稿 |
| [x] | DOC-008 | DesktopPushPort / CommandPalettePort |

---

## 阶段 A — 工程基座

| 状态 | ID | 任务 |
|------|-----|------|
| [x] | AND-001 … AND-010 | 全部完成 |

---

## 阶段 B — SSH / 主机 / 控制面

| 状态 | ID | 任务 |
|------|-----|------|
| [x] | AND-020 | 真 SSH 引擎 sshj + BC/算法矩阵 |
| [x] | AND-021 | 密码 / 私钥 / 口令短语 |
| [x] | AND-022 | known_hosts 确认对话框 |
| [x] | AND-023 | ConnectionManager 重连退避 |
| [x] | AND-024 | 单连接多 channel（sshj Session）+ 断线重试 exec |
| [x] | AND-025 | Host CRUD UI |
| [x] | AND-026 | 粘贴私钥 PEM |
| [x] | AND-027 | list-sessions（真机已通） |
| [x] | AND-028 | list-windows / list-panes |
| [x] | AND-029 | 探测 tmux/mosh/OS/包管理器 |
| [x] | AND-030 | 延迟 ms 展示 |
| [x] | AND-031 | 无 tmux 空态 → 安装向导入口 |

---

## 阶段 C — 终端 / 快照 / 键盘

| 状态 | ID | 任务 |
|------|-----|------|
| [x] | AND-040 | VT 真彩：16/256/24-bit SGR + 下划线/反色/斜体/dim |
| [x] | AND-041 | SSH PTY 交互 |
| [x] | AND-042 | 终端尺寸协商 resize |
| [x] | AND-043 | scrollback 手势（竖滑 + 双击回底 + 指示条） |
| [x] | AND-044 | capture-pane 快照缓存 |
| [x] | AND-045 | 先进入再 PTY |
| [x] | AND-046 | Termius 快捷横条 |
| [x] | AND-047 | 扩展更多快捷键 |
| [x] | AND-048 | 系统 IME |
| [x] | AND-049 | Ctrl 锁定 |
| [x] | AND-050 | 三套键盘方案切换 |
| [x] | AND-051 | 键盘编辑器（复制/编辑/排序/新建，持久化） |
| [x] | AND-052 | 键盘 JSON 导入导出（剪贴板，schema 兼容） |
| [x] | AND-053 | 终端菜单 |
| [x] | AND-054 | attach / new-session -A |

---

## 阶段 D — Mosh

| 状态 | ID | 任务 |
|------|-----|------|
| [x] | AND-060 | Mosh 客户端（纯 Kotlin UDP + AES-OCB） |
| [x] | AND-061 | SSH 启动 mosh-server / 解析 MOSH CONNECT |
| [x] | AND-062 | AUTO 有 mosh-server 则优先 Mosh；可强制 SSH/MOSH |
| [x] | AND-063 | 弱网状态：同步中 / 弱网（状态条） |
| [x] | AND-064 | 控制面仍 SSH（list/kill/probe 不变） |
| [x] | AND-065 | Mosh 失败降级 SSH |

---

## 阶段 E — 交互式安装

| 状态 | ID | 任务 |
|------|-----|------|
| [x] | AND-070 | 探测驱动安装向导 |
| [x] | AND-071 | 发行版预填命令 |
| [x] | AND-072 | 预填 PTY 安装会话 |
| [x] | AND-073 | 复制命令 |
| [x] | AND-074 | 安装后重新检测 |
| [x] | AND-075 | 未知发行版兜底文案 |
| [-] | — | 一键代装（Backlog） |

---

## 阶段 F — 命令补全 + 片段

| 状态 | ID | 任务 |
|------|-----|------|
| [x] | AND-080 | Suggestion UI（终端建议条） |
| [x] | AND-081 | 输入防抖 suggest |
| [x] | AND-082 | Snippet CRUD UI |
| [x] | AND-083 | 片段匹配 |
| [x] | AND-084 | 插入 vs 执行 |
| [x] | AND-085 | 片段变量 host/user/session |
| [x] | AND-086 | CommandHistory 记录 |
| [x] | AND-087 | 内置词表 |
| [x] | AND-088 | 路径补全（远程 compgen，防抖 + 失败静默） |
| [x] | AND-089 | TUI 暂停（DEC alt-screen + 启发式） |
| [x] | AND-090 | 补全总开关 |
| [x] | AND-091 | 片段随配置导出导入 |

---

## 阶段 G — 会话体验

| 状态 | ID | 任务 |
|------|-----|------|
| [x] | AND-100 | 卡片进入/更多 |
| [x] | AND-101 | 杀/重命名/刷新快照 |
| [x] | AND-102 | 新建会话对话框 |
| [x] | AND-103 | 编码模板 claude/codex/grok |
| [x] | AND-104 | 多 Host 分组/最近视图切换 |
| [x] | AND-105 | 下拉刷新 |
| [x] | AND-106 | 启动直达最近会话 |
| [x] | AND-107 | 加密导出导入（主机+片段，默认不含私钥） |
| [x] | AND-108 | 生物识别/设备凭证锁 |
| [x] | AND-109 | 复制 attach 命令 |
| [-] | — | 命令面板 UI |
| [-] | — | 推送到电脑 UI |

---

## 阶段 H — 视觉

| 状态 | ID | 任务 |
|------|-----|------|
| [~] | AND-120…125 | 主题可用；商店级打磨/字体授权待做 |

---

## 阶段 I — 质量与发布

| 状态 | ID | 任务 |
|------|-----|------|
| [x] | AND-130 | 单测：parse / VtBuffer scrollback / autocomplete path+TUI |
| [~] | AND-131 | 补全/片段变量 + 键盘 schema 单测（KeyboardCodec 全内置 round-trip、applyVars） |
| [ ] | AND-132 … AND-138 | 待做（真机矩阵 / TUI / 性能 / AAB / 隐私文案） |
| [x] | AND-139 | 飞书专群发版（持续） |

---

## Backlog

| 状态 | ID | 项 |
|------|-----|-----|
| [-] | BL-001 … BL-007 | 不变 |

---

## 进度摘要

| 阶段 | 约 |
|------|-----|
| A | 100% |
| B | ~100% |
| C | ~100% |
| D Mosh | ~100%（真机 UDP/防火墙需验证） |
| E 安装 | ~100% |
| F 补全 | ~100% |
| G 会话 | ~100% |
| H/I | 部分 |

## 下一步

1. 质量矩阵 AND-131+  
2. Mosh 真机弱网压测 / 端口防火墙说明  
3. DEC 区域滚动 / 完整 italic 字体回退（可选）  

代码：`android/` · 构建：`./gradlew :app:assembleDebug`
