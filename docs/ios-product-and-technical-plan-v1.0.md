# Muxa iOS 完整产品与技术方案 v1.0

> 日期：2026-07-29
> Android 基线：仓库 `Mobile-tmux`，当前提交 `c88b5ef`
> 目标：以现有 Android 真实能力为功能基线，交付原生 iOS 版本；保留名称、图标与完整用户能力，并针对 iOS 深度优化。

## 1. 执行结论

1. **产品名锁定为 `Muxa`。** 仓库文档仍混用 `m-tmux`，但 Android 实际安装包 `app_name` 是 `Muxa`。iOS 的桌面显示名、启动页、关于页和商店主名称均使用 `Muxa`；关于页保留 “Not affiliated with the tmux project”。
2. **图标保持同一视觉。** 使用 `design/muxa-icon-hex-terminal.svg` 的 C25 配色、六边形和 `>_` 几何。iOS AppIcon 输出为无透明通道的 1024px 全幅底图，由 iOS 负责系统圆角遮罩，避免把 Android adaptive icon 的遮罩重复烘焙进去。
3. **不是简单 SSH 终端。** iOS 第一版必须完整交付：主机管理、SSH、known_hosts、tmux 会话中枢、多 socket、快照、交互终端、Mosh、扩展键盘、补全、片段、安装向导、主题、加密迁移、应用锁和诊断。
4. **原生实现。** Swift 6、SwiftUI 为产品 UI，UIKit 承载终端和输入系统；不采用 Flutter/React Native，也不移植 Compose UI。
5. **核心依赖选择：** `SwiftTerm` 负责终端，`libssh2` 负责 SSH，`GRDB` 负责 SQLite，`Valet` 负责 Keychain。Mosh 首选 `swift-mosh`，但必须先通过真实 `mosh-server` 兼容性门槛；失败时只允许切换到经过许可证确认的官方 Mosh/Blink 方案，不再自研协议。
6. **所有构建与自动化测试在本机执行。** 不消费 GitHub Actions 构建额度。自动化 E2E 以本机 iPhone Simulator + 用户后续提供的 VPS 为正式路径。
7. **当前机器尚未满足开工条件。** 目前仅有 Command Line Tools，`xcodebuild` 找不到完整 Xcode。开始工程前必须安装完整 Xcode、iOS Simulator runtime，并将 `xcode-select` 指向 Xcode。

## 2. 第一性原理与范围定义

### 2.1 目标

用户真正需要的不是“手机里有一个 shell”，而是以最少步骤进入远端正在运行的 tmux/Claude Code/Codex 工作现场。因此核心任务链是：

```text
打开 Muxa -> 看到跨主机 tmux 会话与快照 -> 一次点击进入 ->
用触屏/系统键盘可靠操作 TUI -> 弱网后恢复 -> 返回时保留现场
```

### 2.2 已确认事实

- Android 当前约 85%-90% 功能完成，主路径已贯通，但 IME、字体和 Mosh 真机矩阵仍有体验债。
- Android 的控制面始终通过 SSH exec；交互面可以是 SSH PTY 或 Mosh。这一分层应原样保留。
- 现有 tmux 多 socket 发现与 attach 逻辑是产品核心资产，应做语义等价移植，不应改成简单 `tmux ls`。
- 无账号、无云端密钥库、密钥留在设备，是产品承诺。
- iOS 不允许普通 App 在后台无限维持任意 TCP/UDP 会话。功能等价应定义为“回前台自动恢复现场”，不能承诺永久后台常连。

### 2.3 第一版包含与不包含

**包含：** Android 当前 UI 可见且代码链路贯通的全部能力。

**不包含：** Android 本身尚未交付的电脑 Companion、推送到电脑、命令面板完整 UI、一键代填 sudo、云账号。iOS 保留 `DesktopPushPort` 和 `CommandPalettePort` 协议及 NoOp 实现，但不显示入口。

## 3. 用户与产品定位

### 3.1 核心用户

- 在 VPS 上长期运行 Claude Code、Codex、Grok CLI 的开发者。
- 需要在通勤、会议或离开电脑时快速接管 tmux 的运维与研发人员。
- 重视密钥本地保存、不愿注册云账号的技术用户。

### 3.2 核心价值

