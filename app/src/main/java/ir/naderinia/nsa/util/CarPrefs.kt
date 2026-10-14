package ir.naderinia.nsa.util

import android.content.Context
import kotlin.math.ceil

object CarPrefs {
    private const val PREFS_NAME = "car_prefs"
    private const val KEY_CURRENT_KM = "current_km"
    private const val KEY_HISTORY = "km_history" // "millis:km;millis:km;..."

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    private const val MAX_HISTORY = 60

    fun getCurrentKm(context: Context): Long =
        SecurePrefs.get(context, PREFS_NAME).getLong(KEY_CURRENT_KM, 0)

    fun setCurrentKm(context: Context, km: Long) {
        // 0 means "never entered": storing it would create a bogus first reading and wreck the
        // km-per-day average (and so the service-date estimate).
        if (km <= 0L) return
        val prefs = SecurePrefs.get(context, PREFS_NAME)
        val history = readHistory(context).toMutableList()

        // Remember each *new* reading with its date; that's what the daily average comes from.
        if (history.lastOrNull()?.second != km) {
            history.add(System.currentTimeMillis() to km)
        }

        prefs.edit()
            .putLong(KEY_CURRENT_KM, km)
            .putString(KEY_HISTORY, history.takeLast(MAX_HISTORY).joinToString(";") { "${it.first}:${it.second}" })
            .apply()
    }

    private fun readHistory(context: Context): List<Pair<Long, Long>> =
        SecurePrefs.get(context, PREFS_NAME)
            .getString(KEY_HISTORY, null)
            ?.split(";")
            ?.mapNotNull { entry ->
                val parts = entry.split(":")
                val at = parts.getOrNull(0)?.toLongOrNull()
                val km = parts.getOrNull(1)?.toLongOrNull()
                if (at != null && km != null) at to km else null
            }
            ?: emptyList()

    /**
     * Average km driven per day over the last ~90 days of readings.
     * Null until there are at least two readings, 3+ days apart.
     */
    fun averageKmPerDay(context: Context): Double? {
        val cutoff = System.currentTimeMillis() - 90 * DAY_MILLIS
        val recent = readHistory(context).filter { it.first >= cutoff }
        if (recent.size < 2) return null

        val first = recent.first()
        val last = recent.last()
        val days = (last.first - first.first).toDouble() / DAY_MILLIS
        if (days < 3) return null

        return ((last.second - first.second) / days).takeIf { it > 0 }
    }

    /**
     * Days from now until the odometer is expected to reach [targetKm]
     * (0 or negative = already there). Null when there's not enough history.
     * Counted from the date of the last reading, so a stale reading doesn't
     * make the estimate look further away than it really is.
     */
    fun estimateDaysUntil(context: Context, targetKm: Long): Long? {
        val perDay = averageKmPerDay(context) ?: return null
        val last = readHistory(context).lastOrNull() ?: return null

        val remainingKm = targetKm - last.second
        if (remainingKm <= 0) return 0

        val reachAt = last.first + (ceil(remainingKm / perDay) * DAY_MILLIS).toLong()
        return ((reachAt - System.currentTimeMillis()) / DAY_MILLIS).coerceAtLeast(0)
    }
}
