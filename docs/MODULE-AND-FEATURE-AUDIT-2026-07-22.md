# m-tmux 模块与功能审计报告

**日期：** 2026-07-22  
**版本基线：** 0.1.59-yahei-ime（`app.mtmux.android`）  
**目的：**  
1）按 Android 应用能力拆解模块，标注「手搓 / 借用生态」；  
2）对照成熟开源方案是否可替换；  
3）子功能清单 + 是否实现 + 代码链路是否贯通。

---

## 一、总览结论

| 维度 | 结论 |
|------|------|
| **架构形态** | 标准 Gradle 多模块：`app` + `feature/*` + `core/*` + 传输/终端引擎 |
| **核心依赖成熟度** | SSH（sshj+BC）、终端 VT（Termux 源码 vendoring）、UI（Compose Material3）偏成熟 |
| **高风险手搓区** | **Mosh 全协议客户端**、**IME/快捷条布局**、**部分键盘/补全业务** |
| **死代码 / 双轨** | 自研 `terminal-emulator`（VtBuffer）基本**不再驱动主路径**；主路径是 Termux View |
| **产品规格完成度** | v0.3.1 计划内「做」的项大多已落地；**链路通但体验债重**（IME 跳动、雅黑渲染、安装页曾断链） |
| **明确未做（规格）** | 电脑推送 Companion、命令面板 UI、一键代装、iOS、商店级打磨 |

**一句话：** 不是「全手搓」，也不是「组装成品」——**传输与终端底层一半是成熟库/源码，产品壳与 Mosh/IME 大半是自研。**

---

## 二、工程模块地图（Gradle 实际模块）

```
app                    组装 + MainActivity 上帝类导航
├── feature/sessions   会话列表 UI
├── feature/hosts      主机 / 安装向导 UI
├── feature/settings   设置 / 键盘编辑 / 外观 / 片段
├── feature/terminal   终端壳 + IME chrome + Termux 接入
├── core/model         领域模型
├── core/data          Room / DataStore / 密钥 / 键盘仓库
├── core/ui            主题 Token / 字体目录 / Snackbar
├── ssh                SSH 引擎封装 + tmux 控制面
├── mosh               Mosh 客户端（自研协议栈）
├── termux-emulator    VT 模拟器（vendored Termux）
├── termux-view        终端 View（vendored Termux，有本地改动）
├── terminal-emulator  早期自研 VT（遗留，主路径不用）
├── autocomplete       补全引擎
├── desktop-push-api   推送接口 NoOp
└── command-palette-api 命令面板接口 NoOp
```

约 **2.2 万行** 业务源码（不含 build 产物）。体量最大：`termux-emulator` ~5.1k、`termux-view` ~3.0k、`app` ~2.7k、`ssh` ~1.7k、`mosh` ~1.2k。

---

## 三、能力模块拆解：手搓 vs 生态

下表按「一个 Android 远程终端 App 通常需要的能力」拆，不限于 Gradle 模块名。