- **会话优先：** 首页是远端 tmux 会话，不是主机通讯录。
- **现场优先：** 快照先显示，再建立 PTY，降低等待感。
- **移动输入优先：** 系统 IME、Smart Keys、硬件键盘三者统一。
- **弱网友好：** Mosh 可用时优先；不可用时明确降级 SSH。
- **本地隐私：** 不上传主机、密码、私钥、命令历史和诊断日志。

## 4. 竞品与生态对比（2026-07-29 实时快照）

| 项目 | 现状 | 可借鉴内容 | 不直接采用的原因 |
|---|---:|---|---|
| Termius | 商业闭源热门产品 | Smart Keys、主机编辑、终端交互密度 | 无法作为开源模块；产品主轴不是 tmux 会话中枢 |
| Blink Shell | 6,875 stars，GPL-3.0，长期 App Store 产品 | Mosh 生命周期、触屏终端、硬件键盘 | 整体架构重，GPL 与 App Store 分发需专项处理 |
| Remux | 212 stars，MIT，原生 iOS tmux 客户端 | 会话/窗口/Pane 的 iPhone 交互、Citadel + Ghostty 可行性 | 项目创建时间短；其 Ghostty iOS fork 与控制面接口仍在快速变化 |
| VVTerm | 340 stars，GPL-3.0，已上架 | Ghostty、libssh2、Mosh、键盘附件、功能分层 | 功能面过宽且双许可证；不适合整体复用 |
| Conduit | 325 stars，Apache-2.0，Flutter | SSH/Mosh/SFTP/硬件密钥能力矩阵 | 非原生 UI 技术路线，不满足深度 iOS 优化目标 |

结论：产品交互对标 Termius、Blink、Remux；模块选择以稳定、许可证清晰、可在本机复现构建为首要条件，不按 stars 机械选型。

## 5. 完整功能方案与验收口径

### 5.1 应用壳与导航

| 能力 | iOS 方案 | 验收 |
|---|---|---|
| 三个主入口 | `TabView`：会话、主机、设置 | 冷启动默认进入会话；切换保持页面状态 |
| 深层导航 | `NavigationStack` + 类型化 Route | 返回手势、状态恢复、Sheet 不冲突 |
| iPad | `NavigationSplitView` 自适应 | 不把 iPhone 界面简单放大；功能完全一致 |
| 空态/错误 | `ContentUnavailableView` 风格 + 可执行主按钮 | 无主机、无会话、无 tmux、离线均有下一步 |

### 5.2 主机与 SSH

- 主机列表、添加、编辑、删除、别名、hostname、端口、用户名。
- 粘贴字段清洗，防止把 `user@host:port` 或带空格内容错误保存。
- 密码、RSA、Ed25519、ECDSA 私钥；OpenSSH、PKCS#8、传统 PEM；无口令与有口令。
- 传输偏好 `AUTO / SSH / MOSH`。
- 首次 host key 显示算法、SHA-256 指纹并允许拒绝；密钥变更必须阻断，不允许静默覆盖。
- 连接超时、认证失败、DNS、端口拒绝、算法不匹配均映射为用户可理解的错误。
- 单 SSH 连接复用多个 exec/channel；健康检查、指数退避和前台恢复。
- 探测 tmux、mosh-server、OS、包管理器、SSH 延迟和 Mosh UDP 可达性。

### 5.3 tmux 会话中枢

- 跨主机聚合，支持“按主机分组”和“最近使用”两种视图。
- 列表展示会话名、主机、窗口数、attached 状态、更新时间和 80 行快照摘要。
- 下拉刷新、新建空会话、新建 Claude/Codex/Grok/自定义模板会话。
- 进入、重命名、终止、刷新快照、复制等价 attach 命令。
- 查看 windows 和 panes。
- 保留 Android 的多 socket 扫描：`TMUX_TMPDIR`、`TMPDIR`、`XDG_RUNTIME_DIR`、`/tmp`、`/run/user/$uid`。
- 已知 socket 优先；未知时按会话名发现；只有确认不存在后才能新建，禁止产生“平行 tmux 宇宙”。
- 所有会话名和路径必须使用结构化命令构建器和严格 shell quoting。

### 5.4 终端

