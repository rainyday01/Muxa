# m-tmux SSH 算法与密钥兼容性

> Android 上 sshj + 系统密码学库的常见坑与当前处理策略。

## 问题背景

| 现象 | 根因 |
|------|------|
| `no such algorithm: X25519 for provider BC` | Android 自带**残缺** BouncyCastle，没有 X25519 |
| Ed25519 私钥加载失败 | 需完整 BC + `net.i2p.crypto:eddsa` |
| 握手成功但认证失败 | 服务器禁用了客户端密钥类型（如只收 `rsa-sha2-*`） |

**与 Mosh / 未实现模块无关**——发生在 SSH 握手与认证阶段。

## 启动时处理

`SshCryptoBootstrap.ensure()`（`MtmuxApp` + 每次 connect）：

1. 移除系统 `BC` 提供者  
2. 插入 `bcprov-jdk18on` 完整 BC  
3. 探测设备能力：X25519、ECDH、Ed25519、RSA/ECDSA/DSA、AES-GCM/CTR、ChaCha20-Poly1305、HMAC-SHA2  

`MtmuxSshConfig` 根据探测结果 **过滤** 不可用的 KEX / host-key / cipher 工厂，避免中途崩溃。

## 支持矩阵（目标）

### 密钥交换 (KEX)

| 算法 | 策略 |
|------|------|
| `curve25519-sha256` / `libssh.org` | 优先；X25519 不可用则自动剔除并重试 |
| `ecdh-sha2-nistp256/384/521` | 有 ECDH 时启用 |
| `diffie-hellman-group-exchange-sha256` | 保留 |
| `diffie-hellman-group14/15/16/18-sha*` | 保留（兼容老服务器） |
| `diffie-hellman-group1-sha1` | 仅作最后兼容（弱） |

### 客户端私钥 / 主机密钥类型

| 类型 | 支持 | 说明 |
|------|------|------|
| Ed25519 | ✅ | OpenSSH v1 PEM + eddsa 库 |
| RSA（含 rsa-sha2-256/512） | ✅ | PEM / PKCS8 / OpenSSH v1 |
| ECDSA nistp256/384/521 | ✅ | |
| DSA (ssh-dss) | ⚠️ | 可加载但许多服务器已禁用 |
| FIDO/sk-*、PKCS#11 | ❌ | 未做 |
| 证书登录 (*-cert-v01) | 部分 | sshj 有 factory，未做专门 UI |

### 私钥文件格式

| 格式 | 支持 |
|------|------|
| `BEGIN OPENSSH PRIVATE KEY` | ✅ |
| `BEGIN PRIVATE KEY` / `ENCRYPTED PRIVATE KEY` (PKCS#8) | ✅ |
| `BEGIN RSA/EC/DSA PRIVATE KEY` | ✅ |
| PuTTY `.ppk` 文本 | ✅（sshj PuTTYKeyFile） |
| 加密私钥 + passphrase | ✅ |

### 加密与 MAC

| 类型 | 策略 |
|------|------|
| `aes*-ctr` / `aes*-cbc` | 优先保留 |
| `aes*-gcm@openssh.com` | 设备支持则保留 |
| `chacha20-poly1305@openssh.com` | 支持则保留；失败可剔除重试 |
| 老式 blowfish/3des 等 | 仅当没有 AES 时才可能留下 |

## 自动重试

连接失败时按错误信息重试一次：

1. 去掉 curve25519（X25519 问题）  
2. 去掉 chacha20（部分机型）  

仍失败则返回**中文友好错误** + 可选 crypto 诊断摘要（不含密钥）。

## 建议用户密钥

```bash
ssh-keygen -t ed25519 -C "phone-mtmux"
# 或
ssh-keygen -t rsa -b 4096 -C "phone-mtmux"
```

把 **私钥 PEM 全文**（含 BEGIN/END）粘贴进 App；加密密钥填口令短语。

## 验证清单（真机）

- [ ] 密码登录  
- [ ] RSA 私钥（无口令 / 有口令）  
- [ ] Ed25519 OpenSSH 私钥  
- [ ] ECDSA 私钥  
- [ ] 环境检查 `tmux` / `mosh-server`  
- [ ] 首次 host key 确认  
