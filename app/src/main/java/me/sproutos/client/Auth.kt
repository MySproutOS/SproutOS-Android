package me.sproutos.client

import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Signing in from the app.
 *
 * ## A browser, not a WebView
 *
 * The sign-in is a redirect flow, and a `WebView` inside this app could read the password as it was
 * typed. That is precisely the reason every OAuth guideline says native apps must use the system
 * browser, and it is the reason a customer can trust a SproutOS sign-in screen that looks the same
 * as the one in Chrome. Custom Tabs is the browser, in this app's window.
 *
 * ## PKCE is not optional here
 *
 * A public client cannot keep a secret — anything compiled into this APK is readable by anyone who
 * downloads it — so the authorization code is the only thing standing between an attacker and a
 * session. PKCE binds the code to a verifier that never leaves the device, so a code intercepted at
 * the redirect is useless without it.
 */

/** The redirect the platform sends the browser back to. Registered in the manifest. */
const val REDIRECT_URI: String = "sproutos://auth/callback"

/** The least privilege needed to populate the signed-in Personal catalogue. */
val REQUIRED_SCOPES: List<String> = listOf("project:read")

data class PendingAuth(val state: String, val verifier: String)

private val random = SecureRandom()

/*
  `java.util.Base64`, not `android.util.Base64`.

  The Android one is a framework class with no implementation in a JVM unit test — every call
  returns null and every assertion below would pass against nothing. The security-critical parts of
  this file are exactly the parts that must be testable without an emulator, so they use the
  standard library.
*/
private fun base64Url(bytes: ByteArray): String =
    java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

/**
 * A verifier and the state that goes with it.
 *
 * 32 bytes of `SecureRandom`, not `Random`: the whole value of both is that they cannot be guessed,
 * and `java.util.Random` is seeded from the clock.
 */
fun beginAuth(): PendingAuth {
    val verifierBytes = ByteArray(32).also { random.nextBytes(it) }
    val stateBytes = ByteArray(16).also { random.nextBytes(it) }
    return PendingAuth(state = base64Url(stateBytes), verifier = base64Url(verifierBytes))
}

/** S256, never `plain`. A `plain` challenge is the verifier, which defeats the point of sending one. */
fun challengeFor(verifier: String): String =
    base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))

private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

fun authorizeUrl(webBase: String, clientId: String, pending: PendingAuth): String {
    val query =
        listOf(
            "response_type" to "code",
            "client_id" to clientId,
            "redirect_uri" to REDIRECT_URI,
            "state" to pending.state,
            "code_challenge" to challengeFor(pending.verifier),
            // S256, never `plain`. A plain challenge *is* the verifier, which defeats the point of
            // sending one at all.
            "code_challenge_method" to "S256",
            "scope" to REQUIRED_SCOPES.joinToString(" "),
        )
            .joinToString("&") { (key, value) -> "$key=${encode(value)}" }

    return "${webBase.trimEnd('/')}/oauth/authorize?$query"
}

/** The query parameters of a redirect, without pulling in `android.net.Uri`. */
internal fun queryParams(uri: String): Map<String, String> {
    val query = uri.substringAfter('?', "")
    if (query.isEmpty()) return emptyMap()

    return query
        .split("&")
        .filter { it.isNotBlank() }
        .associate { pair ->
            val key = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            java.net.URLDecoder.decode(key, "UTF-8") to java.net.URLDecoder.decode(value, "UTF-8")
        }
}

sealed interface CallbackResult {
    data class Code(val code: String, val verifier: String) : CallbackResult

    /** The platform said no, or the person did. */
    data class Denied(val reason: String) : CallbackResult

    /**
     * The `state` did not match what was sent.
     *
     * Its own case rather than folded into `Denied`, because this is not a person declining — it is
     * a redirect that did not come from the flow this app started, which is the attack `state`
     * exists to catch.
     */
    data object StateMismatch : CallbackResult
}

/**
 * Read the redirect.
 *
 * `state` is compared before the code is looked at. Checking it afterwards would mean an
 * implementation that forgot the comparison still appeared to work, which is how the check gets
 * dropped.
 */
fun readCallback(uri: String, pending: PendingAuth?): CallbackResult {
    if (uri.substringBefore('?').substringBefore('#') != REDIRECT_URI) {
        return CallbackResult.StateMismatch
    }
    val parsed = queryParams(uri)

    if (pending == null) return CallbackResult.StateMismatch
    if (parsed["state"] != pending.state) return CallbackResult.StateMismatch

    parsed["error"]?.let { return CallbackResult.Denied(it) }

    val code = parsed["code"]
    return if (code.isNullOrBlank()) {
        CallbackResult.Denied("no code")
    } else {
        CallbackResult.Code(code, pending.verifier)
    }
}

@Serializable
data class TokenResponse(
    val access_token: String,
    val token_type: String = "Bearer",
    val expires_in: Long,
    val refresh_token: String,
    val scope: String,
)

data class OAuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
    val scopes: Set<String>,
) {
    fun accessTokenAt(nowEpochSeconds: Long): String? =
        accessToken.takeIf { nowEpochSeconds < expiresAtEpochSeconds - 30 }
}

private val tokenJson = Json { ignoreUnknownKeys = true }

fun parseToken(body: String, nowEpochSeconds: Long): OAuthSession? =
    try {
        val response = tokenJson.decodeFromString<TokenResponse>(body)
        val scopes = response.scope.split(' ').filter(String::isNotBlank).toSet()
        if (
            response.access_token.isBlank() ||
                response.refresh_token.isBlank() ||
                !response.token_type.equals("Bearer", ignoreCase = true) ||
                response.expires_in <= 0 ||
                !scopes.containsAll(REQUIRED_SCOPES)
        ) {
            null
        } else {
            OAuthSession(
                accessToken = response.access_token,
                refreshToken = response.refresh_token,
                expiresAtEpochSeconds = Math.addExact(nowEpochSeconds, response.expires_in),
                scopes = scopes,
            )
        }
    } catch (_: Exception) {
        null
    }

/** The form body for the code exchange. No client secret: this is a public client. */
fun tokenRequestBody(code: String, verifier: String, clientId: String): String =
    listOf(
        "grant_type" to "authorization_code",
        "code" to code,
        "redirect_uri" to REDIRECT_URI,
        "client_id" to clientId,
        "code_verifier" to verifier,
    )
        .joinToString("&") { (key, value) -> "$key=${encode(value)}" }

/** Refreshes an expired access token without broadening the original grant. */
fun refreshRequestBody(refreshToken: String, clientId: String): String =
    listOf(
        "grant_type" to "refresh_token",
        "refresh_token" to refreshToken,
        "client_id" to clientId,
        "scope" to REQUIRED_SCOPES.joinToString(" "),
    )
        .joinToString("&") { (key, value) -> "$key=${encode(value)}" }
