# Pane 决策与调研纪要 v0.2

> 日期：2026-07-16  
> **注意：v0.3 已覆盖发布策略 / 推送 / Mosh 等。** 当前权威：`android-full-plan-v0.3.md`。  
> 本文保留主题、SSH 库调研、连接策略、键盘竞品等仍有用的背景。

---

## 1. 已拍板决策一览

| 议题 | 决策 |
|------|------|
| 主视觉 | **#1 Sequoia Glass** + **#2 Big Sur Soft** 为系统默认深/浅主题 |
| 默认跟随 | 随 **系统深浅色** 自动切换（可手动锁定） |
| 去掉 | **#3 Classic Aqua** 不进产品 |
| 个性化主题 | 其余 4 套保留：**Space Gray Pro / Terminal Pure / Vision Layers / Paper Editorial** |
| 桌面推送 MVP | **手机主屏捷径**（系统原生能力，**零额外安装**） |
| 远期推送 | 调研「手机一点 → 电脑桌面自动弹出终端窗」；依赖桌面侧能力，**不进 MVP** |
| 底层 | 不以「调用系统 ssh 命令行」为主；采用 **嵌入式 SSH 库 + 自研连接层**（见 §4） |
| 体验架构 | 连接池、Keepalive、异步预热、快照先显后连 — **架构期预埋** |
| 键盘 | 综合竞品，采用 **「常驻扩展键条 + 可切换布局」**（见 §7） |

---

## 2. 主题体系（落地规格）

### 2.1 角色划分

| 角色 | 风格 | 触发 |
|------|------|------|
| **系统深色（默认 Dark）** | Sequoia Glass | `UIUserInterfaceStyle/dark` / Android night |
| **系统浅色（默认 Light）** | Big Sur Soft | 系统浅色 |
| 个性化 | Space Gray / Terminal Pure / Vision / Paper | 设置 → 外观 → 主题 |

### 2.2 行为

- 默认：`Appearance = System`（深→Sequoia，浅→Big Sur）
- 用户可选：`Always Light` / `Always Dark` / `Custom theme = …`
- 终端配色：每套 UI 主题绑定一套 **终端 palette**（可单独改「终端配色」而不换壳）
- Aqua 从 `ui-styles.html` 与路线图移除，归档备注「曾评估、不采用」

### 2.3 实现提示

- Design Token 分两套基础（light/dark）+ 4 套 overlay JSON  
- Android：`isSystemInDarkTheme()` + Compose 主题  
- iOS：`@Environment(\.colorScheme)` + SwiftUI Asset Catalog

---

## 3. 「桌面推送」澄清（两种完全不同的东西）

之前文案容易混，这里拆开说明。

### 3.1 你期望的「无感一键弹出电脑终端窗」

```
手机 Pane 点「推送到桌面」
    → 家里/公司的电脑屏幕上
    → 自动弹出一个终端窗口
    → 并 attach 到指定 tmux 会话
```

| 问题 | 说明 |
|------|------|
| 能否纯靠手机 SSH 到 **无头 VPS** 弹出 GUI？ | **不能**。VPS 通常没有你看的那块显示器。 |
| 会话实际在哪？ | tmux 在「服务器」上；你看的窗口必须在 **有桌面环境的那台机器**（笔记本 / 台式）上打开。 |
| 无感弹出需要什么？ | 桌面侧必须有一个「接收指令并拉起 Terminal.app / Windows Terminal / 某终端」的能力。 |

**可选远期方案（均需桌面侧配合，MVP 不做）：**

| 方案 | 用户成本 | 体验 |
|------|----------|------|
| A. Pane Desktop 伴侣（本机常驻托盘） | 装一个桌面 App | 最好：手机 → 推送 → 本机弹窗 |
| B. 系统自动化（macOS Shortcuts / 脚本 + 开放端口/中继） | 用户自配 | 可玩，脆弱 |
| C. 仅 SSH 到「那台有桌面的机器」执行 `osascript`/`wt.exe` | 无额外 App，但机器须在线且允许远程开 GUI | 中等，平台碎片多 |
| D. 通知到电脑「点一下打开」 | 轻 | 非无感 |

结论：**「手机一点、电脑自动弹窗」在工程上可行，但不是零组件**；至少要有桌面接收端或可遥控的 GUI 主机。与「零安装」冲突时，优先零安装。

### 3.2 MVP 采用的「主屏捷径」（不是装软件）

**不是**再装一个 App，也**不是**在电脑桌面放图标。

而是：用 **手机系统自带的「添加到主屏幕」能力**，给 **某一个 tmux 会话** 做一个入口：

| 平台 | 机制 | 用户要装别的东西吗？ |
|------|------|----------------------|
| Android | `ShortcutManager` 请求「固定到主屏幕」→ 主屏多一个图标 | **否**，系统 API |
| iOS | 主屏幕快捷方式 / App 快捷操作 / 稍后小组件 | **否**（系统能力；iOS 对「任意钉图标」限制多于 Android） |

