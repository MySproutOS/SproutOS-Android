@file:Suppress("DEPRECATION")

package com.sproutos.store

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
 * Access and refresh tokens are both encrypted. The access token is short lived; the rotating
 * refresh token keeps catalogue refresh asynchronous without sending a user through the browser
 * every hour. Revoking the OAuth grant invalidates both at the platform.
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

    fun read(): Session {
        val access = preferences.getString(ACCESS_TOKEN, null) ?: return Session(null)
        val refresh = preferences.getString(REFRESH_TOKEN, null) ?: return Session(null)
        val expires = preferences.getLong(EXPIRES_AT, 0)
        val scopes = preferences.getStringSet(SCOPES, emptySet()) ?: emptySet()
        return Session(OAuthSession(access, refresh, expires, scopes))
    }

    fun write(session: OAuthSession) {
        preferences.edit()
            .putString(ACCESS_TOKEN, session.accessToken)
            .putString(REFRESH_TOKEN, session.refreshToken)
            .putLong(EXPIRES_AT, session.expiresAtEpochSeconds)
            .putStringSet(SCOPES, session.scopes)
            .apply()
    }

    fun writePending(pending: PendingAuth) {
        preferences.edit()
            .putString(PENDING_STATE, pending.state)
            .putString(PENDING_VERIFIER, pending.verifier)
            .apply()
    }

    fun takePending(): PendingAuth? {
        val state = preferences.getString(PENDING_STATE, null)
        val verifier = preferences.getString(PENDING_VERIFIER, null)
        preferences.edit().remove(PENDING_STATE).remove(PENDING_VERIFIER).apply()
        return if (state == null || verifier == null) null else PendingAuth(state, verifier)
    }

    /** Signing out, and also what happens when the platform rejects the token. */
    fun clear() {
        preferences.edit()
            .remove(ACCESS_TOKEN)
            .remove(REFRESH_TOKEN)
            .remove(EXPIRES_AT)
            .remove(SCOPES)
            .remove(PENDING_STATE)
            .remove(PENDING_VERIFIER)
            .apply()
    }

    private companion object {
        const val ACCESS_TOKEN = "access_token"
        const val REFRESH_TOKEN = "refresh_token"
        const val EXPIRES_AT = "expires_at"
        const val SCOPES = "scopes"
        const val PENDING_STATE = "pending_state"
        const val PENDING_VERIFIER = "pending_verifier"
    }
}