- SSH PTY attach/new-session，先确定终端 cell 尺寸再 attach。
- ANSI/VT、16/256/TrueColor、反色、粗体、斜体、下划线、宽字符、Emoji、组合字符。
- vim、nano、htop、tmux、Claude Code、Codex、Grok CLI 主路径可用。
- 旋转和分屏 resize；节流发送 SSH `window-change` 或 Mosh resize。
- scrollback、选择、复制、粘贴、长按菜单、双击回到底部、捏合字号。
- OSC 8 链接需用户操作后打开；OSC 52 剪贴板默认询问或关闭，不能静默读取敏感内容。
- 返回页面时保存快照；显式断开和意外断开有不同状态。

### 5.5 输入与扩展键盘

- 系统 IME 与终端第一响应者共存，中文输入法不跳动、不遮挡终端。
- 常驻横条支持 Esc、Tab、Ctrl、Alt、方向键、Home/End、PgUp/PgDn、符号与自定义文本。
- `General / tmux / CLI` 三套内置布局，支持复制、编辑、排序、新建、删除和 JSON 导入导出。
- Ctrl/Alt 为可见锁存状态，按键后按规则释放；支持 chord 和 text action。
- 扩展面板与系统键盘平滑切换。
- 外接键盘支持 Command 菜单和标准组合键，不抢占终端的 Ctrl 序列。

### 5.6 补全与片段

- 片段 CRUD、标签、插入/插入并执行、`{{host}}`、`{{user}}`、`{{port}}`、`{{session}}`。
- 按主机保存最近命令，LRU 上限 500；密码输入和敏感提示不进入历史。
- 内置 git/tmux/systemctl 等词表。
- 远程路径补全通过节流 SSH exec 执行 `compgen`/安全兜底；失败静默，不阻塞输入。
- 检测 alternate screen/TUI 后自动暂停建议；提供终端内临时开关和全局设置。

### 5.7 Mosh

- SSH 控制面启动 `mosh-server` 并解析 `MOSH CONNECT`。
- UDP、AES-OCB、序列/确认、差分状态、心跳、重传和恢复。
- AUTO 仅在 mosh-server 与 UDP 探测同时通过后选 Mosh。
- Mosh 启动或同步失败自动降级 SSH，并告诉用户具体原因。
- 进入后台时安全冻结和持久化恢复材料；回前台恢复。Mosh key 必须按设备级机密保护。
- 控制面 list/rename/kill/probe 始终使用 SSH，不与 Mosh 数据面耦合。

### 5.8 安装向导

- 缺少 tmux/mosh 时根据 apt、dnf、yum、pacman、apk、brew 生成命令。
- 显示将执行的命令，可复制；打开专用 PTY 预填命令，由用户回车和自行输入 sudo 密码。
- 不采集、不保存、不代填 sudo 密码。
- 完成后自动复检并刷新主机状态；未知系统提供手工安装提示。

### 5.9 设置、安全与迁移

- 系统浅色/深色、现有 6 套主题语义、终端字体/字号/配色、TERM 和 scrollback。
- 启动直达最近会话、防止屏幕休眠、补全开关、键盘方案。
- 应用锁：Face ID/Touch ID/设备密码；进入后台立即隐私遮罩。
- 加密导出/导入主机元数据和片段，默认不包含私钥；用户明确确认后才可包含。
- 必须兼容 Android v1 导出包：PBKDF2-HMAC-SHA256 120,000 次、AES-256-GCM、独立 salt/iv/ciphertext+tag。
- 诊断日志可查看、导出、清空；默认不联网发送，严格脱敏 hostname、用户名、命令内容、密码和密钥。

## 6. iOS 深度交互设计

### 6.1 会话页

- 导航栏标题为 `Muxa`，右侧使用刷新和新增图标。
- 顶部用 segmented control 切换“按主机/最近”，不增加额外卡片层。
- 会话条目采用紧凑列表：状态、名称、主机、窗口数、快照。主操作整行进入，次操作放 context menu 和 bottom sheet。
- 快照使用终端等宽字体，限制行数并显示更新时间；连接刷新不导致列表大幅跳动。

### 6.2 主机页

- 主机以标准 inset list 呈现，状态徽标只表达 tmux、Mosh 和可达性。
- 编辑页采用 iOS `Form` 分区：身份、认证、传输、高级设置。
- 私钥支持粘贴、文件导入和格式检测；敏感字段不提供普通明文预览。
- 保存前可执行“测试连接”，结果直接回到表单字段而不是通用 toast。

