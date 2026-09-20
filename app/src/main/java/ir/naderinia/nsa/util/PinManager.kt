package ir.naderinia.nsa.util

import android.content.Context
import java.security.MessageDigest

/**
 * Stores a hash of the PIN (never the PIN itself) in SharedPreferences.
 * This is a reasonable baseline for a local app-lock; if the app later
 * stores more sensitive data it's worth upgrading this to
 * EncryptedSharedPreferences (androidx.security:security-crypto).
 */
object PinManager {
    private const val PREFS_NAME = "nsa_security_prefs"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun hash(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun isPinSet(context: Context): Boolean =
        prefs(context).contains(KEY_PIN_HASH)

    fun setPin(context: Context, pin: String) {
        prefs(context).edit().putString(KEY_PIN_HASH, hash(pin)).apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean =
        prefs(context).getString(KEY_PIN_HASH, null) == hash(pin)

    fun clearPin(context: Context) {
        prefs(context).edit().remove(KEY_PIN_HASH).putBoolean(KEY_BIOMETRIC_ENABLED, false).apply()
    }

    fun isBiometricEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BIOMETRIC_ENABLED, false)

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    /** Whether the lock screen should be shown at all. */
    fun isLockEnabled(context: Context): Boolean = isPinSet(context)
}
