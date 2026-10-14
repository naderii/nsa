package ir.naderinia.nsa.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.KeyStore

/**
 * Every sensitive preference file in the app (PIN hash, car odometer,
 * notification sound choice) goes through this instead of plain
 * getSharedPreferences — the values are encrypted at rest, with the
 * encryption key itself held in the Android Keystore (hardware-backed on
 * most devices), not stored alongside the data.
 *
 * Self-healing: the Keystore key never leaves the device, so if the encrypted
 * files exist but the key doesn't match (restored from a backup, moved to a new
 * phone, reinstalled, or a flaky Keystore on some ROMs) EncryptedSharedPreferences
 * throws and the whole app used to crash on launch until the person manually
 * cleared its data. Now the unreadable files are wiped once and recreated, so the
 * app always opens — the only cost is that those few settings reset.
 */
object SecurePrefs {

    /** Keyset file EncryptedSharedPreferences keeps next to the data files. */
    private const val KEYSET_PREFS = "__androidx_security_crypto_encrypted_prefs__"

    /** Every file name opened through [get] — they share one keyset, so they reset together. */
    private val ENCRYPTED_FILES = listOf("security_prefs", "car_prefs", "notification_prefs")

    @Synchronized
    fun get(context: Context, fileName: String): SharedPreferences {
        val app = context.applicationContext
        return try {
            create(app, fileName)
        } catch (_: Exception) {
            try {
                // one immediate retry before declaring the data unreadable
                create(app, fileName)
            } catch (_: Exception) {
                resetAll(app)
                try {
                    create(app, fileName)
                } catch (_: Exception) {
                    // Keystore itself is broken on this device: stay usable with a plain file.
                    app.getSharedPreferences("${fileName}_plain", Context.MODE_PRIVATE)
                }
            }
        }
    }

    private fun create(app: Context, fileName: String): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            fileName,
            masterKeyAlias,
            app,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun resetAll(app: Context) {
        ENCRYPTED_FILES.forEach { app.deleteSharedPreferences(it) }
        app.deleteSharedPreferences(KEYSET_PREFS)
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            keyStore.deleteEntry(MasterKeys.AES256_GCM_SPEC.keystoreAlias)
        } catch (_: Exception) {
            // best effort
        }
    }
}
