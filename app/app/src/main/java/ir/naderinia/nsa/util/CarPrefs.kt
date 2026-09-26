package ir.naderinia.nsa.util

import android.content.Context

object CarPrefs {
    private const val PREFS_NAME = "car_prefs"
    private const val KEY_CURRENT_KM = "current_km"

    fun getCurrentKm(context: Context): Long =
        SecurePrefs.get(context, PREFS_NAME).getLong(KEY_CURRENT_KM, 0)

    fun setCurrentKm(context: Context, km: Long) {
        SecurePrefs.get(context, PREFS_NAME)
            .edit().putLong(KEY_CURRENT_KM, km).apply()
    }
}
