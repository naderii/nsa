package ir.naderinia.nsa.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Every sensitive preference file in the app (PIN hash, car odometer,
 * notification sound choice) goes through this instead of plain
 * getSharedPreferences — the values are encrypted at rest, with the
 * encryption key itself held in the Android Keystore (hardware-backed on
 * most devices), not stored alongside the data.
 */
object SecurePrefs {
    fun get(context: Context, fileName: String): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            fileName,
            masterKeyAlias,
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
