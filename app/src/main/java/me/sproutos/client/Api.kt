package me.sproutos.client

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
data class Session(val token: String?)

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
        session.token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
        // Bounded, because a phone on a bad connection should show an error rather than a spinner
        // that never resolves.
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000

        connection.inputStream.use { stream ->
            CatalogueResult::class.let { parseCatalogue(stream.readBytes().decodeToString()) }
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
    connection.instanceFollowRedirects = true
    connection.connectTimeout = 10_000
    // Generous: an APK is tens of megabytes and this is the read timeout between *packets*, not for
    // the whole transfer.
    connection.readTimeout = 60_000
    return connection.inputStream
}