用户路径（MVP）：

1. 在会话卡片点「推送到主屏幕」  
2. 系统弹一次确认（Android 固定快捷方式）  
3. 主屏出现例如 `claude-main` 图标  
4. 以后点图标 → **直接打开 Pane 并 attach 该会话**（深链 `pane://session/{id}`）

**价值**：通勤时拇指一次进战场，仍无需「打开 App → 找会话」。  
**局限**：操作发生在 **手机主屏**，不会让你的 **电脑显示器** 弹出窗口。

### 3.3 产品文案建议

| 旧叫法 | 新叫法 | 含义 |
|--------|--------|------|
| 推送到桌面（含糊） | **固定到主屏幕** | MVP |
| — | **推送到电脑**（远期） | 需桌面伴侣 / GUI 主机 |

UI 按钮 MVP 文案：**「固定到主屏幕」**，副标题可写「一键直达此会话，无需安装其他应用」。

---

## 4. SSH / 连接底层调研

### 4.1 先澄清：方案里的「OpenSSH」指什么

v0.1 写「标准 OpenSSH + tmux」指的是：

- **协议与服务器兼容**：对接世上 99% 的 `sshd`（OpenSSH 服务端）  
- **不是**要求手机 App 去 `Process` 调用系统 `ssh` 二进制  

移动端几乎都会 **链入 SSH 协议库**，自己管 socket、认证、PTY、信道。

### 4.2 同类应用底层（公开信息汇总）

| 应用 | 平台 | 连接/协议层（公开或社区共识） | 备注 |
|------|------|-------------------------------|------|
| **Termius** | 全平台 | **libssh2**（商店更新日志多次出现 patched libssh2）；支持 **Mosh** | 闭源；重体验与跨端 vault |
| **Blink Shell** | iOS | 长期基于 **libssh / OpenSSH 移植 + Mosh**；更新日志提 LibSSH / OpenSSH / 自研 Mosh | 开源血统；断线续传是卖点 |
| **Prompt** | iOS | 闭源专有栈 | 体验细，底层不透明 |
| **JuiceSSH** | Android | 传统 Java SSH（生态常见 **JSch / 类似库**） | 老牌 Android |
| **ConnectBot** | Android | 开源 SSH 客户端谱系 | 可参考实现 |
| **SwiftNIO SSH** | Apple 生态库 | 纯 Swift SSH **构建块**（类比 libssh2，非成品客户端） | 适合自研 iOS 客户端内核 |
| **sshj / Apache MINA SSHD** | JVM | Java 实现 | Android 可选，体积与维护需评估 |
| **libssh / libssh2** | C | 业界嵌入式事实标准 | 经 JNI/FFI 进双端 |

### 4.3 方案对比（我们该选什么）

| 方案 | 速度可控 | 多信道/复用 | 移动维护成本 | 推荐阶段 |
|------|----------|-------------|--------------|----------|
| 系统 `ssh` 子进程 | 差 | 依赖 ControlMaster 文件 | 双端权限/沙盒麻烦 | **不采用** |
| **libssh2（C）+ 自研会话层** | 好 | 自建 channel 复用 | 中；双端 JNI/Cinterop | **MVP 首选底座** |
| libssh（C） | 好 | 功能全 | 同左 | 与 libssh2 二选一评估 |
| SwiftNIO SSH（仅 iOS） | 好 | 自建 | iOS 纯 Swift 爽，Android 仍要另一套 | iOS 可后期收敛 |
| 纯 Java sshj（仅 Android） | 中 | 自建 | Android 方便，iOS 无 | 不统一 |
| **Mosh** | 弱网极强 | UDP 状态同步 | 服务端要装 mosh-server | **v1.x 增强**，非 MVP 必须 |

### 4.4 推荐架构（双端）

```
┌─────────────────────────────────────────────┐
│  UI（Compose / SwiftUI）                      │
├─────────────────────────────────────────────┤
│  SessionFacade（会话/快照/捷径，与协议无关）   │
├─────────────────────────────────────────────┤
│  ConnectionManager（异步）                    │
│   · 连接池 / 预热 / Keepalive / 重连状态机    │
│   · 信道：exec（tmux 元数据）+ shell/PTY     │
├─────────────────────────────────────────────┤
│  SshEngine（嵌入式）                          │
│   推荐：libssh2 或 libssh via FFI             │
│   iOS 可选第二实现：SwiftNIO SSH              │
├─────────────────────────────────────────────┤
│  网络：TCP (+ 未来 Mosh / ProxyJump)          │
└─────────────────────────────────────────────┘
         ↕ 协议兼容
   用户 VPS 上的 OpenSSH sshd + tmux
```

