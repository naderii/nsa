package ir.naderinia.nsa.util

import android.content.Context

object ChangelogPrefs {
    private const val PREFS_NAME = "changelog_prefs"
    private const val KEY_LAST_SEEN_VERSION = "last_seen_version"

    fun getLastSeenVersion(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_LAST_SEEN_VERSION, 0)

    fun setLastSeenVersion(context: Context, versionCode: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_LAST_SEEN_VERSION, versionCode).apply()
    }
}
