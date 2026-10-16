package ir.naderinia.nsa.util

import android.content.Context
import android.os.PowerManager
import android.view.accessibility.AccessibilityManager

/**
 * تنظیمات «حالت صوتی» برای نابینایان و کم‌بینایان.
 *
 *  • حالت صوتی: برنامه نتیجه‌ی کارها («ثبت شد»، «انجام شد»…) رو با صدا می‌گه.
 *    وقتی TalkBack روشنه لازم نیست (خود TalkBack می‌خونه) و برنامه دوبار نمی‌خونه.
 *  • خواندن اعلان: وقتی وقت یادآوری می‌شه، متنش با صدا خونده می‌شه
 *    (حتی وقتی صفحه خاموشه و گوشی تو جیب یا روی میزه).
 */
object AccessibilityPrefs {

    private const val PREFS_NAME = "access_prefs"
    private const val KEY_VOICE_MODE = "voice_mode"
    private const val KEY_SPEAK_NOTIFICATIONS = "speak_notifications"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isVoiceMode(context: Context): Boolean = prefs(context).getBoolean(KEY_VOICE_MODE, false)

    fun setVoiceMode(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_VOICE_MODE, on).apply()
    }

    fun speaksNotifications(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SPEAK_NOTIFICATIONS, false)

    fun setSpeaksNotifications(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_SPEAK_NOTIFICATIONS, on).apply()
    }

    /** آیا TalkBack (یا هر صفحه‌خوان لمسی دیگه) روشنه؟ */
    fun isTalkBackOn(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        return am != null && am.isEnabled && am.isTouchExplorationEnabled
    }

    /** برنامه خودش باید نتیجه‌ی کارها رو بخونه؟ (حالت صوتی روشن و TalkBack خاموش) */
    fun appShouldSpeak(context: Context): Boolean = isVoiceMode(context) && !isTalkBackOn(context)

    /**
     * موقع اعلان: اگه TalkBack روشنه و صفحه هم روشنه، خود TalkBack اعلان رو می‌خونه؛
     * پس ما نمی‌خونیم تا دوبار شنیده نشه.
     */
    fun shouldSpeakNotification(context: Context): Boolean {
        if (!speaksNotifications(context)) return false
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val screenOn = pm?.isInteractive == true
        return !(isTalkBackOn(context) && screenOn)
    }
}
