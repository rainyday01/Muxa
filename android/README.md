# m-tmux Android

包名：`app.mtmux.android`  
最低 API 26 · Compose · 多模块

## 模块

| 模块 | 职责 |
|------|------|
| app | 壳、导航、DI |
| core/model, ui, data | 模型、主题、Room/密钥 |
| ssh | ConnectionManager + TmuxControl（Phase B 接真引擎） |
| mosh | Mosh 客户端（UDP） |
| termux-emulator / termux-view | 终端 VT + 绘制（Termux vendoring） |
| autocomplete | 补全引擎 |
| desktop-push-api | 推送接口 NoOp |
| command-palette-api | 命令面板 NoOp |
| feature/* | 会话/主机/设置/终端 UI |

## 构建

需要：JDK 17+、Android SDK 35、接受 licenses。

```bash
export JAVA_HOME=...   # JDK 17
export ANDROID_HOME=...
cd android
# 首次：安装 wrapper
gradle wrapper --gradle-version 8.7   # 或使用 Android Studio 打开
./gradlew :app:assembleDebug
```

`local.properties`：

```
sdk.dir=/path/to/Android/Sdk
```

## 构建产物

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/Android/Sdk
cd android
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## 当前能力（已进 debug APK）

- 三 Tab + 主题体系 + 会话列表  
- **sshj 真 SSH**（密码/私钥/口令）+ **known_hosts 确认对话框**  
- 主机 CRUD + 私钥粘贴  
- **PTY attach tmux** + **Termux VT/View 渲染**（主路径；自研 VtBuffer 已移除）  
- Termius 键条 + Ctrl 锁定 + **系统 IME 输入**  
- 补全 / 片段 / 安装向导 / Mosh  
- 推送/命令面板 NoOp（入口隐藏）  

## 下一项

见 `../docs/TODO-TRACKER.md`。
