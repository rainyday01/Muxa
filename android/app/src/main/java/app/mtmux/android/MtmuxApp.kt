package app.mtmux.android

import android.app.Application
import app.mtmux.android.di.AppContainer
import app.mtmux.core.data.diag.DiagLog
import app.mtmux.core.data.diag.LogHubClient
import app.mtmux.ssh.SshCryptoBootstrap

class MtmuxApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        DiagLog.init(this)
        LogHubClient.uploadToken = BuildConfig.LOGHUB_UPLOAD_TOKEN
        // Must run before any SSHClient: replace Android's stripped BC provider.
        SshCryptoBootstrap.ensure()
        container = AppContainer(this)
        DiagLog.i("boot", "AppContainer ready")
    }
}