| # | 能力模块 | 当前实现 | 类型 | 成熟开源可选项 | 是否建议替换 | 说明 |
|---|----------|----------|------|----------------|--------------|------|
| 1 | **应用壳 / 导航** | Jetpack Navigation Compose + 手写多 `if (screen)` 全屏层 | **半生态** | Navigation Compose（已用）、Compose Destinations、Voyager | 可选重构 | 功能可用；`MainActivity` 过重（~1900 行）是维护债 |
| 2 | **UI 框架** | Compose Material3 + 自研主题 Token | **生态 + 薄壳** | Material3（已用） | 无需换 | 主题/玻璃风格自研合理 |
| 3 | **SSH 传输** | **sshj** 0.38 + BouncyCastle + eddsa | **生态 + 封装** | sshj（已用）、ConnectBot `sshlib`、JSch（老）、libssh2 NDK | **保持 sshj** | 封装层 `SshjEngine`/`ConnectionManager` 自研；算法矩阵已针对 Android 打补丁 |
| 4 | **SSH 认证与密钥** | 密码/私钥 PEM/口令短语；EncryptedSharedPreferences | **半自研** | AndroidX Security Crypto（已用）、sshj 密钥加载 | 保持 | `SecretStore`/`KeyMaterialLoader` 业务自研 |
| 5 | **known_hosts** | 自研确认对话框 + 存储 | **半自研** | sshj KnownHosts 机制 | 可加深对接 | UI 与策略是产品层 |
| 6 | **PTY 会话** | sshj Session + allocatePTY + shell/exec | **生态 + 胶水** | 同上 | 保持 | `PtySession`/`openPtyShell` 胶水自研 |
| 7 | **tmux 控制面** | 自研远程 shell 脚本（多 socket 发现、list/kill/rename…） | **手搓** | 无标准库；可参考 tmux 官方语义 | **不必换** | 这是产品差异化；脚本复杂但已打通 |
| 8 | **Mosh 客户端** | **纯 Kotlin 自研**：UDP、AES-OCB(BC)、fragment、diff | **重手搓** | 官方 mosh C++（NDK）、Blink/其他桌面实现无 Android 库 | **高风险区** | 无「拿来即用」的成熟 Android Mosh SDK；NDK 绑官方 mosh 是长期选项 |
| 9 | **终端 VT 模拟** | **Termux terminal-emulator** vendoring（GPLv3） | **成熟源码借用** | Termux、ConnectBot termlib、Ghostty 思路（非 Android） | **保持 Termux** | 正确选择；需遵守 GPLv3 |
| 10 | **终端渲染 / 手势** | **Termux terminal-view** vendoring + **大量本地改**（CJK/IME/freeze） | **成熟源码 + 深改** | Termux view、ConnectBot termlib | 保持，慎改 | 雅黑/跳动问题多出在本地改动 |
| 11 | **遗留自研 VT** | `VtBuffer` / `VtTerminalView` | **手搓遗留** | — | **建议下线或隔离** | 主路径已切 Termux；仍被依赖模块引用，易混淆 |
| 12 | **IME / 底部 Chrome** | 自研 `ImeBottomController` + Compose slot | **重手搓** | WindowInsets / IME Animation API（系统）、Termux extra-keys 模式 | 可参考 Termux 简化 | 抖动/空白带主战场 |
| 13 | **快捷键条 / 布局** | 自研 Compose 横条 + 扩展面板 + JSON schema | **手搓** | Termux extra-keys（JSON）、ConnectBot 有限 | 产品向可保留 | Termius 无开源对等 |
| 14 | **键盘编辑器** | 自研 CRUD / 导入导出 | **手搓** | — | 保留 | 规格内功能 |
| 15 | **本地持久化** | Room + DataStore | **生态** | Room/DataStore（已用） | 无需换 | |
| 16 | **安全存储** | security-crypto EncryptedSharedPreferences | **生态** | 同上；可选 Android Keystore 直管 | 可增强 | |
| 17 | **会话快照** | Room 存 capture-pane 文本 | **手搓业务** | — | 保留 | 链路：SSH capture → DB → 列表展示 |
| 18 | **命令补全** | 自研 `AutocompleteEngine` + 远程 `compgen` | **手搓** | 无标准移动库；可参考 fish/zsh 思路 | 保留 | TUI 暂停启发式已有 |
| 19 | **片段 Snippets** | Room + UI | **手搓** | — | 保留 | |
| 20 | **主机环境探测 / 安装向导** | 自研 probe 脚本 + 安装 PTY | **手搓** | — | 保留 | 0.1.57 修过 sessionGeneration 断链 |
| 21 | **生物识别锁** | AndroidX Biometric | **生态** | 已用 | 无需换 | |
| 22 | **配置加密导出** | 自研 AES 导出 JSON | **手搓** | — | 可换更标准格式 | |
| 23 | **诊断日志** | 自研 `DiagLog` 文件 | **手搓** | Timber + 文件 tree | 可选 | 当前够用 |
| 24 | **桌面推送** | 接口 + NoOp | **占位** | 自建 Companion 协议 | 规格：本版不做 | |
| 25 | **命令面板** | 接口 + NoOp | **占位** | — | 规格：本版不做 | |
| 26 | **字体资源** | assets 多款 Mono + 系统 CJK 解析 | **资源借用** | JetBrains/Noto 等 OFL | 注意授权文案 | |
| 27 | **构建 / 签名** | AGP + 共享 debug keystore | **生态** | — | 发布需正式 key | |

