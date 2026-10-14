package ir.naderinia.nsa.util

import android.content.Context
import java.nio.CharBuffer
import java.security.MessageDigest

/**
 * Lightweight app-lock storage.
 *
 * The PIN itself is never stored. Only its SHA-256 hash is persisted.
 *
 * Note:
 * SHA-256 is suitable for preventing plaintext PIN storage, but because
 * PINs have low entropy, a password KDF such as PBKDF2 is preferable
 * for stronger protection against offline brute-force attacks.
 */
object SecurityPrefs {

    private const val PREFS_NAME = "security_prefs"

    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_LOCK_ENABLED = "lock_enabled"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
    private const val KEY_LOCKOUT_UNTIL = "lockout_until"

    private const val FREE_ATTEMPTS = 5
    private const val BASE_LOCKOUT_MILLIS = 30_000L

    private fun prefs(context: Context) =
        SecurePrefs.get(context, PREFS_NAME)

    fun isLockEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LOCK_ENABLED, false)

    fun isBiometricEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BIOMETRIC_ENABLED, false)

    fun hasPin(context: Context): Boolean =
        prefs(context).contains(KEY_PIN_HASH)

    fun setPin(context: Context, pin: CharArray) {
        try {
            prefs(context).edit()
                .putString(KEY_PIN_HASH, hash(pin))
                .putBoolean(KEY_LOCK_ENABLED, true)
                .apply()
        } finally {
            pin.fill('\u0000')
        }
    }

    /** Milliseconds left of the "too many wrong PINs" wait; 0 when guessing is allowed. */
    fun lockoutRemainingMillis(context: Context): Long =
        (prefs(context).getLong(KEY_LOCKOUT_UNTIL, 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    /**
     * Counts a wrong PIN. A 4-digit PIN has only 10 000 combinations, so after 5 misses every
     * further miss locks entry for 30 s, 60 s, 2 min ... (max about 32 min).
     * Returns the new wait in milliseconds (0 = still free to try).
     */
    fun registerFailedAttempt(context: Context): Long {
        val p = prefs(context)
        val fails = p.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        val wait = if (fails >= FREE_ATTEMPTS) {
            BASE_LOCKOUT_MILLIS shl (fails - FREE_ATTEMPTS).coerceAtMost(6)
        } else 0L
        p.edit()
            .putInt(KEY_FAILED_ATTEMPTS, fails)
            .putLong(KEY_LOCKOUT_UNTIL, if (wait > 0) System.currentTimeMillis() + wait else 0L)
            .apply()
        return wait
    }

    fun resetFailedAttempts(context: Context) {
        prefs(context).edit().remove(KEY_FAILED_ATTEMPTS).remove(KEY_LOCKOUT_UNTIL).apply()
    }

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        prefs(context)
            .edit()
            .putBoolean(KEY_BIOMETRIC_ENABLED, enabled)
            .apply()
    }

    fun disableLock(context: Context) {
        prefs(context)
            .edit()
            .remove(KEY_PIN_HASH)
            .putBoolean(KEY_LOCK_ENABLED, false)
            .putBoolean(KEY_BIOMETRIC_ENABLED, false)
            .apply()
    }

    fun verifyPin(context: Context, pin: CharArray): Boolean {
        return try {
            val stored = prefs(context)
                .getString(KEY_PIN_HASH, null)
                ?: return false

            MessageDigest.isEqual(
                stored.toByteArray(Charsets.UTF_8),
                hash(pin).toByteArray(Charsets.UTF_8)
            )
        } finally {
            pin.fill('\u0000')
        }
    }

    private fun hash(input: CharArray): String {
        val buffer = Charsets.UTF_8.encode(CharBuffer.wrap(input))
        val bytes = ByteArray(buffer.remaining())

        buffer.get(bytes)

        return try {
            MessageDigest
                .getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { "%02x".format(it) }
        } finally {
            bytes.fill(0)
        }
    }
}