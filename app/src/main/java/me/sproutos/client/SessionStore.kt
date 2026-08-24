package me.sproutos.client

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Where the session token is kept.
 *
 * `EncryptedSharedPreferences`, so the key lives in the Android keystore and the file on disk is
 * ciphertext. Plain `SharedPreferences` would put a bearer token in an XML file readable by anyone
 * with a backup of the device or a rooted shell — and this token is the customer's whole account,
 * not a preference.
 *
 * There is no refresh token stored. A short session that has to be re-established is a smaller
 * thing to lose than a credential that renews itself indefinitely on a device somebody may not have
 * any more.
 */
class SessionStore(context: Context) {
    private val preferences =
        EncryptedSharedPreferences.create(
            context,
            "sproutos.session",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    fun read(): Session = Session(preferences.getString(TOKEN, null))

    fun write(token: String) {
        preferences.edit().putString(TOKEN, token).apply()
    }

    /** Signing out, and also what happens when the platform rejects the token. */
    fun clear() {
        preferences.edit().remove(TOKEN).apply()
    }

    private companion object {
        const val TOKEN = "access_token"
    }
}
