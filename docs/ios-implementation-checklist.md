# Muxa iOS 实施清单

> 对应方案：`docs/ios-product-and-technical-plan-v1.0.md`
> 状态：`[ ]` 未开始、`[~]` 进行中、`[x]` 已完成、`[!]` 外部阻塞
> 完成定义：代码、自动化测试、文档与本机构建同时通过，才可标记完成；真实 VPS、真机或网络条件未满足时，不将本机 Simulator 结果冒充全量完成。

## 2026-08-15 进度快照

- 对照 `origin/main`（`cb0770b`）安卓最新合入：iOS 已补齐设置/关于/隐私、片段、补全、安装向导、加密导入导出、诊断+LogHub、应用锁、离开时可见快照、alt-screen 远程翻页、捏合冻结缩放、创建会话静默刷新。
- 2026-08-15 续：App Icon 已换成 Ambient Coral Glow；设置 Custom 可选 6 套应用主题（Material You / Light / Space Gray Pro / Terminal Pure / Vision Layers / Paper Editorial）；会话详情含窗口列表，主机与会话支持手动排序。
- iOS 桌面显示名已确认：`PRODUCT_NAME` / `CFBundleDisplayName` = **Muxa**，设置关于区也显示 Muxa。
- 安卓专用项未原样搬（IME page-curl、Xiaomi pinch ticks、sshj Reader NPE、系统状态栏对比度）；行为等价已在 iOS 原生路径落地。
- 真机安装、设置/补全/安装 E2E 仍待本机 PM 会话。

## 2026-08-01 进度快照

- Step 1：Mosh bootstrap cleanup 竞态已修复并提交 `267ed8a`。
- Step 2：MoshInteractiveTransport 已接入远程终端流程并提交 `151416d`。
- Step 3：AUTO / 强制 SSH / 强制 MOSH / SSH fallback 已实现并提交 `c65422f`。
- Step 4：终端后台 freeze/resume、网络状态 UI 已验证并提交 `b2e6c4a`。
- Step 5：SessionListStore 加载竞态与 `demo` UI fixture 已修复；Unit/UI 验证通过（UI 13/13），提交 `b3e8071`。
- Step 6：`hkvps` 的 SSH password、host-key、exec、PTY、resize、TUI 与 Mosh bootstrap 通过，但 UDP round-trip 仍阻塞；新增 `devps` 后，macOS swift-mosh、iOS Simulator Mosh、5% loss/100ms delay 及本轮 15% loss/250ms 单轮探针均通过，详见 `docs/ios-live-e2e-report.md`。
- 当前结论：真实 UDP 已在 `devps` 证实可用；`hkvps` UDP、完整弱网/切网矩阵、真实远端 tmux/TUI 全矩阵和真机 gate 仍不能标记完成。

## P0 工程与交付基座

- [x] IOS-000 核对 Xcode、SDK、Simulator、Swift、CMake、OpenSSL 和本机工具链。
- [x] IOS-001 固化完整产品与技术方案。
- [x] IOS-002 建立 XcodeGen 原生 iOS 工程，部署目标 iOS 17，Swift 6 严格并发。
- [x] IOS-003 建立 App、Unit Tests、UI Tests targets 和可注入依赖容器。
- [x] IOS-004 固定 SwiftTerm、GRDB、Valet、swift-mosh 版本与 `Package.resolved`。
- [x] IOS-005 建立本机 bootstrap/build/test/verify/archive 命令，不使用 GitHub Actions。
- [x] IOS-006 建立 Debug/Release 配置、版本号、Bundle ID、权限与隐私清单基座。
- [x] IOS-007 生成与 Android 同视觉的 iOS AppIcon 和 Launch Screen。
- [x] IOS-008 建立代码格式、lint、敏感信息扫描和许可证清单入口。

退出条件：clean checkout 可在 iPhone Simulator 构建、启动并执行 Unit/UI smoke tests。

## P1 关键技术探针