### 6.3 终端页

- 终端全屏、无装饰卡片，顶部栏可自动收起。
- SwiftTerm 的 UIKit view 直接占据稳定 viewport；SwiftUI 仅管理顶部状态和底部键盘 chrome。
- 快捷键条放在 `inputAccessoryView`/统一键盘协调器中，与系统键盘同属一个布局链，避免 Android 现有 IME 二次布局问题。
- 网络状态只在变化时显示短暂、非阻塞提示；长期弱网显示小型状态标记。
- 建议条只在普通 shell 输入时出现，绝不覆盖 TUI 关键区域。

### 6.4 可访问性与适配

- 产品 UI 支持 Dynamic Type；终端字体独立缩放，不随 Dynamic Type 破坏网格。
- 所有图标按钮有 accessibility label 和 tooltip；状态不只依赖颜色。
- 支持 Reduce Motion、VoiceOver 的主机/会话管理；终端本体按 SwiftTerm 能力提供基本可访问性。
- iPhone 小屏、标准屏、Max 屏均无文字截断；横屏保留可用终端高度。

## 7. 技术选型与替代矩阵

| 模块 | 候选对比 | 最终建议 | 决策原因 |
|---|---|---|---|
| UI | SwiftUI / UIKit / Flutter | SwiftUI + 少量 UIKit | 原生导航、Form、Sheet、可访问性；终端输入需 UIKit 精确控制 |
| 终端 | SwiftTerm 1,631 stars；Ghostty 58,841 stars；Blink hterm | **SwiftTerm v1.15.x** | MIT、iOS 原生、SPM、商业 SSH App 已使用、TrueColor/Unicode/Metal/测试完整；Ghostty 核心成熟但 libghostty 尚未版本化、API 仍变 |
| SSH | libssh2；Citadel；NMSSH；swift-nio-ssh | **libssh2 1.11.x + 自有薄 Swift adapter** | BSD-3、Blink/VVTerm 生产验证、密钥格式和旧/新算法兼容更完整；Citadel 更现代但密钥口令与其 NIOSSH fork 必须额外承担风险 |
| Mosh | 官方 Mosh/Blink libmoshios；swift-mosh；自研 | **swift-mosh 0.1.x，带强制 POC 门槛** | MIT、Swift 6、真实 mosh-server E2E、VVTerm 已采用；生态历史较短，不能无条件信任。禁止重新手写协议 |
| Mosh 备选 | 官方 Mosh 14,261 stars / Blink | 许可证评审后才可启用 | 技术最成熟，但 GPL-3.0 与 App Store 分发、整 App 开源义务存在实质风险 |
| 数据库 | SwiftData / Core Data / GRDB | **GRDB 7.x** | MIT、8,571 stars、显式 migration、SQLite 可检查、并发与测试稳定 |
| 密钥 | Security.framework / KeychainAccess / Valet | **Valet 5.x + Security 属性审计** | Apache-2.0、活跃、4,168 stars；统一 Keychain 错误处理，仍保留系统属性控制 |
| 生物识别 | 第三方 / LocalAuthentication | **LocalAuthentication** | 系统标准，不引入依赖 |
| 导出加密 | 自研 crypto / CryptoKit + CommonCrypto | **CryptoKit AES-GCM + CommonCrypto PBKDF2** | 系统成熟实现，并可与 Android v1 字节级兼容 |
| 日志 | print / CocoaLumberjack / OSLog | **OSLog** | 隐私级别、性能和本地 Console 支持；另加脱敏导出层 |
| 网络状态 | Reachability 包 / Network.framework | **NWPathMonitor** | 系统能力；只用于状态提示，不用 reachability 预测请求必然成功 |
| 测试 | XCTest / Swift Testing / XCUITest | **Swift Testing + XCTest/XCUITest** | 领域单测用 Swift Testing，系统和 UI/E2E 用 XCTest/XCUITest |

### 7.1 必须先做的技术探针

开发主功能前用 5 个独立样例验证：

