# iOS 真实 VPS E2E 记录

日期：2026-08-01  
范围：Step 6（SSH / Mosh bootstrap / PTY-TUI / UDP）  
目标：用户提供的 VPS（本次新增 `devps`；`hkvps` 历史结果保留；host 信息不写入凭据；认证信息只从本机 `~/.env` 读取）
历史 hkvps 验证环境：Xcode 26.5、iOS 26.5、iPhone 17 Pro Simulator（arm64）；本次 devps 复验：Xcode 26.6、iOS 26.5、同一 Simulator（arm64）

## 安全边界

- `host` 与 `rootpassword` 通过 `~/.env` 的 `hkvps info` / `devps info` 区块静默读取；本报告不记录实际值。
- 密码只通过进程环境变量传入探针，不进入命令参数、Git、测试 fixture、报告或 xcresult 附件。
- SSH host key 使用本次扫描得到的 SHA-256 指纹固定校验；不使用盲目接受 host key。
- 临时 mosh connect 文件只存在于本机临时目录和 Simulator 沙盒，测试后删除；报告不记录 endpoint key。
- E2E 输出只保留阶段、通过/失败和错误分类，不保留认证字符串。

## 结果

| 场景 | 结果 | 证据 |
|---|---|---|
| SSH password authentication（hkvps） | PASS | iPhone 17 Pro Simulator 启动 Debug App 后，libssh2 连接、exec、PTY 与关闭完成 |
| SSH exec（hkvps） | PASS | `muxa-ssh-probe` 输出与退出状态校验通过 |
| SSH PTY resize（hkvps） | PASS | PTY `120x40` / `1200x800` window-change 完成 |
| SSH TUI 基础 fixture（hkvps） | PASS | PTY 输出 `MUXA_TUI_READY`，发送输入后收到 `MUXA_TUI_ECHO:muxa-tui-input` |
| 远端 tmux 可用性（hkvps） | PASS | 真实 VPS 返回 tmux 版本信息 |
| mosh-server bootstrap（hkvps） | PASS | 成功取得 `MOSH CONNECT`；UDP 端口范围已开放 |
| Mosh UDP round-trip（hkvps / Simulator） | BLOCKED | Swift Mosh probe 返回 `echoTimedOut`；VPS 端口捕获在 Simulator 运行期间观察到 0 个 UDP 包 |
| Mosh UDP round-trip（hkvps / macOS package probe） | BLOCKED | 同一真实 endpoint 的 swift-mosh real probe 也返回 `timeoutWaitingForOutput` |
| Mosh startup health timeout | PASS（本机单测） | UDP socket ready 但没有首个远端 host operation 时，在有界超时内返回 typed `startupTimeout`；不会把静默丢包误报为已连接 |
| AUTO fallback | PASS（本机单测） | 覆盖 bootstrap 失败与 startup timeout 两条 Mosh 失败路径，随后切换 SSH；`devps` live Mosh 已单独通过，未把 `hkvps` UDP 阻塞伪造为通过 |

## devps 真实 UDP 验证（2026-08-01）

| 场景 | 结果 | 证据 |
|---|---|---|
| SSH password / host-key / mosh-server bootstrap（devps） | PASS | 使用 `devps info` 凭据；SSH 严格 host-key 校验通过，远端 `mosh-server` 可启动并取得临时 `MOSH CONNECT` |
| Mosh UDP round-trip（devps / macOS swift-mosh） | PASS | 真实 endpoint 的 package E2E echo round-trip 通过 |
| Mosh UDP round-trip（devps / iOS Simulator） | PASS | iPhone 17 Pro Simulator Debug App 的 `MoshLiveProbeRunner` 首次 echo 与 pause 后 resume echo 均通过 |
| devps 轻度弱网（100ms delay + 10ms jitter + 5% loss / macOS） | PASS | 远端临时 `tc netem` 条件下 macOS package E2E 通过 |
| devps 轻度弱网（100ms delay + 10ms jitter + 5% loss / iOS Simulator） | PASS | 同条件下 iOS Simulator Mosh live probe 通过 |
| devps 较强弱网（250ms delay + 15% loss / macOS） | PASS（单轮） | 本轮 8 秒 package probe 通过；尚未视为完整弱网矩阵结论 |
| devps 较强弱网（250ms delay + 15% loss / iOS Simulator） | PASS（单轮） | 本轮 iOS Simulator live probe 通过；尚未覆盖切网与真机 |
| 弱网测试后的远端 qdisc 清理 | PASS | `tc netem` 规则验证已清理；没有把临时 endpoint key 写入报告或 Git |

