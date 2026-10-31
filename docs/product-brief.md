# m-tmux — 产品方案摘要 v0.3.1

> **权威执行文档：** [`android-full-plan-v0.3.1.md`](./android-full-plan-v0.3.1.md)  
> **命名分析：** [`naming-and-brand.md`](./naming-and-brand.md)  
> 工作名倾向 **m-tmux**（移动端 tmux 客户端）。历史名 Pane 仅作仓库/文档遗留时可并存。

---

## 一句话

Android 上的远程 **tmux 会话启动器 + SSH/Mosh 终端**：会话列表、快照秒进、Termius 级键盘与**命令补全/片段**；无账号。电脑推送与命令面板 **只留接口**。

---

## v0.3.1 锁定

| 项 | 结论 |
|----|------|
| 策略 | 不做 MVP；Android 全功能 → 再 iOS |
| 名 | 倾向 **m-tmux**（风险与备选见命名文） |
| 电脑推送 | **接口预留**，Companion 后置 |
| 安装 | **交互式预填 PTY**（+ 可选复制命令）；无一键代装 |
| 命令面板 | **接口预留** |
| 命令补全 | **完整做**（片段 + 历史 + 内置词表 + 可选路径） |
| 键盘 | Termius 横条 + 扩展 + 系统 IME |
| 会话 | 列表即中枢 |
| 传输 | SSH + Mosh；探测 tmux/mosh |

---

## 命名一句话

- **m-tmux**：语义好；注意 **非官方** 免责，避免商标/混淆风险。  
- 更稳品牌向：**Attach** / **Remux** + 副标题 *Mobile tmux client*。

---

## 模块示意

```
会话 | 主机 | 设置
  + autocomplete（片段/历史）
  + desktop-push-api（NoOp）
  + command-palette-api（NoOp）
```

全量 TODO 见 `android-full-plan-v0.3.1.md` §9。