1. libssh2：密码、RSA/Ed25519/ECDSA、OpenSSH/PKCS8/PEM、有无 passphrase、host key mismatch、PTY resize。
2. SwiftTerm：中文、Emoji、TrueColor、vim/htop/tmux/Claude/Codex、1 MB 连续输出、选择与输入法。
3. swift-mosh：真实 VPS、UDP 60000-61000、丢包/延迟、后台冻结、前台恢复、进程重启恢复。
4. Android 导出包：iOS 解密导入、iOS 导出后 Android 解密导入。
5. iPhone Simulator：系统中文输入法、软键盘/扩展键盘切换、旋转和 XCUITest 稳定性。

任一探针不达标必须在进入业务开发前更换组件，避免后期返工。

## 8. 推荐架构

```text
MuxaApp
├── AppShell                 Tab、Route、依赖组装、Scene 生命周期
├── Features
│   ├── Sessions            会话列表、快照、创建、更多操作
│   ├── Hosts               CRUD、认证、探测、安装向导
│   ├── Terminal            SwiftTerm host、键盘、补全、状态
│   └── Settings            外观、键盘编辑、片段、备份、隐私
├── Domain
│   ├── Models              Host、TmuxSession、Keyboard、Snippet
│   ├── UseCases            纯业务规则
│   └── Ports               SSH、Mosh、Tmux、Secrets、Repositories
├── Infrastructure
│   ├── SSH                 libssh2 adapter、连接池、known_hosts
│   ├── Mosh                swift-mosh adapter、checkpoint
│   ├── Tmux                命令构建、解析、多 socket
│   ├── Persistence         GRDB migrations/repositories
│   ├── Security            Valet、LocalAuthentication、加密导出
│   └── Diagnostics         OSLog、脱敏文件导出
└── SharedUI                Tokens、反馈、通用 iOS 组件
```

### 8.1 架构规则

- Feature 不直接依赖 libssh2、SwiftTerm 或 GRDB，只依赖 Domain Port。
- SSH 控制面和交互 PTY 使用同一连接管理器，但不同 channel 生命周期。
- Mosh 与 SSH 均实现 `InteractiveTransport`，终端不感知具体传输。
- tmux 命令构建器和解析器是纯 Swift，可用 Android 样例做黄金测试。
- UI 状态只在 `@MainActor` 更新；网络、数据库和解析使用 structured concurrency/actor。
- 不创建类似 Android `MainActivity` 的上帝对象；每个 Feature 有独立 Store/Coordinator。

## 9. 数据与安全设计

### 9.1 存储边界

| 数据 | 位置 | 保护 |
|---|---|---|
| 主机元数据、片段、历史、快照 | GRDB SQLite | App sandbox；备份策略显式配置 |
| 密码、私钥、passphrase | Keychain/Valet | `WhenUnlockedThisDeviceOnly`，不进 iCloud Keychain |
| known_hosts | GRDB 或独立受保护文件 | host:port + key type + fingerprint，变更阻断 |
| Mosh 恢复 key | Keychain | Device-only；显式断开立即删除 |
| Mosh 非密钥 checkpoint | Application Support | 加密/文件保护 Complete，原子写入 |
| 设置 | UserDefaults | 不含凭据和完整命令 |

### 9.2 安全验收

- 仓库、日志、崩溃信息、截图和 XCTest attachment 中不得出现真实凭据。
- 私钥导入后清理临时文件和内存中的可变 buffer；UI 不回显完整私钥。
- host key 首次信任必须由用户操作，变更时默认拒绝。
- 导出私钥需要二次确认和系统分享面板；默认导出永远不含私钥。
- App Switcher 使用隐私遮罩；应用锁失败不泄露主机和快照。
- 完成第三方许可证清单、字体授权、加密出口合规说明和隐私清单。

## 10. iOS 生命周期策略

| 场景 | 行为 |
|---|---|
| 短暂切后台 | 申请有限 background task 完成状态落盘；停止 UI 渲染 |
| SSH 在后台被系统断开 | 回前台健康检查并重连，再 attach 原 tmux 会话 |
| Mosh 切后台 | freeze + checkpoint；回前台 resume，失败则重新 bootstrap/降级 SSH |
| App 被杀 | 冷启动显示本地会话与快照；用户进入时重建控制面和交互面 |
| 网络 Wi-Fi/蜂窝变化 | Mosh 尝试迁移；SSH 标记重连，不循环弹窗 |

