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

    /** Channel of «حیاتی» reminders: alarm-style sound, strong vibration, asks to bypass Do Not Disturb. */
    const val CRITICAL_CHANNEL_ID = "reminders_critical_v1"

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

    fun ensureCriticalChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CRITICAL_CHANNEL_ID) != null) return

        val soundUri = currentSoundUri(context)
            ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
            ?: Settings.System.DEFAULT_NOTIFICATION_URI
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(CRITICAL_CHANNEL_ID, "یادآوری‌های حیاتی", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "یادآوری‌های حیاتی؛ تا تأیید نکنی چند بار تکرار می‌شن"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 900)
            enableLights(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setSound(soundUri, audioAttributes)
            // Only takes effect if the person allows it for this channel in system settings (see openCriticalChannelSettings).
            setBypassDnd(true)
        }
        manager.createNotificationChannel(channel)
    }

    /** System page of the critical channel, where the person can allow «override Do Not Disturb». */
    fun criticalChannelSettingsIntent(context: Context): android.content.Intent {
        ensureCriticalChannel(context)
        return android.content.Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, CRITICAL_CHANNEL_ID)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Call after the person picks a new ringtone; creates a fresh channel using it. */
    fun setCustomSound(context: Context, uri: Uri?) {
        val p = prefs(context)
        p.edit()
            .putString(KEY_SOUND_URI, uri?.toString())
            .putInt(KEY_CHANNEL_VERSION, p.getInt(KEY_CHANNEL_VERSION, 0) + 1)
            .apply()
        ensureChannel(context)
        // The critical channel takes the same sound: drop it so it is rebuilt with the new one next time.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).deleteNotificationChannel(CRITICAL_CHANNEL_ID)
        }
    }
}
