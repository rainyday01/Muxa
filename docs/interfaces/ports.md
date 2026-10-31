# 预留接口说明（DOC-008）

实现可替换；第一版使用 NoOp，UI 不暴露半残能力。

## DesktopPushPort

```kotlin
interface DesktopPushPort {
    fun isFeatureEnabled(): Boolean = false
    suspend fun listPairedDesktops(): List<PairedDesktop>
    suspend fun pushSession(request: PushSessionRequest): PushResult
}

data class PairedDesktop(val id: String, val name: String, val online: Boolean)
data class PushSessionRequest(
    val hostId: String,
    val sessionName: String,
    val desktopId: String? = null,
)
sealed class PushResult {
    data object NotImplemented : PushResult()
    data object Sent : PushResult()
    data class Failed(val reason: String) : PushResult()
}
```

- UI：默认隐藏「推送到电脑」；设置中不展示配对页。  
- 后续：替换为局域网/Companion 实现，无需改业务调用方签名。

## CommandPalettePort

```kotlin
interface CommandPalettePort {
    fun isFeatureEnabled(): Boolean = false
    fun actions(): List<PaletteAction> = emptyList()
}

data class PaletteAction(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val keywords: List<String> = emptyList(),
)
```

- 第一版不提供 ⌘K UI。  
- 会话操作全部走列表卡片 / 更多菜单。