“后台永不断线”不是 iOS 可保证的系统能力。产品承诺是现场可恢复、状态可解释、tmux 远端任务不丢失。

## 11. 本机构建与测试方案

### 11.1 环境前置

- 完整 Xcode 与最新稳定 iOS Simulator runtime。
- `sudo xcode-select -s /Applications/Xcode.app/Contents/Developer`。
- XcodeGen 可选；若采用，固定版本并提交 `project.yml`，生成的工程可重复。
- SPM 依赖提交 `Package.resolved`；libssh2/OpenSSL 从固定 tag 在本机构建 XCFramework并校验 checksum。
- GitHub Actions 不执行 build/test/archive；GitHub 只保存源码和普通提交。

### 11.2 本地命令入口

统一提供以下脚本或 Make target：

```text
make bootstrap       安装/检查本机工具、拉取固定依赖
make build-sim       构建 iPhone Simulator Debug
make test-unit       单元与集成测试
make test-ui         本地 XCUITest
make test-e2e        Simulator + VPS 全链路
make archive-local   本地 Release archive
make verify          format + lint + 全部测试 + license 检查
```

所有 DerivedData、测试结果、视频、截图和密钥 fixture 保存在本机忽略目录，不提交仓库。

### 11.3 测试金字塔

- **单元测试：** tmux 命令/解析、shell quoting、状态机、补全、变量、键盘 codec、导入加密、错误映射。
- **组件测试：** GRDB migration、Keychain adapter、known_hosts、SSH/Mosh mock transport、SwiftTerm bridge。
- **UI 测试：** 首启、三 Tab、主机表单、会话操作、键盘编辑、设置、应用锁 harness。
- **真实 E2E：** 本机 Simulator 访问 VPS，覆盖 Android 文档现有 A-K 全组用例。
- **性能测试：** 冷启动、会话列表首屏、PTY 可输入时间、连续输出、内存、滚动帧率。

### 11.4 VPS 测试矩阵

用户提供 VPS 后，在服务器侧准备隔离账号/容器：

| 环境 | 用途 |
|---|---|
| SSH-PASS | 密码认证 |
| SSH-KEY | RSA/Ed25519/ECDSA、含/不含 passphrase |
| MOSH | mosh-server + UDP 60000-61000 |
| BARE | 无 tmux/mosh，验证安装向导 |
| DUAL | 桌面与 App 共用，多 socket 互通 |
| FAIL | 错密码、错 key、错端口、host key 变更 |

VPS 凭据只放本机忽略的 E2E secret store。测试 runner 不打印环境变量、不把密码放 launch arguments、不上传 xcresult。

### 11.5 Simulator 与真机边界

全部自动化 E2E 按要求在本机 iPhone Simulator 完成。为确保上架质量，还需要一个很小的真机 smoke gate，验证 Simulator 无法真实代表的 Face ID/Touch ID、蜂窝/Wi-Fi 漫游、后台挂起、第三方输入法、硬件键盘和内存压力；它不替代 Simulator E2E，也不使用云构建。

## 12. 交付阶段与排期

以 1 名资深 iOS 工程师全职估算约 15-18 周；2 名工程师并行约 10-12 个自然周。Mosh 和终端探针失败会触发重估。

| 阶段 | 时间 | 交付与退出条件 |
|---|---:|---|
| P0 技术探针 | 1.5-2 周 | SSH 密钥矩阵、终端 TUI、Mosh、导入兼容、IME 全通过 |
| P1 工程基座 | 1 周 | SwiftUI 壳、模块边界、GRDB/Valet、主题、测试 harness |
| P2 主机与 SSH | 2 周 | CRUD、known_hosts、连接池、探测、错误体系 |
| P3 tmux 会话中枢 | 2 周 | 多 socket、列表/快照、创建/进入/管理完整 |
| P4 终端与键盘 | 2.5-3 周 | PTY、resize、输入法、Smart Keys、TUI 兼容 |
| P5 Mosh 与生命周期 | 2 周 | AUTO、降级、弱网、后台/前台恢复 |
| P6 补全/片段/安装 | 1.5 周 | 全功能贯通 |
| P7 设置/迁移/安全 | 1.5 周 | 主题、应用锁、Android 双向导入、诊断 |
| P8 iOS 视觉与可访问性 | 1.5 周 | 全尺寸 UI、动效、文案、图标、无障碍 |
| P9 E2E 与发布收口 | 2 周 | Simulator + VPS 全绿、archive、许可证与隐私材料 |

