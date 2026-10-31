# m-tmux v0.3.1 — Android 全功能方案 + TODO（当前权威）

> 日期：2026-07-16  
> 工作名倾向：**m-tmux**（命名分析见 [`naming-and-brand.md`](./naming-and-brand.md)）  
> 覆盖 v0.3 中与下列冲突的条目。  
> 策略：不做 MVP；**先 Android 全功能**，再 iOS。  
> 飞书归档：仅项目私有群。

---

## 0. 相对 v0.3 的拍板变更

| 议题 | v0.3 | **v0.3.1** |
|------|------|------------|
| 应用名 | Pane | 倾向 **m-tmux**；备选 Attach/Remux（见命名文） |
| 桌面 Companion | Phase1 完整做 | **App 内只留接口/占位**；有用户需求再实现 Companion |
| 安装 tmux/mosh | A 代装 + B 交互 + C 复制 | **仅 B 交互式预填**（+ 可选 C 复制）；A 代装以后再说 |
| 命令面板 | Phase1 建议做 | **只留接口**，体验稳定后再做 |
| 命令补全 | 未单列 | **Phase1 完整做**（类 Termius Autocomplete + Snippets） |

**继承不变：**

- 无 MVP；Android → iOS  
- 无手机主屏捷径  
- SSH + Mosh 第一阶段都做；探测引导  
- 会话列表 = 管理中枢  
- Termius 型扩展键盘（可配置）  
- 主题：Sequoia / Big Sur + 4 个性化  
- 快照先显后连  

---

## 1. 产品一句话

**m-tmux**：移动端远程 **tmux 会话启动器 + 终端**（SSH / Mosh），会话一等公民，Termius 级键盘与命令补全；无账号。电脑推送能力 **预留接口**，Companion 后置。

### 1.1 做 / 不做（第一版 Android）

| 做 | 不做（本版） |
|----|----------------|
| Android 原生全功能客户端 | iOS |
| SSH + Mosh | 云账号 |
| tmux/mosh 探测 + **交互式预填安装** | 一键代装（sudo 密码代填） |
| 会话列表中枢 + 快照 | 手机主屏捷径 |
| Termius 键盘 + **命令补全/片段** | 完整桌面 Companion 实现 |
| 主题 2+4 | 命令面板完整 UI（仅接口） |
| 桌面推送 **API/接口预留** | 真实配对/通知/拉起终端 |

### 1.2 核心路径

```
添加 Host
  → 探测 tmux / mosh-server
  → 缺失则「交互安装」：预填命令的 PTY，用户自行确认/输密码
  → 会话列表（快照）
  → 进入 → 快照秒显 → SSH 或 Mosh
  → 扩展键盘 + 输入时命令补全 / 片段
  →（未来）推送到电脑 — 本版按钮可隐藏或点按提示「即将推出」
```

---

## 2. 桌面推送：仅接口（v0.3.1）

### 2.1 产品态度

- 方案 D（通知一点开）**产品方向仍保留**。  
- **本版不实现** Companion、不实现局域网配对、不做真实推送。  
- 代码与设置中预留：

```text
DesktopPushPort (interface)
  - listPairedDesktops(): empty
  - isAvailable(): false
  - pushSession(hostId, sessionName): Result.NotImplemented
  - pairing API stubs
```

- UI：设置里可有「电脑（即将推出）」灰色入口，或完全隐藏功能入口（推荐 **隐藏入口，保留代码接口**，避免半残体验）。  
- 文档标注：用户验证全功能后，按需启动 DES-* 任务。

### 2.2 从 TODO 移除的本版交付

- 全部 `DES-001+` 实现类任务 → 移入 **Backlog / 接口期仅 AND-DES-API**  
- 原 AND-080～085 实现 → 改为接口与占位（见 §9）

---

## 3. 安装 tmux / mosh：仅交互预填

### 3.1 流程

1. 探测失败 → 安装向导  
2. 根据发行版生成命令（如 `sudo apt-get install -y tmux mosh`）  
3. **打开专用 PTY 会话**，将命令 **预填到输入行或回显说明 + 一行待执行命令**  
4. 用户回车执行，在终端内完成 sudo 密码等交互  
5. 结束后 App **复测** 并刷新徽章  

### 3.2 可选

- **复制命令**到剪贴板（辅助，实现成本低，建议保留）  
- **一键代装 + sudo 框**：明确 **不做**，进 Backlog  

### 3.3 安全

- 不采集、不代填 sudo 密码  
- 安装会话与普通会话一样走 PTY，无额外提权通道  

---

## 4. 会话管理（不变）

- **常驻会话列表 = 唯一管理中枢**  
- 卡片：进入 / 更多（杀、重命名、刷新快照、复制 attach 命令…）  
- **命令面板**：`CommandPalettePort` 接口 + 空实现；UI 不进主导航  