- [x] IOS-010 SwiftTerm 嵌入 SwiftUI，验证输入、resize、scrollback、TrueColor 和 Unicode fixture。
- [x] IOS-011 构建固定版本 libssh2 + OpenSSL iOS/Simulator XCFramework。
- [x] IOS-012 建立 libssh2 Swift adapter 探针：连接、exec、PTY、window-change、关闭。
- [x] IOS-013 验证密码、RSA、Ed25519、ECDSA、OpenSSH/PKCS8/PEM 与 passphrase 矩阵。
- [x] IOS-014 验证 host key 首次确认和 mismatch 阻断。
- [x] IOS-015 集成 swift-mosh，完成协议 fixture 与生命周期单测。
- [~] IOS-016 `devps` 已完成 mosh-server bootstrap 与真实 UDP round-trip（macOS、iOS Simulator 均通过）；`hkvps` UDP round-trip 仍超时，跨 VPS 结论与完整弱网验证尚未收敛。（见 `docs/ios-live-e2e-report.md`。）
- [x] IOS-017 实现 Android v1 配置包解密/加密，完成双向黄金 fixture。
- [x] IOS-018 形成探针报告并冻结依赖；失败项按方案触发替换决策。

退出条件：除需要用户 VPS 的 IOS-016 外，关键组件在 Simulator 和本地测试中通过；IOS-016 在发布前必须解除阻塞。

## P2 领域、数据与安全基座

- [x] IOS-020 定义 Host、TmuxSession、Window、Pane、Snippet、KeyboardLayout、Suggestion 模型。
- [x] IOS-021 定义 SSHControlPlane、InteractiveTransport、TmuxControlling、SecretStoring 等 Port。
- [x] IOS-022 建立连接与 Scene 生命周期状态机，使用 actor 隔离并发状态。
- [x] IOS-023 建立 GRDB schema、migration、Host/Snippet/History/Snapshot/CachedSession repositories。
- [x] IOS-024 使用 Valet/Keychain 保存密码、私钥、passphrase 和 Mosh key。
- [x] IOS-025 实现 known_hosts store、SHA-256 指纹和严格校验策略。
- [x] IOS-026 实现 OSLog 脱敏日志与本地诊断导出。
- [x] IOS-027 为数据库迁移、Keychain、状态机、脱敏建立测试。

退出条件：领域层不依赖 UI 或具体库；数据可迁移；凭据不会进入数据库、日志或测试附件。

## P3 主机与 SSH 控制面

- [x] IOS-030 主机列表、空态、状态与刷新。
- [x] IOS-031 添加/编辑表单：身份、认证、传输、高级设置。
- [x] IOS-032 主机字段清洗、格式检测和本地校验。
- [x] IOS-033 私钥粘贴、文件导入、类型检测和 passphrase。
- [x] IOS-034 测试连接、首次 host key 确认、mismatch UI。
- [x] IOS-035 ConnectionManager：连接复用、健康检查、超时、指数退避和前台恢复。
- [x] IOS-036 SSH exec 多 channel、错误分类与面向用户的文案。
- [x] IOS-037 探测 tmux、mosh-server、OS、包管理器、延迟和 UDP 状态。
- [x] IOS-038 删除主机并原子清理关联数据和凭据。
- [~] IOS-039 主机/SSH 单测与 UI tests 已完成；`devps` iOS Simulator live probe 的 password、pinned host-key、exec、PTY、resize、TUI 已通过，完整 B 组与真机主机 UI smoke 仍待补。

## P4 tmux 会话中枢

- [x] IOS-040 移植安全 shell quoting 和 tmux 命令构建器。
- [x] IOS-041 多 socket 发现与去重，覆盖 TMPDIR/TMUX_TMPDIR/XDG_RUNTIME_DIR。
- [x] IOS-042 list sessions/windows/panes 与健壮解析。
- [x] IOS-043 capture-pane 快照、缓存与 stale 状态。
- [x] IOS-044 会话页按主机/最近两种视图、刷新和状态保持。
- [x] IOS-045 新建空会话和 Claude/Codex/Grok/自定义模板。
- [x] IOS-046 进入、重命名、终止、刷新快照、复制 attach 命令。
- [x] IOS-047 已知 socket 优先 attach，确认不存在后才 new-session。
- [x] IOS-048 启动直达最近会话与失败回退。
- [~] IOS-049 golden parser tests、Simulator UI tests 与真实 VPS tmux 可用性检查已完成；`devps` 真实 socket 的 list sessions/windows/panes、capture、rename、has-session、cleanup C 组命令级验证已通过，应用内完整会话 UI、双 socket 对照和交互式 attach 仍待补。

