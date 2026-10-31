# Muxa iOS P1 关键技术探针报告

> **状态修订（2026-08-01）：** 本文记录的是 2026-07-29 的历史探针结果；对当前用户 VPS 的重新验证显示 SSH password/PTY/TUI 通过，但 Mosh UDP round-trip 超时。当前 transport 已增加首个远端响应 startup timeout，并由 AUTO fallback 回归测试覆盖；这不解除外部 UDP 阻塞。当前外部验证以 `docs/ios-live-e2e-report.md` 为准，不能据本文历史结论将 IOS-016/IOS-066/IOS-096 标记为完成。


日期：2026-07-29
范围：实施清单 `IOS-010` 至 `IOS-018`
执行环境：本机 Xcode 26.4、iOS 26.5 iPhone 17 Pro Simulator、用户提供的 Ubuntu 24.04 VPS

## 1. 结论

P1 关键组件全部达到继续产品开发的门槛，不触发依赖替换：

- 终端保留 SwiftTerm 1.15.0。
- SSH 保留 libssh2 1.11.1 + OpenSSL 3.5.7，并使用 Muxa 的薄 Swift adapter。
- Mosh 保留 swift-mosh 0.1.0；真实 mosh-server、弱网、freeze/resume 与 SSH 降级均已通过。
- Android v1 配置归档使用 Apple CommonCrypto + CryptoKit 实现，不增加第三方加密依赖。

所有依赖继续使用精确版本。Swift Package 版本由 `Package.resolved` 固定，原生库版本、来源和
SHA-256 由 `ios/Vendor/Native/Provenance.json` 固定。

## 2. 验证结果

| 探针 | 结果 | 证据与覆盖 |
|---|---|---|
| SwiftTerm | 通过 | Simulator 嵌入、输入、resize、scrollback、TrueColor、Unicode/CJK/Emoji fixture |
| libssh2 原生构建 | 通过 | iOS arm64 与 Simulator arm64 XCFramework，本机重复构建与链接 |
| SSH 基础链路 | 通过 | 真实 VPS TCP、握手、exec、PTY、window-change、close |
| SSH 认证矩阵 | 通过 | Password；RSA/Ed25519/ECDSA；OpenSSH/PKCS8/PEM；无口令/有口令 |
| SSH 负向认证 | 通过 | 错误 password 与错误 passphrase 均在认证阶段拒绝 |
| Host key | 通过 | 首次确认后连接；SHA-256 mismatch 在认证前硬阻断 |
| SSH 高 RTT 稳定性 | 通过 | channel 改为 nonblocking + EAGAIN/socket direction/deadline，stdout/stderr 同步抽干 |
| swift-mosh 协议 | 通过 | bootstrap noisy fixture、非法 key、session snapshot/restore/stop 单测 |
| 标准 mosh-server | 通过 | Ubuntu mosh-server 1.4.0 加密 UDP 回显；上游 macOS E2E 与 iPhone Simulator App E2E |
| Mosh 弱网恢复 | 通过 | 指定 UDP 端口 15% 丢包、250ms 延迟；App freeze 4 秒后恢复并完成第二轮回显 |
| Mosh 降级 | 通过 | 无服务 UDP 端口触发 typed failure；同 App 进程随后完成 SSH exec/PTY 全链路 |
| Android v1 归档 | 通过 | PBKDF2-SHA256 120,000 + AES-256-GCM；Android/iOS 双向黄金向量字节一致 |

## 3. 关键修正

### SSH channel 生命周期

探针发现 exec channel 在成功路径被重复 close，导致下一 channel 偶发阻塞。现已收敛为一次
`wait_eof -> close -> wait_closed -> free`。进一步测试发现 libssh2 blocking 模式在 Simulator
高 RTT 场景不能稳定遵守超时，因此 channel I/O 改为 nonblocking：

- 所有 channel 操作显式处理 `LIBSSH2_ERROR_EAGAIN`。
- 根据 `libssh2_session_block_directions` 等待 socket。
- 单次 poll 最多 250ms，操作使用绝对 deadline，不能无限 loading。
- stdout/stderr 在同一循环抽干，避免单流大量输出造成互锁。
- 清理操作使用 500ms 有界 deadline。

### 测试机配置

VPS 已安装 Ubuntu 官方 `mosh 1.4.0`，UFW 开放标准 UDP `60000:61000`。弱网测试使用的临时
`tc netem` 规则和临时 mosh-server 均在用例结束后移除。测试账号与一次性凭据不进入仓库。

## 4. 依赖决策

| 模块 | 决策 | 理由 | 后续门槛 |
|---|---|---|---|
| SwiftTerm 1.15.0 | 接受 | MIT、UIKit 原生、功能 fixture 通过 | P5 验证 IME、TUI 与大输出性能 |
| libssh2 1.11.1 | 接受 | BSD-3、认证格式覆盖完整、真实 VPS 通过 | P3 完成连接池、错误映射与多 channel 压测 |
| OpenSSL 3.5.7 | 接受 | libssh2 成熟 crypto backend、双平台静态链接通过 | 发布前复核 CVE 与 provenance |
| swift-mosh 0.1.0 | 有条件接受 | MIT、协议单测完整、标准 server 与恢复场景通过 | P6 保留替换边界并完成网络切换/真机门槛 |
| CommonCrypto + CryptoKit | 接受 | Apple 系统 API、与 Android 黄金向量一致 | P8 接入 UI 时保证密码与明文不写日志 |

swift-mosh 的生态历史仍短于 Blink/官方 Mosh，因此维持 `InteractiveTransport` 隔离边界；当前没有
证据支持承担 GPL 集成成本切换到 Blink/libmoshios，也不允许自研协议替代。

## 5. 质量门禁

P1 关闭前执行本机 `make verify`，包含 Simulator build、全部 Unit/UI tests、SwiftLint、
SwiftFormat、敏感信息扫描和许可证清单。GitHub Actions 未用于任何构建或测试。