### 3.1 借用成熟度分层

```
████████████ 成熟库/源码直接用
  Compose / Navigation / Room / DataStore / Biometric / sshj / BC / Termux VT

██████░░░░░░ 成熟源码 + 深改（债集中）
  termux-view 渲染、IME freeze、字体 cell 逻辑

████░░░░░░░░ 合理自研业务
  tmux 控制脚本、主机/会话 UI、补全/片段、安装向导

██░░░░░░░░░░ 高风险自研
  Mosh 全栈、IME slot 状态机
```

### 3.2 可替换优先级（务实）

| 优先级 | 动作 | 收益 | 成本 |
|--------|------|------|------|
| P0 | **不要再深改 Termux 渲染**，改动要对照上游 diff | 减少雅黑/跳动回归 | 低 |
| P1 | 确认 **主路径只走 Termux**，标记/删除 `VtBuffer` 主用假设 | 降低双 VT 心智负担 | 中 |
| P2 | Mosh：真机矩阵 + 防火墙文档；长期评估 **NDK 官方 mosh** | 可靠性 | 高 |
| P3 | 拆 `MainActivity` 导航 | 可维护性 | 中 |
| — | 为「换库而换库」（如换 sshj→JSch） | **不建议** | 高、无收益 |

**说明：** 移动端「Termius 级产品」没有完整开源等价物。ConnectBot 是开源 SSH 客户端，但不是 tmux 中枢 + Mosh + 补全套件。能借的是**底层**（SSH/VT），不是**产品壳**。

---

## 四、功能子清单 + 实现状态 + 链路是否通

图例：  
- **实现**：规格/代码是否有  
- **链路**：从 UI 入口 → 数据/网络 → 回写 UI 是否在代码上连上  
- **体验债**：已知问题（不等于断链）

### 4.1 主机与连接

| 子功能 | 实现 | 链路 | 备注 |
|--------|------|------|------|
| 主机列表 | ✅ | ✅ | `HostsScreen` |
| 添加/编辑主机 | ✅ | ✅ | 密码/密钥/口令 |
| 删除主机 | ✅ | ✅ | |
| 主机字段清洗（粘贴 hostname 带密码等） | ✅ | ✅ | `HostFieldSanitizer` |
| SSH 密码登录 | ✅ | ✅ | |
| SSH 私钥登录 | ✅ | ✅ | PEM 粘贴 |
| known_hosts 首次确认 | ✅ | ✅ | 对话框 + 重试 |
| 连接池 / 断线重试 | ✅ | ✅ | `ConnectionManager` |
| 传输偏好 AUTO/SSH/MOSH | ✅ | ✅ | AUTO 依赖 UDP 就绪标志 |
| 延迟探测展示 | ✅ | ✅ | probe 内 latencyMs |

### 4.2 tmux 控制面

| 子功能 | 实现 | 链路 | 备注 |
|--------|------|------|------|
| list-sessions（多 socket） | ✅ | ✅ | 真机路径常用 |
| list-windows / panes | ✅ | ✅ | 更多菜单 |
| new-session / attach -A | ✅ | ✅ | |
| kill / rename | ✅ | ✅ | |
| capture-pane 快照 | ✅ | ✅ | 进列表缓存 |
| 复制 attach 命令 | ✅ | ✅ | |

### 4.3 终端会话

| 子功能 | 实现 | 链路 | 备注 |
|--------|------|------|------|
| 会话列表 / 分组 / 最近 | ✅ | ✅ | |
| 下拉刷新 | ✅ | ✅ | |
| 进入会话（先 UI 后 attach） | ✅ | ✅ | size-first attach |
| 快照秒显 | ✅ | ✅ | leave 时 snapshot |
| SSH PTY attach tmux | ✅ | ✅ | |
| 尺寸 WINCH | ✅ | ✅ | 有 debounce/freeze |
| Scrollback 手势 | ✅ | ✅ | Termux view |
| 系统 IME 输入 | ✅ | ⚠️ | **链路通**；跳动/空白/雅黑渲染有债 |
| 快捷横条 | ✅ | ✅ | |
| ⋯ 循环（IME ↔ 面板） | ✅ | ⚠️ | 逻辑已改；贴底/跳动需真机确认 |
| Ctrl 锁存 | ✅ | ✅ | |
| 离开 dispose / 返回重连 | ✅ | ✅ | ON_RESUME health check |
| 终端外观（字体/配色/字号） | ✅ | ✅ | 字号 13–52；雅黑 0.1.59 修裁切 |
| TERM / buffer 设置 | ✅ | ✅ | prefs → prepareSession |