阶段不能仅以“页面完成”退出，必须以对应自动化测试和真实链路通过退出。

## 13. 发布质量门槛

- P0 用例 100% 通过；P1 用例无已知崩溃或数据损坏。
- 主机首次添加到看到会话列表，正常网络下目标小于 3 秒。
- 点击已有快照会话后 300ms 内出现稳定首屏，PTY 可输入时间单独上报。
- 1 MB 连续输出不崩溃、无明显输入阻塞；滚动和键盘动画维持可接受帧率。
- vim/htop/tmux/Claude Code/Codex/Grok CLI 样例通过。
- Android <-> iOS 配置迁移黄金样例全部通过。
- host key mismatch、错误凭据、断网、Mosh 失败均不能造成无限 loading。
- 日志和测试产物 secrets scan 为零发现。
- 本机 clean checkout 可完成 bootstrap、build、test、archive。

## 14. 风险与决策门

| 风险 | 级别 | 处理 |
|---|---|---|
| Mosh 的成熟度与 GPL | P0 | swift-mosh 先做真实兼容探针；失败后暂停并做 Blink/官方 Mosh 许可证决策，禁止自研兜底 |
| SSH 私钥格式/旧服务器算法 | P0 | 采用 libssh2；以 Android 算法矩阵做黄金验收 |
| iOS 后台限制 | P0 | 产品文案改为可恢复；Mosh checkpoint + SSH reattach |
| 终端 TUI/CJK/IME | P0 | SwiftTerm 探针先行；inputAccessoryView 统一布局；保留替换边界 |
| 当前仓库 MIT 与 vendored GPL Termux | P0 | 发布前做整仓许可证审计；Android 当前 LICENSE 表述可能不足，iOS 不复制 Termux 源码 |
| App Store 名称/商标 | P1 | 设备展示名保持 Muxa；上架前检索 Muxa/tmux 名称并保留非官方声明 |
| Ghostty 热门但接口变化 | P1 | 第一版不依赖未版本化 GhosttyKit；SwiftTerm 不达标才重新评估 |
| 本机无完整 Xcode | 阻塞开工 | 安装 Xcode 和 Simulator runtime 后才能创建/构建工程 |

## 15. 最终交付物

- 可本地重现构建的 iOS 源码工程和固定依赖。
- 与 Android 同名同图标的 Muxa iPhone/iPad App。
- 模块架构文档、数据 schema、SSH/Mosh/terminal adapter 说明。
- Android/iOS 双向配置兼容规范和黄金 fixture。
- 单测、集成测试、XCUITest、Simulator + VPS E2E runner。
- 测试报告、性能报告、许可证清单、隐私清单、加密出口说明。
- 本地 Debug/Release archive 流程；不依赖 GitHub 构建。

## 16. 立即执行顺序

1. 安装并切换完整 Xcode，创建 `ios/` 原生工程与本地构建入口。
2. 先完成 libssh2、SwiftTerm、swift-mosh、Android 导出兼容四个技术探针。
3. 根据探针结果冻结依赖版本和许可证策略。
4. 以现有 `docs/E2E-TEST-CASES.md` 为母版生成 iOS 追溯矩阵，确保无功能遗漏。
5. 按 P1-P9 实施，每个阶段用本地 iPhone Simulator 测试后再进入下一阶段。

## 17. 参考项目

- SwiftTerm: https://github.com/migueldeicaza/SwiftTerm
- libssh2: https://github.com/libssh2/libssh2
- Citadel: https://github.com/orlandos-nl/Citadel
- SwiftNIO SSH: https://github.com/apple/swift-nio-ssh
- Blink Shell: https://github.com/blinksh/blink
- Mosh: https://github.com/mobile-shell/mosh
- swift-mosh: https://github.com/wiedymi/swift-mosh
- Remux: https://github.com/h3nock/remux
- VVTerm: https://github.com/vivy-company/vvterm
- Conduit: https://github.com/gwitko/Conduit
- GRDB: https://github.com/groue/GRDB.swift
- Valet: https://github.com/square/Valet