## P5 终端与输入系统

- [x] IOS-050 建立稳定 SwiftTerm UIKit host 和 SwiftUI bridge。（完成稳定 UIKit host、SwiftUI UIViewControllerRepresentable bridge、终端复用、几何回调、焦点管理与本机单测。）
- [x] IOS-051 SSH PTY 数据流、首帧快照、attach、关闭和错误状态。（完成 libssh2 非阻塞 PTY 读写、独立 SSH interactive control port、attach command/geometry、快照首帧、EOF/错误状态与本机 Simulator 单测。）
- [x] IOS-052 cell/pixel 尺寸测量、旋转和 window-change 节流。（完成 cell/viewport pixel metrics、旋转布局重测、SwiftUI 配置透传与可取消的 geometry debounce，并通过本机单测。）
- [x] IOS-053 scrollback、选择、复制、粘贴、链接和回到底部。（完成 scrollback、选择、复制、粘贴、链接和回到底部，并通过本机 Simulator 单测。）
- [x] IOS-054 系统 IME、中文组合输入、第一响应者与键盘避让。（透传 SwiftTerm UITextInput marked text、中文组合提交/取消、系统输入 traits、第一响应者与 keyboardLayoutGuide 避让，并通过本机 Simulator 单测。）
- [x] IOS-055 inputAccessoryView 快捷横条和稳定 viewport。（新增可配置、可滚动且可访问的常驻快捷横条，覆盖 Esc/Tab/Ctrl/方向/Home/End/PgUp/PgDn/符号/文本动作，并通过本机 Simulator 单测。）
- [x] IOS-056 General/tmux/CLI 内置布局与 key/chord/text/modifier actions。（实现 General/tmux/CLI 内置布局、键序列编码、chord、text、Ctrl/Alt 锁存及一次性释放，并通过本机 Simulator 单测。）
- [x] IOS-057 键盘布局复制、编辑、排序、新建、删除、JSON 导入导出。（新增内置布局保护、自定义布局 CRUD/排序、SwiftUI 编辑页、JSON 文档导入导出与本机单测。）
- [x] IOS-058 扩展键盘、系统键盘切换和硬件键盘命令。（新增扩展输入面板、系统键盘切换、Command-C/V/A、Escape/Ctrl-C 硬件命令透传与本机 Simulator 单测。）
- [~] IOS-059 本机已通过 TrueColor/CJK/Emoji、vim/htop/tmux/Claude/Codex/Grok fixture、Simulator UI E2E；`devps` SSH TUI 与 tmux 基础矩阵已通过，远端 CLI/D/E 组和真机交互仍待补。

## P6 Mosh 与生命周期

- [x] IOS-060 SSH bootstrap mosh-server 与解析 MOSH CONNECT。（新增安全命令构建、SSH 执行、MOSH CONNECT endpoint 解析、失败分类与本机 fixture 单测。）
- [x] IOS-061 MoshInteractiveTransport 连接终端输入输出和 resize。（已接入真实终端流程并通过本机 transport 单测；`devps` iOS Simulator live Mosh round-trip 已通过，`hkvps` UDP 仍受 IOS-066 外部阻塞。）
- [x] IOS-062 AUTO 选择规则、强制 SSH/MOSH 和明确降级。（覆盖 bootstrap 失败与 Mosh startup timeout 两条 fallback 单测；`devps` live Mosh 已通过，未把 `hkvps` 的 UDP 阻塞伪造为通过。）
- [~] IOS-063 Mosh 弱网/同步状态和非干扰式 UI。（网络状态 UI、同步/诊断状态及首个远端响应 startup timeout 已实现；`devps` macOS/iOS Simulator 的基础与 5% loss 弱网 round-trip 已通过，完整同步/切网矩阵仍待补。）
- [x] IOS-064 后台 freeze、敏感 checkpoint、前台 resume 和显式清理。（已接入终端 scenePhase 流程、网络状态 UI，并通过 Simulator/Unit 验证。）
- [x] IOS-065 App 被杀后的快照恢复与重新 bootstrap。（Mosh checkpoint 按 host/session 隔离后写入 Keychain-backed SecretStoring；新终端实例会恢复 checkpoint，恢复/关闭后清理，并通过 Simulator Unit 回归测试。）
- [~] IOS-066 完成 Wi-Fi/蜂窝切换、丢包和 VPS E2E G 组。（`devps` macOS/iOS Simulator UDP、5% loss/100ms delay 与本轮 15% loss/250ms 单轮探针已通过；Wi-Fi/蜂窝切换、重复弱网矩阵、hkvps UDP 和真机仍待补。）