## devps tmux C 组命令级验证（2026-08-01）

在 `devps` 上使用临时独立 tmux socket 创建 1 个 session、2 个 window 和 2 个 pane，并按应用当前命令构建器/发现器的路径执行：

| 场景 | 结果 |
|---|---|
| 多 socket 发现并去重 | PASS |
| list sessions | PASS |
| list windows（2 windows） | PASS |
| list panes（2 panes） | PASS |
| capture-pane 快照 | PASS |
| socket-scoped rename / has-session | PASS |
| kill-session 清理 | PASS |

这证明 `devps` 上真实 tmux 的 C 组控制命令和 socket 目标可用；应用内完整会话列表 UI、双 socket 桌面对照和交互式 attach 仍未冒充为已完成。

## 此前 hkvps 补充验证（2026-08-01）

- 使用 `~/.env` 中 `hkvps info` 区块的凭据重新执行 SSH host-key scan 与 `mosh-server new` bootstrap，结果仍为 PASS。
- 使用同一临时 endpoint 在 macOS swift-mosh real probe 中重新执行 UDP round-trip，结果为 `UDP unavailable`；没有把 bootstrap 成功误当作 UDP 可用。
- 新增并通过 MoshInteractiveTransport 回归测试：socket ready 无 host response 的 `startupTimeout`、强制 MOSH 失败，以及 AUTO 自动 SSH fallback。
- IOS-065 已接入：Mosh checkpoint 只通过 Keychain-backed SecretStoring 持久化，按 host/session 隔离；新终端实例可恢复，成功恢复或显式关闭后清理。
- 本次临时 connect 文件和 host-key 文件已在验证后删除；endpoint key 未写入报告或 Git。

## 结论与后续阻塞

`devps` 的真实 UDP 已确认可用：macOS swift-mosh 与 iOS Simulator 的真实 Mosh round-trip 均通过，且 5% loss / 100ms delay 的轻度弱网条件也通过；本轮 250ms / 15% loss 单轮探针同样通过。因此此前 `hkvps` 的失败不能归因于客户端协议在所有环境下不可用，更可能是该 VPS 或其云侧 UDP/网络路径问题。

SSH 控制面、PTY、resize、输入/输出和基础 TUI fixture 在 `hkvps` 与 `devps` 均有通过证据。客户端仍会在 Mosh socket ready 但没有远端响应时以有界 `startupTimeout` 失败，AUTO 因而能及时切换 SSH；这修复了静默丢包下的状态误判，但不等于所有外部网络条件已完成验证。

仍不能把 IOS-066 / IOS-096 标记为全量完成：Wi-Fi/蜂窝切换、真实 iPhone 硬件、完整重复弱网矩阵和发布签名 gate 尚未执行；`hkvps` UDP 仍保持 BLOCKED。

## 验证入口

- SSH live runner：`ios/Muxa/Infrastructure/SSH/SSHLiveProbeRunner.swift`
- Mosh live runner：`ios/Muxa/Infrastructure/Mosh/MoshLiveProbeRunner.swift`
- 本机验证：`MuxaTests`、`MuxaUITests`、SwiftLint、SwiftFormat、Simulator build
