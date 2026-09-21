package ir.naderinia.nsa.util

import android.content.Context

object CarPrefs {
    private const val PREFS_NAME = "car_prefs"
    private const val KEY_CURRENT_KM = "current_km"

    fun getCurrentKm(context: Context): Long =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong(KEY_CURRENT_KM, 0)

    fun setCurrentKm(context: Context, km: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_CURRENT_KM, km).apply()
    }
}