### 4.4 Mosh

| 子功能 | 实现 | 链路 | 备注 |
|--------|------|------|------|
| SSH 拉起 mosh-server | ✅ | ✅ | |
| 解析 MOSH CONNECT | ✅ | ✅ | |
| UDP + AES-OCB | ✅ | ⚠️ | **代码通**；依赖防火墙 UDP 60000–61000 |
| UDP 探测写 prefs | ✅ | ✅ | `probeEnvFull` |
| AUTO 仅在 UDP 绿时用 Mosh | ✅ | ✅ | |
| 失败降级 SSH | ✅ | ✅ | |
| 弱网状态展示 | ✅ | ⚠️ | 有状态流；真机弱网压测不足 |

### 4.5 环境探测与安装

| 子功能 | 实现 | 链路 | 备注 |
|--------|------|------|------|
| 探测 tmux / mosh-server / OS / PM | ✅ | ✅ | |
| 探测驱动安装向导 UI | ✅ | ✅ | 文案已精简 |
| 预填安装命令 | ✅ | ✅ | |
| 打开安装 PTY 执行 | ✅ | ⚠️ | **0.1.57 修 sessionGeneration 断链**；需持续回归 |
| 复制命令 | ✅ | ✅ | |
| 安装后复检 + toast | ✅ | ✅ | force toast |
| 手势返回（向导） | ✅ | ✅ | 0.1.56+ |
| 一键代装 | ❌ | — | 规格 Backlog |

### 4.6 补全与片段

| 子功能 | 实现 | 链路 | 备注 |
|--------|------|------|------|
| 建议条 UI | ✅ | ✅ | |
| 防抖 suggest | ✅ | ✅ | |
| 片段 CRUD | ✅ | ✅ | |
| 片段变量 | ✅ | ✅ | |
| 历史命令 | ✅ | ✅ | |
| 内置词表 | ✅ | ✅ | |
| 远程路径补全 | ✅ | ⚠️ | 依赖 SSH exec；失败静默 |
| TUI 时暂停补全 | ✅ | ✅ | alt-screen 启发式 |
| 总开关 | ✅ | ✅ | |

### 4.7 设置与安全

| 子功能 | 实现 | 链路 | 备注 |
|--------|------|------|------|
| 主题 6 套 | ✅ | ✅ | |
| 键盘方案切换/编辑/导入导出 | ✅ | ✅ | |
| 加密导出/导入配置 | ✅ | ✅ | 默认不含私钥 |
| 生物识别锁 | ✅ | ✅ | |
| 诊断日志导出/清空 | ✅ | ✅ | |
| 防止休眠 | ✅ | ✅ | |

### 4.8 规格明确不做 / 仅接口

| 子功能 | 实现 | 链路 |
|--------|------|------|
| 电脑推送 Companion | 接口 NoOp | 无 UI |
| 命令面板 ⌘K | 接口 NoOp | 无 UI |
| iOS | ❌ | — |
| 云账号 | ❌ | — |

---

## 五、关键链路「代码贯通」风险清单（真机债）

这些**不是「没写」**，而是**写了但易坏 / 体验未收口**：

