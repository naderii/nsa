package ir.naderinia.nsa.util

import android.content.Context

/** App appearance: follow the phone's setting (default), or force light / dark. */
object ThemePrefs {
    const val SYSTEM = 0
    const val LIGHT = 1
    const val DARK = 2

    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_MODE = "mode"
    private const val KEY_CLASSIC = "classic_style"

    fun getMode(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_MODE, SYSTEM)

    fun setMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_MODE, mode).apply()
    }

    /** false = modern look (default); true = the classic look used up to 0.22.x. */
    fun isClassic(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_CLASSIC, false)

    fun setClassic(context: Context, classic: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_CLASSIC, classic).apply()
    }
}
