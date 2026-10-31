# Pane v0.3 — 产品方案 + Android 全功能路线与 TODO

> 日期：2026-07-16  
> **本文件为当前权威方案**（覆盖 v0.1 / v0.2 中与下列冲突的条目）。  
> 策略：**不做 MVP 裁剪**；**先 Android 全功能**，体验达标后再移植 iOS。  
> 飞书归档：仅项目私有群。

---

## 0. 本轮拍板（相对 v0.2 变更）

| 议题 | v0.2 | **v0.3 结论** |
|------|------|----------------|
| 发布策略 | 分层 MVP | **不做 MVP**；Android **完整功能** 一次做满 |
| 平台顺序 | 待定 | **仅 Android → 验证 → 再 iOS** |
| 手机主屏捷径 | MVP 做 | **不做**（无必要） |
| 电脑推送 | 远期 | **Phase 1 做方案 D**：通知到有头电脑 → 用户点一下打开终端 |
| Mosh | 后期 | **Phase 1 必做**；加主机时探测 + 引导/代装 |
| 会话管理入口 | 未定 | **以常驻会话列表为中枢**，不做平行的第二套「管理中心」（见 §4） |
| 键盘 | Termius 路线 | **严格对齐 Termius 交互模型** + 可配置（见 §5） |

**仍有效（v0.2 继承）：**

- 主题：System Dark=Sequoia / Light=Big Sur；4 套个性化；无 Aqua  
- 零账号；会话一等公民  
- 嵌入式 SSH 库（非壳调 `ssh` CLI）+ ConnectionManager  
- 快照先显后连  

---

## 1. 产品一句话与范围

**Pane**：面向在 **有头电脑 + 远程 tmux（Claude/Codex/Grok 等 TUI）** 场景的开发者，提供 **原生 Android 远程终端启动器**——会话列表、极稳连接（SSH+Mosh）、漂亮终端与 Termius 级扩展键盘，并能 **把会话推送到电脑：通知一点即开**。

### 1.1 做 / 不做

| 做 | 不做 |
|----|------|
| Android 原生全功能客户端 | 第一版 iOS（第二阶段） |
| SSH + **Mosh** 双传输 | 强制云账号 / SaaS 登录 |
| tmux / mosh-server 探测与安装引导 | 手机主屏幕快捷方式 |
| 会话列表中枢 + 快照 | 重造 IDE |
| Termius 型扩展键盘（可配置） | Classic Aqua 主题 |
| 电脑端轻量伴侣：**通知 → 一点打开**（方案 D） | 完全无组件的电脑弹窗（不可靠） |
| 主题体系 2 默认 + 4 个性化 | — |

### 1.2 核心用户路径

```
添加 Host（密钥）
  → 自动探测 tmux / mosh-server（缺失则引导安装）
  → 首页会话列表（快照卡片）
  → 点「进入」→ 快照秒显 → SSH 或 Mosh 附着
  → Termius 型键盘操作 TUI
  → 点「推送到电脑」→ 电脑通知 → 用户点通知 → 桌面终端 attach 同会话
```

---

## 2. 推送到电脑（方案 D · Phase 1 完整规格）

### 2.1 用户故事

> 人在手机上确认了会话状态，希望 **回到有头电脑前时少敲命令**：手机点「推送到电脑」→ 电脑弹出 **一条通知** → 点通知 → 系统终端打开并 attach 到该 tmux 会话。

**不是**手机主屏图标；**不是**强制零点击弹窗（系统权限与焦点策略不友好）。  
**是**「通知 + 一击」——你倾向的 D。

### 2.2 为什么需要桌面组件

手机无法凭空在电脑屏幕上画窗口。方案 D 的最小桌面侧是：

**Pane Desktop Companion（轻量托盘程序）**

| 能力 | 说明 |
|------|------|
| 常驻 | 用户登录桌面后托盘运行（可设开机启动） |
| 收通知 | 接收「打开 host/session」意图 |
| 一点打开 | 点击通知 → 调用本机终端执行等价于 `ssh … -t 'tmux attach -t NAME'` 或本地 `tmux attach`（若会话就在本机） |
| 配对 | 与手机 **本地配对码 / 二维码 / 同一局域网发现**（见下） |
| 无账号云 | 默认 **不经过我们的服务器**；配对在用户设备间完成 |

