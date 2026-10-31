package app.mtmux.core.model.locale

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import app.mtmux.core.model.AppLanguage
import java.util.Locale

/**
 * In-app language: SharedPreferences + [wrap] for attachBaseContext and
 * getString() from non-Activity code (SSH errors, keyboard repo, …).
 */
object AppLocale {
    private const val PREFS = "mtmux_locale"
    private const val KEY = "app_language"

    fun read(ctx: Context): AppLanguage {
        val raw = ctx.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
        return AppLanguage.fromPref(raw)
    }

    fun write(ctx: Context, language: AppLanguage) {
        ctx.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, language.prefValue)
            .apply()
    }

    fun wrap(base: Context, language: AppLanguage = read(base)): Context {
        val locale = localeOf(language) ?: return base
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }

    /** Locale-aware [Context.getString] (works from Application context). */
    fun getString(ctx: Context, id: Int, vararg args: Any): String {
        val c = wrap(ctx)
        return if (args.isEmpty()) c.getString(id) else c.getString(id, *args)
    }

    private fun localeOf(language: AppLanguage): Locale? = when (language) {
        AppLanguage.SYSTEM -> null
        AppLanguage.CHINESE -> Locale.SIMPLIFIED_CHINESE
        AppLanguage.ENGLISH -> Locale.ENGLISH
    }
}