| 风险 | 表现 | 根因类型 | 状态（截至 0.1.59） |
|------|------|----------|-------------------|
| 安装 PTY 黑屏「连接中」 | shell 永不 attach | UI 未订阅 `sessionGeneration` | **已修 0.1.57** |
| 雅黑拉丁字母异常（M≈N） | 比例字体超格被裁 | 渲染 cell/clip | **已修 0.1.59** |
| IME 收起留白 | 快捷条悬空 | slot 未跟降 / watchdog 误提交 | **已修 0.1.59**（待你验收） |
| IME 打开跳动 / 绿条跳 | 多次 WINCH | chrome 状态机 + 远端 redraw | **未彻底收口** |
| Mosh 在部分云主机不可用 | 一直 SSH | UDP 安全组 | 产品说明问题，非断链 |
| `MainActivity` 上帝类 | 改一处易回归 | 架构债 | 未拆 |
| 双 VT（Termux + VtBuffer） | 认知/依赖混乱 | 历史迁移 | 主路径 Termux |

---

## 六、与「成熟开源」对照的务实建议

### 6.1 应该继续借、别重写

- **VT + 终端 View：** Termux（当前）——业界 Android 开源最硬。  
- **SSH：** sshj + BC——别退回 JSch。  
- **UI/存储/生物识别：** AndroidX——已正确。

### 6.2 没有「直接借用成品」的部分

- **tmux 会话中枢 + 多 socket 合并**  
- **Termius 式快捷条 / 安装向导 / 补全片段**  
- **完整 Mosh 客户端 Android 库**（几乎空白市场）

### 6.3 若要「少自研」，唯一大动作

| 选项 | 含义 |
|------|------|
| A. 保持现状 + 收敛体验债 | 推荐短期 |
| B. Mosh 改 NDK 链官方 mosh-client | 中长期可靠性 |
| C. 砍 Mosh，只做 SSH+tmux | 减复杂度，损卖点 |
| D. 整应用改 fork ConnectBot | 丢 tmux 中枢与大量产品功能，**不推荐** |

---

## 七、功能完成度打分（规格视角）

| 阶段（v0.3.1） | 规格完成 | 链路贯通 | 体验收口 |
|----------------|----------|----------|----------|
| A 工程基座 | 100% | 100% | — |
| B SSH/主机 | 100% | ~95% | 良好 |
| C 终端/键盘 | ~100% | ~90% | **IME/字体仍债** |
| D Mosh | ~100% 代码 | ~70% 真机 | 依赖网络环境 |
| E 安装 | ~100% | ~90% | 需回归 |
| F 补全 | ~100% | ~90% | 可接受 |
| G 会话体验 | ~100% | ~95% | 良好 |
| H 视觉打磨 | ~70% | — | 进行中 |
| I 质量发布 | ~40% | — | 缺完整真机矩阵 |
| 推送/面板 | 0% 实现（符合规格） | NoOp | 刻意不做 |

**综合：** 作为 v0.3.1「功能做完」约 **85–90%**；作为「可安心日用」约 **70%**（IME/字体/Mosh 环境拖后腿）。

---

## 八、建议的排查与改造顺序（给你排期用）

1. **验收 0.1.59：** 雅黑 M/N、收起键盘贴底。  
2. **IME 跳动：** 冻结进一步「发明算法」；对照 Termux extra-keys（键盘与终端同窗、少二次布局）做减法。  
3. **代码卫生：** 标注 `terminal-emulator` 为 legacy；禁止新代码依赖 `VtBuffer` 主路径。  
4. **Mosh：** 出「安全组检查清单」+ 真机矩阵，而不是再写一层协议。  
5. **结构：** 拆 `MainActivity` 导航（install/terminal/settings 路由）。  
6. **质量：** AND-132+ 真机用例与 E2E 矩阵。

---

## 九、附录：关键依赖版本（当前）

| 库 | 版本/来源 |
|----|-----------|
| sshj | 0.38.0 |
| BouncyCastle | 1.78.1 |
| eddsa | 0.3.0 |
| Room | 2.6.1 |
| DataStore | 1.1.1 |
| Compose BOM | 2024.09.02 |
| Navigation | 2.8.1 |
| Biometric | 1.1.0 |
| Termux VT/View | vendored 源码（GPLv3） |
| Mosh | 自研 Kotlin |

---

*报告生成：基于仓库 `android/` 与 `docs/TODO-TRACKER.md`、`product-brief.md`、`android-full-plan-v0.3.1.md` 及 0.1.53–0.1.59 实机问题回流。*