### 2.3 传输通道（推荐优先级）

| 优先级 | 通道 | 说明 |
|--------|------|------|
| P0 | **同一局域网 UDP/TCP 发现 + 直连** | 家/公司最常见；零云 |
| P0 | **通过已配置的 SSH 反向**：手机 SSH 到「跳板/同网机器」写 inbox 文件，桌面轮询 | 复杂网络兜底 |
| P1 | 用户自备 **ntfy / 自建 webhook**（可选） | 公网也能推；配置项高级 |
| 不做（P1 前） | 我们运营的推送中台 | 与「无账号」气质冲突，且运维重 |

**Phase 1 必达：** 局域网直连配对 + 通知一点开。  
**Phase 1 应达：** 配对引导极简（扫码或 6 位码）。  
**可并行：** 高级设置里填 ntfy topic（可选）。

### 2.4 打开终端的实现（桌面 Companion）

按 OS 调用用户配置的终端：

| OS | 默认策略 |
|----|----------|
| macOS | `Terminal.app` / `iTerm` / `Warp`（设置可选）执行 ssh/tmux |
| Linux | `gnome-terminal` / `konsole` / `x-terminal-emulator` / `kitty` |
| Windows | Windows Terminal `wt.exe` / 配置的 shell |

命令模板（可配置）：

```text
ssh -t {user}@{host} -p {port} "tmux attach-session -t {session} || tmux new-session -A -s {session}"
```

若 Host 标记为「本机 tmux」（会话就在这台电脑）：

```text
tmux attach-session -t {session}
```

### 2.5 手机侧 UI

- 会话卡片 / 终端内菜单：**推送到电脑**  
- 状态：未配对电脑 / 电脑离线 / 已发送 / 对方已打开  
- 设置 → **已配对电脑** 列表（可多台：书房 PC / 笔记本）

### 2.6 Companion 是否算「安装东西」

是——**仅在你要用「推送到电脑」时**需要装一次桌面伴侣。  
纯手机 SSH/Mosh/会话管理 **不依赖** Companion。  
产品文案写清：**推送到电脑需要桌面伴侣；其余功能装 Android App 即可。**

---

## 3. 连接层：SSH + Mosh（Phase 1 全做）

### 3.1 架构

```
UI (Compose)
  → SessionRepository / SnapshotCache
  → ConnectionManager
       ├─ transport = SSH | MOSH | AUTO
       ├─ SSH engine: libssh2/libssh (FFI)
       ├─ Mosh client: 嵌入 mosh 协议栈或成熟移植
       └─ 状态机: Idle/Connecting/Ready/Degraded/Reconnecting/Failed
  → 远程: sshd + (可选) mosh-server + tmux
```

| 模式 | 何时用 |
|------|--------|
| SSH | 默认；列表/探测/安装/快照 exec 信道 |
| Mosh | 交互 PTY 优先（弱网）；需服务端 `mosh-server` |
| AUTO | 有 mosh-server 且用户开启「优先 Mosh」→ 交互走 Mosh；控制面仍 SSH |

**控制面（list/capture/install）始终走 SSH exec**（可靠、短连接友好）。  
**交互面**可 SSH PTY 或 Mosh。

### 3.2 服务端探测（添加 Host / 首次连接 / 手动「环境检查」）

并行检测（SSH exec）：

```bash
command -v tmux
tmux -V
command -v mosh-server
mosh-server --version   # 或 mosh --version
uname -s / 包管理器线索（apt/dnf/pacman/apk/brew）
```

结果写入 Host 卡片徽章：`tmux ✓/✗` · `mosh ✓/✗` · 延迟。

### 3.3 缺失时的安装引导（三种路径，Phase 1 全提供）

