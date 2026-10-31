package app.mtmux.desktoppush

/**
 * Reserved API for "push session to headed desktop" (plan D).
 * v0.3.1 ships [NoOpDesktopPush] only — no UI entry that pretends it works.
 */
interface DesktopPushPort {
    /** Feature flag for UI; false until Companion is implemented. */
    fun isFeatureEnabled(): Boolean = false

    suspend fun listPairedDesktops(): List<PairedDesktop>

    suspend fun pushSession(request: PushSessionRequest): PushResult
}

data class PairedDesktop(
    val id: String,
    val name: String,
    val online: Boolean,
)

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

class NoOpDesktopPush : DesktopPushPort {
    override fun isFeatureEnabled(): Boolean = false

    override suspend fun listPairedDesktops(): List<PairedDesktop> = emptyList()

    override suspend fun pushSession(request: PushSessionRequest): PushResult =
        PushResult.NotImplemented
}
