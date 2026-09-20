package ir.naderinia.nsa.util

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object OcrHelper {

    /**
     * Runs on-device OCR (Google ML Kit — free, no network call, no API key,
     * bundled model) and returns the raw recognized text.
     *
     * Important limitation: this recognizer supports Latin script only. It
     * will NOT read Persian/Arabic words reliably. It's mainly useful for
     * pulling numeric amounts (printed in Western digits, as most Iranian
     * bank slips/receipts do) off a photo — titles and descriptions still
     * need to be typed by hand.
     */
    suspend fun recognizeText(context: Context, imageUri: Uri): String =
        suspendCancellableCoroutine { continuation ->
            try {
                val image = InputImage.fromFilePath(context, imageUri)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image)
                    .addOnSuccessListener { result -> continuation.resume(result.text) }
                    .addOnFailureListener { e -> continuation.resumeWithException(e) }
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }

    /** Extracts plausible amount candidates (digit sequences, possibly with
     * thousand separators) from raw OCR text, filtered to a sane Toman range. */
    fun extractNumberCandidates(rawText: String): List<Long> {
        val regex = Regex("""[\d,،.]{3,}""")
        return regex.findAll(rawText)
            .mapNotNull { match ->
                match.value.replace(",", "").replace("،", "").replace(".", "").toLongOrNull()
            }
            .filter { it in 1_000..999_999_999L }
            .distinct()
            .toList()
    }
}
