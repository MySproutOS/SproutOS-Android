package com.sproutos.store

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * The two HTTP calls this app makes.
 *
 * `HttpURLConnection` rather than a client library. Two GETs — one JSON, one file — do not justify
 * OkHttp's footprint in an app whose whole job is to be small enough that installing it from a
 * warning screen feels reasonable.
 */

/** How the caller is identified, when they are. */
data class Session(val oauth: OAuthSession?) {
    fun accessToken(nowEpochSeconds: Long = System.currentTimeMillis() / 1000): String? =
        oauth?.accessTokenAt(nowEpochSeconds)
}

/**
 * Fetch the catalogue.
 *
 * The session header is omitted rather than sent empty when there is none. An empty `Authorization`
 * is a malformed credential; no header is an anonymous request, and the platform answers it with a
 * catalogue whose personal half is empty — which is what the Public tab needs.
 */
fun fetchCatalogue(apiBase: String, session: Session): CatalogueResult =
    try {
        val connection = URL(catalogueUrl(apiBase)).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        session.accessToken()?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
        // Bounded, because a phone on a bad connection should show an error rather than a spinner
        // that never resolves.
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000

        when (connection.responseCode) {
            HttpURLConnection.HTTP_OK ->
                connection.inputStream.use { stream ->
                    parseCatalogue(stream.readBytes().decodeToString())
                }
            HttpURLConnection.HTTP_UNAUTHORIZED -> CatalogueResult.Unauthorized
            else -> CatalogueResult.Malformed("SproutOS returned HTTP ${connection.responseCode}")
        }
    } catch (cause: Exception) {
        CatalogueResult.Malformed(cause.message ?: "could not reach SproutOS")
    }

/**
 * Open an APK download.
 *
 * Follows redirects, because a signed object-storage URL is usually one. Handed to `downloadApk` as
 * a stream so the digest is computed on the way to disk.
 */
fun openDownload(url: String): InputStream {
    val connection = URL(url).openConnection() as HttpURLConnection
    require(connection.url.protocol == "https") { "APK downloads require HTTPS" }
    connection.instanceFollowRedirects = true
    connection.connectTimeout = 10_000
    // Generous: an APK is tens of megabytes and this is the read timeout between *packets*, not for
    // the whole transfer.
    connection.readTimeout = 60_000
    // Force redirects now so the final destination can be checked before any APK bytes are read.
    require(connection.responseCode == HttpURLConnection.HTTP_OK) {
        "APK download returned HTTP ${connection.responseCode}"
    }
    require(connection.url.protocol == "https") { "APK download redirected away from HTTPS" }
    require(
        connection.contentType
            ?.substringBefore(';')
            ?.equals("application/vnd.android.package-archive", ignoreCase = true) == true,
    ) {
        "APK download returned the wrong content type"
    }
    return connection.inputStream
}

/**
 * Exchange an authorization code for a token.
 *
 * Returns null on anything other than a token, so the caller reports "signing in did not complete"
 * rather than storing an empty string and behaving as though it worked.
 */
fun exchangeCode(
    apiBase: String,
    clientId: String,
    code: CallbackResult.Code,
    nowEpochSeconds: Long = System.currentTimeMillis() / 1000,
): OAuthSession? =
    try {
        val connection = URL("${apiBase.trimEnd('/')}/v1/oauth/token").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000

        connection.outputStream.use {
            it.write(tokenRequestBody(code.code, code.verifier, clientId).toByteArray())
        }

        connection.inputStream.use { parseToken(it.readBytes().decodeToString(), nowEpochSeconds) }
    } catch (_: Exception) {
        null
    }

fun refreshSession(
    apiBase: String,
    clientId: String,
    session: OAuthSession,
    nowEpochSeconds: Long = System.currentTimeMillis() / 1000,
): OAuthSession? =
    try {
        val connection = URL("${apiBase.trimEnd('/')}/v1/oauth/token").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.outputStream.use {
            it.write(refreshRequestBody(session.refreshToken, clientId).toByteArray())
        }
        connection.inputStream.use { parseToken(it.readBytes().decodeToString(), nowEpochSeconds) }
    } catch (_: Exception) {
        null
    }
