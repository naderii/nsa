package ir.naderinia.nsa.util

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * گوینده‌ی فارسی بر پایه‌ی موتور TextToSpeech خود اندروید (آفلاین و بدون مجوز).
 * از همون موتور و صدایی استفاده می‌کنه که کاربر در تنظیمات گوشی انتخاب کرده؛
 * پس صدای مورد علاقه‌ی کاربر (مثلاً صدای فارسیِ مخصوص TalkBack) همین‌جا هم کار می‌کنه.
 */
class Speaker(context: Context, private val onReady: ((persianOk: Boolean) -> Unit)? = null) {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var queued: Triple<String, (() -> Unit)?, Boolean>? = null

    /** بعد از آماده شدن موتور مشخص می‌شه؛ false یعنی صدای فارسی روی گوشی نصب نیست. */
    var persianAvailable: Boolean = false
        private set

    init {
        tts = TextToSpeech(appContext) { status ->
            main.post {
                if (status == TextToSpeech.SUCCESS) {
                    val engine = tts
                    if (engine != null) {
                        val result = engine.setLanguage(Locale("fa", "IR"))
                        persianAvailable = result != TextToSpeech.LANG_MISSING_DATA &&
                            result != TextToSpeech.LANG_NOT_SUPPORTED
                        engine.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        ready = true
                        onReady?.invoke(persianAvailable)
                        queued?.let { (t, done, flush) -> speak(t, flush, done) }
                        queued = null
                    }
                } else {
                    onReady?.invoke(false)
                    queued?.second?.invoke()
                    queued = null
                }
            }
        }
    }

    /** [flush] = true یعنی هر چی در حال خوندنه قطع بشه. */
    fun speak(text: String, flush: Boolean = true, onDone: (() -> Unit)? = null) {
        val engine = tts
        if (engine == null) { onDone?.invoke(); return }
        if (!ready) { queued = Triple(text, onDone, flush); return }
        val id = UUID.randomUUID().toString()
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { if (utteranceId == id) main.post { onDone?.invoke() } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { if (utteranceId == id) main.post { onDone?.invoke() } }
        })
        val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        if (engine.speak(text, mode, null, id) == TextToSpeech.ERROR) onDone?.invoke()
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    companion object {
        /**
         * یک‌بار خوندن و بستن؛ برای وقتی که از BroadcastReceiver (بدون صفحه) صدا می‌زنیم.
         * [onFinished] حتماً صدا زده می‌شه (حتی اگه موتور صدا خراب باشه یا دیر جواب بده).
         */
        fun speakOnce(context: Context, text: String, timeoutMs: Long = 12_000, onFinished: () -> Unit) {
            val handler = Handler(Looper.getMainLooper())
            var finished = false
            var speaker: Speaker? = null
            fun finish() {
                if (finished) return
                finished = true
                speaker?.shutdown()
                onFinished()
            }
            handler.postDelayed({ finish() }, timeoutMs)
            handler.post {
                speaker = Speaker(context)
                speaker?.speak(text, true) { finish() }
            }
        }
    }
}