| 路径 | 流程 | 密码问题 |
|------|------|----------|
| **A. 一键代装（推荐有权限用户）** | App 生成发行版对应 install 脚本 → SSH 执行；若需 root，弹出 **一次性 sudo 密码框** → `sudo -S` 写入 stdin（**内存中短暂持有，不落盘、不写日志**） | 用户主动输入；可取消 |
| **B. 交互式安装会话** | 打开专用 PTY，预填命令，用户在真终端里自己输密码（最安全、最好懂） | 系统 sudo 正常交互 |
| **C. 复制命令** | 展示 `apt install tmux mosh` 等，一键复制，用户自行粘贴 | 无 |

**安全约束：**

- sudo 密码：仅存于内存 `CharArray`，用完填零；绝不进 Room/日志/崩溃报告  
- 失败时展示 stderr 友好化（权限不足 / 无包管理器 / 需交互确认）  
- 不支持的环境：明确提示走 B/C  

**包管理器模板（内置表，可扩展）：**

| 探测 | tmux | mosh |
|------|------|------|
| Debian/Ubuntu | `apt-get install -y tmux mosh` | 同左 |
| Fedora | `dnf install -y tmux mosh` | 同左 |
| Arch | `pacman -S --noconfirm tmux mosh` | 同左 |
| Alpine | `apk add tmux mosh` | 同左 |
| macOS (brew) | `brew install tmux mosh` | 同左 |

### 3.4 连接体验（完整，非骨架）

- 连接池 / 多 channel（exec 并行 list+capture）  
- Keepalive、网络回调（Wi‑Fi↔蜂窝）、指数退避重连  
- 进会话：快照先渲染 → 真会话就绪交叉淡入  
- Mosh：断网恢复指示「Mosh 同步中」  
- 明确状态条：连接中 / SSH / Mosh / 重连中 / 已断开  

---

## 4. 会话管理：要不要单独入口？

### 4.1 结论（产品）

**不需要**再做一套与首页平行的「会话管理中心」。

原因：

1. 产品定位就是 **会话一等公民**——**常驻会话列表 = 会话管理主界面**。  
2. 控制面命令（list / attach / capture / kill / new）都应落在 **列表卡片操作** 与 **会话详情/更多菜单**，而不是让用户先进入「管理器」再找会话。  
3. 单独入口会 **双份 IA**，增加认知负担，和 Termius「主机列表」式分层不同——我们要更短路径。

### 4.2 推荐信息架构

```
Tab「会话」          ← 中枢（管理 + 进入都在这）
  ├─ 筛选：Host / 最近 / 标签
  ├─ 下拉刷新 = 重新 list-sessions + 可见项 capture
  ├─ 卡片主按钮：进入
  ├─ 卡片次级：推送到电脑 / 更多
  └─ 更多（BottomSheet）：
        刷新快照、重命名、新建窗口、分离提示、
        终止会话、复制 attach 命令、会话详情

Tab「主机」
  ├─ 添加 / 编辑 / 密钥
  ├─ 环境检查（tmux/mosh）
  ├─ 默认传输：SSH / Mosh / AUTO
  └─ 安装引导入口

Tab「电脑」          ← 原「捷径」位替换
  ├─ 配对桌面伴侣
  ├─ 在线状态
  └─ 默认终端类型（Companion 侧也会存）

Tab「设置」
  ├─ 外观主题
  ├─ 键盘布局编辑器
  ├─ 连接与预热
  └─ 关于 / 导出配置（加密）
```

### 4.3 全局「命令面板」（可选增强，Phase 1 建议做）

类似 ⌘K：**模糊搜会话 / 主机 / 动作**（进入、推送、新建 claude 会话…）。  
这是 **快捷入口**，不是第二套管理树。优先级：P1（完整版建议包含）。

### 4.4 编码会话模板（完整版包含）

一键：`new-session -A -s claude-work` + 可选 `send-keys 'claude' Enter`（用户可编辑模板）。

---

## 5. 键盘（严格 Termius 模型）

### 5.1 交互结构（与你描述一致）

```
┌─────────────────────────────────────────────┐
│  终端 viewport（可滚动）                       │
├─────────────────────────────────────────────┤
│  快捷键横条（常显）              [ ⋮ 扩展 ]   │  ← 区域 1
├─────────────────────────────────────────────┤
│  扩展面板（仅点扩展后）：更多快捷键网格         │  ← 可选展开
├─────────────────────────────────────────────┤
│  系统输入法 / 用户安装的 IME（未点扩展时默认）  │  ← 区域 2
└─────────────────────────────────────────────┘
```