---

## 5. 键盘（Termius 模型，不变）

```
终端
快捷横条（常显）……………… [扩展]
扩展面板（可选）
系统 IME（默认）
```

可配置键位、多套方案、导入导出 JSON。

---

## 6. 命令补全（类 Termius · Phase1 完整做）

参考 Termius [Autocomplete](https://docs.termius.com/terminal/autocomplete-and-shell-integration) / Snippets：输入时出建议条，来源含历史、片段、路径等。

### 6.1 用户体验

```
用户在终端输入字符
  → 快捷横条上方或输入附近弹出「建议列表」
  → 点选：插入 / 或插入并执行
  → 可关闭补全；可只对「行首/空格后」触发
```

### 6.2 建议来源（第一版范围）

| 来源 | 说明 | 优先级 |
|------|------|--------|
| **Snippets 片段** | 用户保存的命令/脚本；按名称或内容模糊匹配 | P0 |
| **本地输入历史** | 本 App 内该 Host/全局发送过的命令行（客户端侧记录） | P0 |
| **内置常用命令** | 可配置词表：git/tmux/systemctl 等（静态 + 可扩展 JSON） | P0 |
| **路径补全** | 完整 shell 路径需远程配合；第一版可用 **简化策略**（见下） | P1 |
| **远端 shell 集成** | 类 Termius 深集成（可选 agent/脚本） | Backlog |

**路径补全简化（P1 可做）：**

- 检测输入含 `cd ` / `./` / `/` 时，SSH exec `bash -lc 'compgen -d'` 或 `ls` 节流请求（防抖 200ms+）  
- 失败则静默，不挡输入  

### 6.3 片段（Snippets）管理

| 能力 | 说明 |
|------|------|
| CRUD | 名称、正文、标签、是否「执行」还是「仅粘贴」 |
| 变量 | 可选 `{{host}}` `{{user}}` 等本地变量替换 |
| 入口 | 设置 → 片段；终端补全面板「管理」；扩展键「Snippets」 |
| 同步 | 无账号；随加密配置导出/导入 |

### 6.4 架构接口

```text
AutocompleteEngine
  - suggest(prefix, context): List<Suggestion>
  - sources: SnippetSource, HistorySource, BuiltinSource, PathSource?

SnippetRepository  (Room)
CommandHistoryStore (按 host 分桶，上限 LRU)
```

### 6.5 与 TUI 的关系

- Claude/Codex 等 **全屏 TUI** 时：补全可能干扰 → 提供  
  - 自动：检测「应用模式」/ bracketed paste 等启发式 **暂停补全**  
  - 手动：终端菜单「补全开/关」  
- 在普通 shell 行编辑时默认开启  

### 6.6 不做（本版）

- 密码补全进建议列表（安全）  
- 多主机广播执行片段（Termius 企业向，后置）  

---

## 7. 连接层 SSH + Mosh（不变要点）

- 控制面：SSH exec（list / capture / 探测）  
- 交互面：SSH PTY 或 Mosh（AUTO）  
- 连接池、keepalive、重连、快照先显  
- 添加 Host 时探测 tmux / mosh-server  

---

## 8. Android 技术栈（微调）

| 层 | 选型 |
|----|------|
| 展示名 | **m-tmux**（已锁定） |
| 包名 | **`app.mtmux.android`**（已锁定） |
| UI | Kotlin · Compose · 主题 Token |
| 模块 | `app`, `core-ui`, `core-data`, `ssh`, `mosh`, `terminal`, `autocomplete`, `desktop-push-api`（仅 API） |
| SSH/Mosh | NDK 库 + ConnectionManager |
| 补全 | `autocomplete` 模块 + Room 片段/历史 |

---

## 9. 详细 TODO（v0.3.1）

> 确认后开工。ID 以本表为准；旧 v0.3 中 DES 实现类、代装 A、命令面板实现、主屏捷径均已删除或降级。

### 9.0 设计 / 协议

| ID | 任务 | 验收 |
|----|------|------|
| DOC-001 | 高保真：会话列表、主机、终端+键盘+补全、探测/交互安装 | 可开发对照 |
| DOC-002 | Design Token：6 主题 | JSON |
| DOC-003 | 键盘布局 schema + 3 套默认 | JSON |
| DOC-004 | 补全/片段 schema | JSON |
| DOC-005 | 文案与免责（Not affiliated with tmux） | 进关于页 |
| DOC-006 | 安装预填命令模板表（发行版） | scripts/docs |
| DOC-007 | 命名定稿后全局替换（m-tmux / 包名） | 一致 |
| DOC-008 | `DesktopPushPort` / `CommandPalettePort` 接口说明 | 短文 |

---

### 9.1 阶段 A — 工程基座

| ID | 任务 | 验收 |
|----|------|------|
| AND-001 | 多模块工程（含 autocomplete、desktop-push-api） | 同步成功 |
| AND-002 | 4 Tab：会话 / 主机 / **设置相关**（电脑 Tab 本版可并入设置「即将推出」或去掉） | 导航清晰 |
| AND-003 | 主题 System + Sequoia/Big Sur | 跟随系统 |
| AND-004 | 4 个性化主题 | 可切换持久化 |
| AND-005 | Room + Keystore 密钥 | 安全 |
| AND-006 | 全局 UI 反馈规范 | 统一 |
| AND-007 | 日志脱敏 | 无密钥 |
| AND-008 | 关于页 + 开源声明 + **tmux 免责** | 可见 |
| AND-009 | `DesktopPushPort` NoOp 实现 | 可注入、不崩溃 |
| AND-010 | `CommandPalettePort` NoOp 实现 | 可注入、不崩溃 |

*注：原 AND-010 起编号与 v0.3 SSH 段冲突，自 9.2 起 SSH 从 AND-020 编号。*

---

### 9.2 阶段 B — SSH / 主机 / 控制面

| ID | 任务 | 验收 |
|----|------|------|
| AND-020 | NDK libssh2/libssh | 公钥连接成功 |
| AND-021 | 密码 / 私钥 / 口令短语 | 三种成功 |
| AND-022 | known_hosts 确认 UI | 可拒绝 |
| AND-023 | ConnectionManager 状态机 + keepalive + 重连 | 断网可恢复或明确失败 |
| AND-024 | 连接池多 channel | 并行 list/探测 |
| AND-025 | Host CRUD | 完成 |
| AND-026 | 导入/粘贴私钥 | 完成 |
| AND-027 | list-sessions → 列表 | 正确 |
| AND-028 | list-windows/panes | 详情可用 |
| AND-029 | 探测 tmux / mosh-server / OS / 包管理器 | 徽章 |
| AND-030 | 延迟与状态点 | UI |
| AND-031 | 无 tmux 空态 → 交互安装 | 跳转正确 |

---

### 9.3 阶段 C — 终端 / 快照 / 键盘

| ID | 任务 | 验收 |
|----|------|------|
| AND-040 | VT 渲染内核 | 中文/真彩/TUI |
| AND-041 | SSH PTY | 交互可用 |
| AND-042 | resize | 旋转正确 |
| AND-043 | scrollback 手势 | 流畅 |
| AND-044 | capture-pane 快照缓存 | 列表卡片 |
| AND-045 | 先快照后 PTY | 体感秒开 |
| AND-046 | Termius 快捷横条 | 常显 |
| AND-047 | 扩展更多快捷键 | 展开收起 + IME |
| AND-048 | 系统 IME 共存 | 第三方输入法 |
| AND-049 | Ctrl 组合态 | ^C 等 |
| AND-050 | 三套键盘方案 | 可切换 |
| AND-051 | 键盘编辑器 | 持久化 |
| AND-052 | 键盘 JSON 导入导出 | 往返 |
| AND-053 | 终端菜单（断开/字体/补全开关） | 可用 |
| AND-054 | 进入 = attach / new-session -A | 正确 |

---

### 9.4 阶段 D — Mosh

| ID | 任务 | 验收 |
|----|------|------|
| AND-060 | Mosh 客户端集成 | 连 mosh-server |
| AND-061 | 启动/握手流程 | 标准 |
| AND-062 | AUTO 优先 Mosh | 可强制 SSH |
| AND-063 | 弱网同步 UI | 提示清晰 |
| AND-064 | 控制面仍 SSH | 无冲突 |
| AND-065 | 失败降级 SSH | 提示 |

---

### 9.5 阶段 E — 交互式安装（仅 B + 可选 C）

| ID | 任务 | 验收 |
|----|------|------|
| AND-070 | 探测驱动安装向导 | 缺啥显示啥 |
| AND-071 | 生成发行版预填命令 | 模板正确 |
| AND-072 | 打开预填 PTY 安装会话 | 用户可 sudo 交互 |
| AND-073 | 复制命令到剪贴板 | 可选按钮 |
| AND-074 | 安装后自动复测 | 徽章更新 |
| AND-075 | 未知发行版兜底文案 | 不崩溃 |
| — | ~~一键代装 / sudo 代填~~ | **不做（Backlog）** |

---

### 9.6 阶段 F — 命令补全 + 片段（完整）

| ID | 任务 | 验收 |
|----|------|------|
| AND-080 | Suggestion 模型与 UI 列表组件 | 不挡关键操作 |
| AND-081 | 输入防抖触发 suggest | 性能可接受 |
| AND-082 | SnippetRepository CRUD UI | 可管片段 |
| AND-083 | 片段参与补全匹配 | 按名/内容 |
| AND-084 | 插入 vs 插入并执行 | 可配 |
| AND-085 | 片段变量替换 | host/user 等 |
| AND-086 | CommandHistory 记录与补全 | 按 Host LRU |
| AND-087 | 内置命令词表 JSON | 可扩展 |
| AND-088 | 路径补全（防抖 SSH compgen/ls） | 可选开；失败静默 |
| AND-089 | TUI 场景暂停补全 + 手动开关 | 不干扰 Claude 等 |
| AND-090 | 补全总开关与设置项 | 持久化 |
| AND-091 | 片段随加密配置导出/导入 | 往返 |

---

### 9.7 阶段 G — 会话体验打磨

| ID | 任务 | 验收 |
|----|------|------|
| AND-100 | 卡片：进入 / 更多 | 符合中枢模型 |
| AND-101 | 杀会话、重命名、刷新快照 | 代执行 |
| AND-102 | 新建会话 + 名 | 成功 |
| AND-103 | 编码模板 claude/codex/grok/自定义 | 可编辑 |
| AND-104 | 多 Host 分组 / 最近 | 两种视图 |
| AND-105 | 下拉刷新 | 正确 |
| AND-106 | 启动直达最近会话（可关） | 正确 |
| AND-107 | 加密导出/导入 Hosts+片段+键盘 | 迁移 |
| AND-108 | 可选生物识别锁 App | 可关 |
| AND-109 | 复制等价 ssh/tmux 命令 | 调试友好 |
| — | ~~命令面板完整 UI~~ | **仅 Port NoOp** |
| — | ~~推送到电脑完整~~ | **仅 Port NoOp** |

---

### 9.8 阶段 H — 视觉打磨

| ID | 任务 | 验收 |
|----|------|------|
| AND-120 | Sequoia 商店级 | 截图 |
| AND-121 | Big Sur 完整 | 截图 |
| AND-122 | 4 个性化无回归 | 切换稳定 |
| AND-123 | 转场 60fps | 中端机 |
| AND-124 | 空状态与 onboarding | 首启友好 |
| AND-125 | 终端字体打包与授权 | 合规 |

---

### 9.9 阶段 I — 质量与发布

| ID | 任务 | 验收 |
|----|------|------|
| AND-130 | 连接状态机单测 | 覆盖重连 |
| AND-131 | 补全/片段/键盘 schema 测 | 通过 |
| AND-132 | 真机：SSH / Mosh / 无 tmux / 错密 | 清单绿 |
| AND-133 | TUI：tmux/htop/claude/codex/vim | 抽样过 |
| AND-134 | 补全在 shell vs TUI 行为 | 符合 §6.5 |
| AND-135 | 性能指标 | 达预算 |
| AND-136 | 耗电：默认不后台保活 | 可接受 |
| AND-137 | R8 签名 AAB/APK | 可分发 |
| AND-138 | 隐私说明（密钥、无 sudo 代填） | 文案 |
| AND-139 | 飞书专群发版 | **仅目标群** |

---

### 9.10 Backlog（明确不做进第一版）

| ID | 项 |
|----|-----|
| BL-001 | 桌面 Companion 全实现 + 配对 + 通知开终端 |
| BL-002 | 一键代装 + sudo 密码框 |
| BL-003 | 命令面板完整 UI |
| BL-004 | 手机主屏捷径 |
| BL-005 | iOS 客户端 |
| BL-006 | 补全密码建议 / 多主机广播片段 |
| BL-007 | 远端 deep shell 集成（完整路径/参数描述） |

---

### 9.11 数量与排期粗估

| 类别 | 约 |
|------|-----|
| DOC | 8 |
| AND 实现 | ~90 |
| Backlog | 7 |
| 单人全职粗估 | **约 3 个月量级**（视 Mosh/终端难度） |

---

## 10. 信息架构（本版）

```
Tab 会话     ← 中枢
Tab 主机     ← CRUD、探测、交互安装、SSH/Mosh 偏好
Tab 设置     ← 主题、键盘、补全/片段、连接、关于
             （电脑推送：隐藏或「即将推出」）
```

---

## 11. 已确认（2026-07-16）

| 项 | 结论 |
|----|------|
| 展示名 | **m-tmux** |
| 包名 | **app.mtmux.android** |
| 电脑入口 | **完全隐藏**（仅接口） |
| 开工 | **按 v0.3.1 全量 TODO 一项接一项** |

跟踪文件：`docs/TODO-TRACKER.md` · 代码：`android/`

---

*接口预留 ≠ 半残功能：用户不可见，避免差评。*