## P7 生产力功能

- [x] IOS-070 补全引擎：片段、历史、内置词表、排序与去重。
- [x] IOS-071 远程路径补全、节流、取消和失败静默。
- [x] IOS-072 alternate-screen/TUI 检测与自动暂停。
- [x] IOS-073 片段 CRUD、标签、变量替换、插入/执行。
- [x] IOS-074 命令历史按主机 LRU 和敏感输入排除。
- [x] IOS-075 探测驱动安装向导和包管理器命令模板。
- [x] IOS-076 专用安装 PTY、预填、复制、用户 sudo 和安装后复检。
- [ ] IOS-077 完成补全/片段/安装 E2E F/H 组。

## P8 设置、迁移与隐私

- [x] IOS-080 系统浅/深色、现有主题语义与终端配色。
- [x] IOS-081 字体、字号、TERM、scrollback、防休眠和启动行为。
- [x] IOS-082 应用锁、Face ID/Touch ID/设备密码与后台隐私遮罩。
- [x] IOS-083 Android/iOS 加密导入导出，默认排除私钥，显式包含二次确认。
- [x] IOS-084 诊断查看、导出、清空与 secrets scan。
- [x] IOS-085 关于页、tmux 非官方声明、开源许可证、隐私清单。
- [x] IOS-086 保留 DesktopPushPort/CommandPalettePort NoOp，UI 无入口。
- [ ] IOS-087 完成设置、安全、备份 E2E I/K 组。

## P9 iOS 体验与发布质量

- [ ] IOS-090 会话/主机/设置页面达到原生 iOS 视觉标准。
- [ ] IOS-091 iPhone 小屏/标准/Max、横屏和 iPad 自适应。
- [ ] IOS-092 Dynamic Type、VoiceOver、Reduce Motion、非颜色状态表达。
- [ ] IOS-093 加载、空态、离线、错误、破坏性确认和触觉反馈统一。
- [ ] IOS-094 冷启动、首屏、PTY 可输入、1 MB 输出、内存与帧率指标。
- [x] IOS-095 SessionListStore 定向 Unit、全量 Unit（94 cases）、全量 UI suite（13/13）和 iPhone 17 Pro Simulator E2E 已通过；`make verify` 的 build、lint、format、secrets scan、license check 也已通过。
- [~] IOS-096 `devps` SSH/TUI 与 iOS Simulator Mosh live round-trip 已通过；Mosh UDP 全矩阵、网络切换和真实硬件 smoke gate 仍未完成，`hkvps` UDP 继续 BLOCKED。
- [~] IOS-097 本机 `archive-local` Release archive 已通过，许可证检查已通过；签名配置、隐私材料和真实设备发布 gate 仍待执行。
- [x] IOS-098 独立 clean worktree 已通过 `make verify` 与 `make archive-local`；Simulator Unit/UI、Lint、Format、secrets scan、license check 和 Release archive 均通过。
- [~] IOS-099 已补充 Step 6 E2E 报告，记录 `devps` UDP PASS、`hkvps` UDP BLOCKED、弱网单轮结果、startup timeout、持久化 checkpoint 与凭据边界；待完整切网/真机/发布外部验证后再关闭全部 P0/P1 gate。