| 规则 | 说明 |
|------|------|
| 快捷横条 | **始终**在终端与 IME 之间（可设置「自动隐藏横条」为高级选项，默认显示） |
| 右侧扩展 | 展开/收起 **更多快捷键**；收起后下方仍是 **系统 IME** |
| 不替换系统键盘 | 字母数字仍走用户自己的输入法（搜狗/Gboard/等） |
| 可配置 | 横条键位、扩展页键位、键宽、是否显示 Ctrl 锁定态、多套方案 |

### 5.2 默认键位包

| 方案名 | 用途 |
|--------|------|
| General | Esc Ctrl Alt Tab ←↑↓→ Home End PgUp PgDn ~ \| - / |
| tmux | 前缀 Ctrl-b 提示键、c n p d % " [ ] 等发送真实序列 |
| CLI Tools | Ctrl-C Ctrl-D Ctrl-Z Ctrl-L Ctrl-W 等 |

### 5.3 配置器

设置 → 键盘：

- 拖拽排序、增删键、录制组合键  
- 导入/导出 JSON 布局  
- 跟主题无关的独立配置  

---

## 6. UI / 主题（Android 完整）

| 角色 | 主题 |
|------|------|
| 系统深色 | Sequoia Glass |
| 系统浅色 | Big Sur Soft |
| 个性化 | Space Gray / Terminal Pure / Vision Layers / Paper |
| 终端 palette | 每套 UI 绑定默认 palette，可单独改 |

动效、空状态、连接状态、错误友好文案均按完整产品打磨（非线框级）。

---

## 7. Android 技术栈（可执行）

| 层 | 选型 |
|----|------|
| 语言/UI | Kotlin 2.x · Jetpack Compose · Navigation · Material 定制（非默认灰） |
| 架构 | 多模块：`app` / `core-ui` / `core-data` / `ssh` / `mosh` / `terminal` / `desktop-push` |
| DI | Hilt 或 Koin |
| 异步 | Coroutines · Flow |
| 存储 | DataStore + Room；密钥 **Android Keystore** |
| SSH | libssh2 或 libssh · JNI/NDK |
| Mosh | 嵌入 mosh 客户端实现（NDK）或维护良好的移植库 |
| 终端 | 自研 Compose Canvas / 或成熟 Terminal Emulator 内核 + 自研壳 |
| 桌面推送 | 局域网协议（自研简单 JSON over TCP/UDP）+ Companion 仓库可同 monorepo `desktop/` |
| 测试 | 单元（连接状态机）· 仪器（UI）· 真机 SSH 矩阵脚本 |
| 最低系统 | 建议 API 26+（可评估 24+） |
| 包名（暂定） | `life.eds.pane` 或后续确定 |

### 7.1 仓库结构建议

```
tmuxapp/
  docs/
  android/                 # Android 全功能 App
  desktop-companion/       # 方案 D：macOS/Linux/Windows 托盘
  protocols/               # 推送消息 schema、主题 JSON、键盘 JSON
  scripts/                 # 探测/安装模板、集成测试
```

---

## 8. Android 全功能交付阶段（非 MVP 裁剪，仅工程排序）

> 仍是 **同一版本目标全做完**；阶段只为并行与依赖排序，不是砍功能。

| 阶段 | 名称 | 目标 |
|------|------|------|
| **A** | 工程基座 | 多模块、主题系统、导航壳、密钥存储 |
| **B** | SSH 控制面 | 连接池、list-sessions、Host CRUD、环境探测 |
| **C** | 终端交互 | VT、PTY、Termius 键盘、快照先显 |
| **D** | Mosh | 客户端、AUTO 策略、弱网指示 |
| **E** | 安装引导 | tmux/mosh 三路径安装 |
| **F** | 会话体验打磨 | 模板、命令面板、详情操作、多 Host |
| **G** | 桌面推送 D | 协议 + Companion + 手机配对 UI |
| **H** | 主题与动效 | 6 主题完整、商店级打磨 |
| **I** | 质量与发布 | 测试矩阵、混淆、签名、文档 |