**原则：**

1. **Wire 兼容 OpenSSH 服务端**；客户端用库而不是 CLI。  
2. **一套 ConnectionManager 语义** 双端对齐（状态机、错误码、重试策略）。  
3. MVP 不做 Mosh；接口预留 `Transport: SSH | Mosh`。  
4. 密钥只进 Keystore/Keychain，库只拿句柄/短期材料。

### 4.5 MVP 技术选型结论

| 层 | 选型 |
|----|------|
| 协议库 | **libssh2**（或评估期 A/B libssh）经 FFI |
| 不选 | 壳调用系统 OpenSSH 客户端 |
| 增强（后期） | Mosh、ProxyJump、SSH 证书、FIDO |
| 服务端假设 | 标准 `sshd` + `tmux`；**用户无需装我们的 agent 也能 MVP** |

---

## 5. 连接体验与稳定性（竞品做法 + 我们架构）

### 5.1 竞品常见手段

| 手段 | 谁在用/常识 | 用户感知 |
|------|-------------|----------|
| **SSH Keepalive**（ServerAlive 类心跳） | 所有正经客户端 | 少「假死断线」 |
| **连接复用 / 多信道** | OpenSSH ControlMaster 思想；App 内多 channel 共享 TCP | 二次进入秒开 |
| **Mosh** | Blink、Termius | 地铁/切换网络仍续上 |
| **断线自动重连 + 状态提示** | 主流 | 「重连中…」而不是黑屏 |
| **网络变化监听** | 移动端必备 | Wi‑Fi↔蜂窝切换可恢复 |
| **后台策略克制** | iOS 尤甚 | 不骗用户「永远后台不断」 |

### 5.2 Pane 连接架构（MVP 就要有的异步策略）

```
App 启动 / 回前台
  ├─ async 预热「最近 1 个 Host」TCP+认证（可开关，耗电提示）
  ├─ 列表页：async 拉 tmux ls（走 exec 信道，可复用已预热连接）
  └─ 进入会话：
        ① 立即显示本地快照（0ms 级）
        ② 并行 attach PTY
        ③ 首帧到达后交叉淡入真终端

连接状态机（每 Host 一份）
  Idle → Connecting → Ready → Degraded → Reconnecting → Ready
                     ↘ Failed（可重试）

策略参数（可调）
  · keepalive_interval: 30–45s（蜂窝可更短）
  · connect_timeout / auth_timeout
  · max_retry + 指数退避 + 抖动
  · pool: 每 Host 1 条主连接，多 channel（list / capture / pty）
  · 前台才强预热；后台仅可选短 keepalive（默认关）
```

### 5.3 体验指标（验收用）

| 场景 | 目标 |
|------|------|
| 已预热 Host，点会话 → 可输入 | ≤ 1.0s（快照先出） |
| 冷连接同 Host 第二次会话 | 明显快于第一次（复用） |
| 地铁闪断 5s 内恢复 | 自动重连成功并恢复 PTY 或提示「会话仍在服务器」 |
| 用户可见 | 始终有明确状态：连接中 / 已连接 / 重连中 / 已断开 |

### 5.4 架构预埋（接口级，即使 UI 未全做）

- `HostConnectionPool`  
- `TmuxControlChannel`（exec：list/capture/kill…）  
- `PtySession`（交互）  
- `SnapshotCache`  
- `ReconnectPolicy`  
- 事件总线：`ConnectionEvent` 驱动 UI 与角标  

---

## 6. 应用里「会用到哪些命令」——用户几乎不用打

**重要：** `attach` 等是 **App 在 SSH 里代你执行的控制指令**，不是要用户在界面里背命令。  
正常路径：点卡片「进入」→ App 完成连接与附着。

### 6.1 控制面（通常走 SSH `exec`，不占用交互 TTY）

| 目的 | 典型远程命令（实现可微调） |
|------|----------------------------|
| 列会话 | `tmux list-sessions -F '…'` |
| 列窗口/窗格 | `tmux list-windows` / `list-panes` |
| **附着会话** | `tmux attach-session -t <name>` 或 `tmux switch-client`（视场景） |
| 没有则创建 | `tmux new-session -A -s <name>`（**一条命令创建或附着**，很适合「模板启动」） |
| 文本快照 | `tmux capture-pane -p -t <target> -S -…` |
| 会话是否存在 | `tmux has-session -t <name>` |
| 杀会话 | `tmux kill-session -t <name>` |
| 新建窗口 | `tmux new-window -t <name> -n …` |
| 探测环境 | `command -v tmux`；`echo $TERM` 等 |
| 延迟探测 | SSH 连通后 RTT / `echo ok` |

### 6.2 交互面（PTY shell 信道）

