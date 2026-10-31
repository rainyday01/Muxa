package app.mtmux.android.di

import android.content.Context
import androidx.room.Room
import app.mtmux.autocomplete.AutocompleteEngine
import app.mtmux.commandpalette.CommandPalettePort
import app.mtmux.commandpalette.NoOpCommandPalette
import app.mtmux.core.data.db.MIGRATION_2_3
import app.mtmux.core.data.db.MtmuxDatabase
import app.mtmux.core.data.keyboard.KeyboardRepository
import app.mtmux.core.data.prefs.UserPreferences
import app.mtmux.core.data.repo.HostRepository
import app.mtmux.core.data.security.SecretStore
import app.mtmux.desktoppush.DesktopPushPort
import app.mtmux.desktoppush.NoOpDesktopPush
import app.mtmux.ssh.ConnectionManager
import app.mtmux.ssh.KnownHostsStore
import app.mtmux.ssh.SshjEngine
import app.mtmux.ssh.TmuxControl
import java.io.File

/**
 * Manual DI (Hilt can replace later). Wires Phase A modules + reserved ports.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val db: MtmuxDatabase = Room.databaseBuilder(
        appContext,
        MtmuxDatabase::class.java,
        "mtmux.db",
    ).addMigrations(MIGRATION_2_3)
        .fallbackToDestructiveMigration()
        .build()

    val secrets = SecretStore(appContext)
    val prefs = UserPreferences(appContext)
    val keyboards = KeyboardRepository(appContext)
    val hosts = HostRepository(db.hosts(), secrets)
    val knownHosts = KnownHostsStore(File(appContext.filesDir, "known_hosts_mtmux"))
    val sshEngine = SshjEngine(knownHosts, appContext)
    val connections = ConnectionManager(engine = sshEngine, appContext = appContext)
    val tmux = TmuxControl(connections)
    val autocomplete = AutocompleteEngine(db.snippets(), db.history())
    val desktopPush: DesktopPushPort = NoOpDesktopPush()
    val commandPalette: CommandPalettePort = NoOpCommandPalette()
}