阶段可部分并行（如 A+B 后 C∥G 准备协议）。

---

## 9. 详细 TODO 清单（请确认后开工）

> 编号 `AND-xxx` 为 Android；`DES-xxx` 为桌面伴侣；`DOC-xxx` 为文档/设计。  
> 确认方式：回复「按 v0.3 TODO 开工」或标注要改的编号。

### 9.0 设计与协议（开工前 / 并行）

| ID | 任务 | 验收 |
|----|------|------|
| DOC-001 | 高保真关键页：会话列表、主机、终端+键盘、探测安装、推送到电脑、配对 | 可点击原型或完整标注稿 |
| DOC-002 | Design Token JSON：Sequoia / Big Sur / 4 个性化 | 双端可读 JSON |
| DOC-003 | 键盘布局 JSON schema + 三套默认布局文件 | 可被 App 解析 |
| DOC-004 | 桌面推送消息 schema（session open 意图） | 版本化 JSON |
| DOC-005 | 局域网发现与配对流程说明（安全：配对码时效） | 写入 docs |
| DOC-006 | 安装脚本模板表（各发行版）与失败文案 | scripts + docs |
| DOC-007 | 术语表与 UI 文案（中文优先） | 无「主屏捷径」等旧文案 |

---

### 9.1 阶段 A — 工程基座

| ID | 任务 | 验收 |
|----|------|------|
| AND-001 | 创建 `android/` Gradle 多模块工程（app, core-ui, core-data, ssh, mosh, terminal, desktop-push） | 同步成功 |
| AND-002 | 应用壳：底部 4 Tab（会话/主机/电脑/设置）+ Navigation | 切换无状态丢失（基本） |
| AND-003 | 主题系统：System 跟随 + Sequoia/Big Sur Token 落地 | 改系统深浅即切换 |
| AND-004 | 个性化 4 主题可切换（设置） | 重启后保持 |
| AND-005 | 安全存储：Host 元数据 Room + 密钥 Keystore 封装 | 卸装密钥不可明文导出 |
| AND-006 | 全局错误/Snackbar/加载规范 | 统一组件 |
| AND-007 | 日志策略：默认脱敏（无密钥/无 sudo 密码） | code review 清单 |
| AND-008 | 关于页、版本号、开源许可占位 | 可展示 |

---

### 9.2 阶段 B — SSH 与主机 / 控制面

| ID | 任务 | 验收 |
|----|------|------|
| AND-010 | NDK 集成 libssh2 或 libssh | 真机连公钥主机成功 |
| AND-011 | 认证：密码 / 私钥(ed25519/rsa) / 口令短语 | 三种路径测通 |
| AND-012 | known_hosts 策略与首次指纹确认 UI | 可拒绝未知主机 |
| AND-013 | ConnectionManager 状态机 + Keepalive + 重连 | 拔网线/恢复可恢复或明确失败 |
| AND-014 | 连接池：同 Host 多 exec channel | 列表+探测并行不建双 TCP（或可配置） |
| AND-015 | Host CRUD UI（别名、host、port、user、默认传输） | 增删改查 |
| AND-016 | 导入私钥文件 / 粘贴 PEM | 成功保存并连接 |
| AND-017 | `tmux list-sessions` 解析 → 会话列表模型 | 多会话正确 |
| AND-018 | list-windows / list-panes（详情用） | 数据正确 |
| AND-019 | 环境探测：tmux / mosh-server / OS / 包管理器 | 徽章展示 |
| AND-020 | 延迟探测与 Host 状态点 | UI 展示 |
| AND-021 | 无 tmux 时列表空态引导 | 跳转安装 |

---

### 9.3 阶段 C — 终端 + 快照 + 键盘

