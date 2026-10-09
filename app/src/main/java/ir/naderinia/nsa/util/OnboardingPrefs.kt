package ir.naderinia.nsa.util

import android.content.Context

/**
 * Remembers that the person already went through the permission screen, so a
 * skipped *optional* permission (battery exemption) doesn't bring the screen
 * back on every single launch. Missing *required* permissions still do.
 */
object OnboardingPrefs {
    private const val PREFS_NAME = "onboarding_prefs"
    private const val KEY_SEEN = "permission_onboarding_seen"

    fun isSeen(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_SEEN, false)

    fun setSeen(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SEEN, true).apply()
    }
}
