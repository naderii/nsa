package ir.naderinia.nsa.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings
import ir.naderinia.nsa.util.SecurePrefs

/**
 * Android only lets you set a NotificationChannel's sound at creation time —
 * once created, a channel's sound can't be changed in code (only the user
 * can change it from system Settings). So picking a new sound here creates
 * a NEW channel with a version-bumped id instead of trying to mutate the
 * old one; the old channel is simply abandoned (harmless, costs nothing).
 */
object NotificationHelper {

    private const val PREFS_NAME = "notification_prefs"
    private const val KEY_SOUND_URI = "sound_uri"
    private const val KEY_CHANNEL_VERSION = "channel_version"

    private fun prefs(context: Context) =
        SecurePrefs.get(context, PREFS_NAME)

    fun currentChannelId(context: Context): String {
        val version = prefs(context).getInt(KEY_CHANNEL_VERSION, 0)
        return "reminders_channel_v$version"
    }

    fun currentSoundUri(context: Context): Uri? =
        prefs(context).getString(KEY_SOUND_URI, null)?.let { Uri.parse(it) }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channelId = currentChannelId(context)
        if (manager.getNotificationChannel(channelId) != null) return

        val soundUri = currentSoundUri(context) ?: Settings.System.DEFAULT_NOTIFICATION_URI
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(channelId, "یادآوری‌ها", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "یادآوری‌های زمان‌بندی‌شده نرم‌افزار NSA"
            enableVibration(true)
            setSound(soundUri, audioAttributes)
        }
        manager.createNotificationChannel(channel)
    }

    /** Call after the person picks a new ringtone; creates a fresh channel using it. */
    fun setCustomSound(context: Context, uri: Uri?) {
        val p = prefs(context)
        p.edit()
            .putString(KEY_SOUND_URI, uri?.toString())
            .putInt(KEY_CHANNEL_VERSION, p.getInt(KEY_CHANNEL_VERSION, 0) + 1)
            .apply()
        ensureChannel(context)
    }
}