| ID | 任务 | 验收 |
|----|------|------|
| AND-030 | VT/终端渲染内核集成或自研最小可用 | 真彩色、中文、TUI 不乱 |
| AND-031 | SSH PTY 交互会话 | Claude/Codex/htop 可用 |
| AND-032 | 窗口尺寸协商（resize） | 旋转/分屏自适应 |
| AND-033 | scrollback 与手势滚动 | 流畅 |
| AND-034 | `capture-pane` 快照 + 磁盘缓存 | 列表显示伪终端卡片 |
| AND-035 | 进入会话：先快照后真 PTY 交叉 | 体感秒开 |
| AND-036 | Termius 型快捷横条（常显） | 布局同 §5 |
| AND-037 | 右侧扩展 → 更多快捷键面板 | 展开/收起；收起后 IME 正常 |
| AND-038 | 与系统 IME 共存（不抢输入法） | 搜狗/Gboard 可用 |
| AND-039 | Ctrl 锁定/下一键组合 | ^C ^D 等正确 |
| AND-040 | 三套默认键盘方案切换 | General/tmux/CLI |
| AND-041 | 键盘布局编辑器（增删改排序） | 持久化 |
| AND-042 | 键盘布局导入导出 JSON | 往返成功 |
| AND-043 | 终端内菜单：断开、推送到电脑、字体大小 | 可用 |
| AND-044 | 会话「进入」= attach / `new-session -A` | 行为正确 |

---

### 9.4 阶段 D — Mosh

| ID | 任务 | 验收 |
|----|------|------|
| AND-050 | Mosh 客户端库集成（NDK） | 连已知 mosh-server 成功 |
| AND-051 | SSH 预测/启动 mosh-server 握手流程 | 标准 mosh 流程 |
| AND-052 | 传输 AUTO：有 mosh 则交互优先 Mosh | 设置可强制 SSH |
| AND-053 | 弱网：断网重连 UI「Mosh 同步中」 | 地铁场景可测 |
| AND-054 | Mosh 与快照/控制面协作（控制面仍 SSH） | 无冲突 |
| AND-055 | 无 UDP/防火墙失败时降级 SSH 并提示 | 友好 |

---

### 9.5 阶段 E — tmux / mosh 安装引导

| ID | 任务 | 验收 |
|----|------|------|
| AND-060 | 探测结果驱动安装向导 UI | 缺啥显示啥 |
| AND-061 | 路径 C：复制安装命令 | 剪贴板正确 |
| AND-062 | 路径 B：打开预填 PTY 安装会话 | 用户可交互 sudo |
| AND-063 | 路径 A：一键代装 + sudo 密码框 | 成功装上 tmux/mosh |
| AND-064 | sudo 密码内存清零与禁止日志 | 安全审查通过 |
| AND-065 | 发行版模板与未知系统兜底 | 不硬崩 |
| AND-066 | 安装后自动复测并刷新徽章 | 无需手刷 |

---

### 9.6 阶段 F — 会话体验完整

| ID | 任务 | 验收 |
|----|------|------|
| AND-070 | 会话卡片完整操作：进入/推送/更多 | 符合 §4.2 |
| AND-071 | 更多：杀会话、重命名、刷新快照 | 代执行 tmux 命令 |
| AND-072 | 新建会话对话框（名 + 模板） | 创建成功 |
| AND-073 | 编码模板：claude / codex / grok / 自定义 | 可编辑持久化 |
| AND-074 | 多 Host 分组与扁平最近 | 两种视图 |
| AND-075 | 下拉刷新列表 | 正确 |
| AND-076 | 命令面板 ⌘K 风格搜索 | 跳转/执行动作 |
| AND-077 | 最近会话启动选项（开 App 直达上次） | 可关 |
| AND-078 | 加密导出/导入主机配置（无账号同步替代） | 跨机迁移 |
| AND-079 | 可选 App 锁（生物识别） | 可关 |

---

### 9.7 阶段 G — 推送到电脑（方案 D）