进入会话后，用户看到的是 **已 attach 的 tmux 画面**（Claude/Codex/Grok TUI）。  
此后输入原样进 PTY；App **不必**再解释用户敲的每个字符。

### 6.3 产品层面对用户暴露的动作（中文）

| UI 文案 | 背后大致动作 |
|---------|----------------|
| 进入 | 连接池 → PTY → `new-session -A` 或 `attach-session` |
| 刷新列表 | `list-sessions` 等 |
| 刷新快照 | `capture-pane` → 缓存 |
| 新建编码会话 | `new-session -s claude-…` + 可选 send-keys 启动 CLI |
| 固定到主屏幕 | 只写本地深链，**不跑**远程命令 |
| 终止会话 | 确认后 `kill-session` |

### 6.4 和「OpenSSH」的关系

- 传输：SSH 协议（库）  
- 远程业务：主要是 **tmux 子命令字符串**  
- 用户不需要会 SSH/tmux 语法也能用；高级用户可在设置里看「等价命令」便于调试  

---

## 7. 扩展键盘调研与选型

### 7.1 竞品模式

| 应用 | 键盘形态 | 特点 |
|------|----------|------|
| **Termius** | 终端上方/下方 **扩展键条** | Ctrl、Esc、Tab、方向、符号；可配置；大众最熟悉 |
| **Blink** | 强扩展 + 硬件键盘友好 + 多布局 | 硬核；学习成本略高；弱网/桌面级叙事 |
| **Prompt** | 精致扩展条 + 手势 | 视觉与触控反馈好 |
| **JuiceSSH** | 特殊键行 + 音量键映射 Ctrl 等 | Android 经典；密度高 |
| **系统键盘** | 几乎没有 Ctrl/Esc | 写 TUI 不够用 |

### 7.2 我们的结论（MVP）

**主方案：Termius 路线为主，吸收 Blink/Prompt 的可配置与触感。**

| 元素 | 规格 |
|------|------|
| 位置 | 终端与系统键盘之间的 **常驻扩展条**（可收起） |
| 默认键 | `Esc` `Ctrl` `Alt` `Tab` `←↑↓→` `~` `/` `-` `|` 以及 `PgUp/PgDn` |
| Ctrl 组合 | 点按 Ctrl 进入「下一键组合」态（高亮），再点字母发送 ^X |
| 布局切换 | 至少 3 套：**通用 / tmux / CLI 工具**（可后续开放编辑） |
| tmux 布局 | `Ctrl-b` 前缀提示、`n/p/c/d/%/"` 等快捷（发送真实按键序列） |
| 手势 | 双指滚动 → 终端 scrollback；三指可考虑切换窗格（后期） |
| 硬件键盘 | 外接键盘走系统快捷键映射表（iPad/平板优先） |
| 收起 | 一键隐藏扩展条，扩大 TUI 可视区 |

### 7.3 不优先

- 重造一整套「形似系统键盘的自定义输入法」（审核/系统冲突/成本高）  
- 音量键劫持（可访问性与系统冲突，可选高级设置）  

### 7.4 评估标准（实现时 A/B）

1. 单手能否完成 Esc、Ctrl+C、方向选择  
2. Claude/Codex TUI 常用键路径 ≤ 2 次点按  
3. 扩展条高度不吃掉超过 ~12% 竖屏  
4. 收起后误触率低  

---

## 8. 更新后的 MVP 范围（v0.2）

### 做

1. 双端原生壳 + **System 深浅 = Sequoia / Big Sur**  
2. Host 管理 + 密钥本地安全存储  
3. **嵌入式 SSH 库** + ConnectionManager（池化/keepalive/重连骨架）  
4. 会话列表 + 进入（底层 `new-session -A` / attach）  
5. 快照 L1（capture-pane + 缓存先显）  
6. **固定到主屏幕**（零安装）  
7. **扩展键盘 v1**（通用布局）  
8. 无账号  

### 明确不做（MVP）

- Classic Aqua  
- 手机驱动 **电脑 GUI 自动弹窗**（记入 v2 桌面伴侣）  
- Mosh（接口预留）  
- 云同步账号  
- 强制 pane-agent  

### 个性化主题

- 四套可切换；商店截图可用 Vision 打冲击力，但默认仍是系统跟随  

---

## 9. 文档与工程下一步

1. ~~决策固化~~（本文）  
2. 更新 `ui-styles.html`：Aqua 移除；标注默认深/浅 + 个性化  
3. 合并关键决策进 `product-brief.md` 头部  
4. 输出 **连接层状态机草图 + 主题 Token JSON 骨架**  
5. 高保真流程：启动 → 列表 → 进入 → 键盘 → 固定主屏幕  
6. 工程骨架：Android 优先或双端 —— 待你点首发端  

---

## 10. 飞书归档

本文与更新后的 UI HTML、brief 增量发往项目专群：  
(private project chat)（仅此群）。