| ID | 任务 | 验收 |
|----|------|------|
| DES-001 | Companion 工程骨架（建议 Kotlin Multiplatform 或 Go/Tauri；优先好做托盘的技术） | 三平台或先 Linux+macOS |
| DES-002 | 托盘图标 + 通知权限 | 可弹出系统通知 |
| DES-003 | 局域网 discovery + 配对码/二维码 | 手机能配对 |
| DES-004 | 接收 open-session 意图 | 日志/通知可见 |
| DES-005 | 点击通知 → 拉起终端执行 attach 模板 | 真开窗 |
| DES-006 | 终端类型设置（Terminal/iTerm/wt/…） | 可配 |
| DES-007 | 本机 tmux vs SSH 远程会话分支 | 两种都对 |
| AND-080 | 手机「电脑」Tab：配对、列表、在线状态 | 可用 |
| AND-081 | 会话「推送到电脑」发送意图 | Companion 收到 |
| AND-082 | 发送结果反馈（成功/离线/未配对） | UI 明确 |
| AND-083 | 多电脑选择器 | 指定目标 |
| AND-084 | （可选）ntfy 高级通道 | 文档+设置项 |
| AND-085 | 未装 Companion 时的引导页（下载/说明） | 不阻塞其它功能 |

> Companion 首发范围建议：**Linux + macOS 优先**（与你 VPS/开发机一致），Windows 同阶段或紧随；若你确认只要某平台可再收窄。

---

### 9.8 阶段 H — 视觉与动效打磨

| ID | 任务 | 验收 |
|----|------|------|
| AND-090 | Sequoia 材质（blur/边框）达商店级 | 截图对比 |
| AND-091 | Big Sur 浅色完整 | 同上 |
| AND-092 | 4 个性化主题无回归 | 切换无崩溃 |
| AND-093 | 列表/终端转场动效 | 60fps 中端机 |
| AND-094 | 空状态插画/文案 | 首启友好 |
| AND-095 | 终端字体（JetBrains Mono 等）打包与授权确认 | 合规 |

---

### 9.9 阶段 I — 质量、性能、发布

| ID | 任务 | 验收 |
|----|------|------|
| AND-100 | 连接状态机单元测试 | 覆盖重连分支 |
| AND-101 | 键盘布局解析测试 | schema 兼容 |
| AND-102 | 真机矩阵：SSH only / Mosh / 无 tmux / 错密码 | 清单全绿 |
| AND-103 | TUI 兼容：tmux, htop, claude, codex, vim | 抽样通过 |
| AND-104 | 性能：冷启动、列表 50 会话、长 scrollback | 达 brief 指标 |
| AND-105 | 耗电：后台默认不保活策略验证 | 可接受 |
| AND-106 | ProGuard/R8、签名、app bundle | 可分发 |
| AND-107 | 隐私说明：密钥与 sudo 处理文案 | 商店/关于内 |
| AND-108 | 飞书专群发布安装包与说明 | 仅目标群 |
| DES-010 | Companion 打包与安装说明 | 用户可跟做 |

---

### 9.10 TODO 统计（便于排期）

| 类别 | 数量（约） |
|------|------------|
| DOC | 7 |
| AND | ~80 |
| DES | ~8 |
| **合计** | **~95 项** |

粗排期参考（单人全职量级，仅参考）：基座+SSH 3–4 周 · 终端键盘 3–4 周 · Mosh+安装 2–3 周 · 会话打磨 1–2 周 · 推送+Companion 2–4 周 · 打磨测试 2 周 → **约 3–4 个月** 视熟练度与并行（桌面可另一人）。

---

## 10. 明确删除的旧范围

- ❌ 手机「固定到主屏幕 / 快捷图标」  
- ❌ MVP 裁剪版发布  
- ❌ 第一版同时交付 iOS  
- ❌ 无桌面组件却承诺电脑弹窗  

---

## 11. 请你确认的问题（确认后开工）

1. **桌面 Companion 首发 OS**：Linux + macOS + Windows 全做，还是先 Linux/macOS？  
2. **包名 / 应用显示名**：Pane 是否最终名？  
3. **一键代装（路径 A）**：是否必须进第一版？（B+C 更安全；A 体验最好但安全实现要谨慎）  
4. **命令面板（AND-076）**：第一版必须 / 可稍后？  
5. 以上 TODO 是否 **按全文开工**，或要砍/并某些编号？

确认示例回复：

> Companion 先 Linux+macOS；应用名 Pane；路径 A/B/C 都要；命令面板要；按 v0.3 TODO 全文开工。

---

*下一步：你确认 §11 后，从 AND-001 / DOC-001 并行启动。*
