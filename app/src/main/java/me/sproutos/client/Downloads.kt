package me.sproutos.client

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * Fetching the catalogue, and fetching an APK.
 *
 * Both are plain `java.net` over a supplied stream opener, so the logic is testable without a
 * network and without an emulator. What matters here is not the transport — it is what happens to
 * the bytes after they arrive.
 */

/** Where the catalogue lives. */
fun catalogueUrl(apiBase: String): String = "${apiBase.trimEnd('/')}/v1/android/catalogue"

/** Lowercase hex of a stream's sha256, computed while it is being written. */
fun hashingCopy(source: InputStream, sink: OutputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)

    while (true) {
        val read = source.read(buffer)
        if (read <= 0) break
        digest.update(buffer, 0, read)
        sink.write(buffer, 0, read)
    }
    sink.flush()

    return digest.digest().joinToString("") { "%02x".format(it) }
}

sealed interface DownloadResult {
    data class Ok(val file: File) : DownloadResult

    /**
     * The bytes are not what the catalogue said they would be.
     *
     * The file is deleted before this is returned. Leaving it would mean the next install attempt
     * finds a file that exists, skips the download, and installs the thing that failed
     * verification — which is the whole failure this check exists to prevent, arrived at one step
     * later.
     */
    data class DigestMismatch(val expected: String, val actual: String) : DownloadResult

    data class Failed(val reason: String) : DownloadResult
}

/**
 * Download an APK and verify it against the digest the catalogue carried.
 *
 * **Verification is not optional.** Shipping a digest and not checking it is worse than not
 * shipping one: it looks like the bytes were verified. The URL is signed and expires, but a signed
 * URL says the platform issued it, not that what came back is intact — a truncated response over a
 * bad connection is the ordinary case, never mind a hostile one.
 *
 * The hash is computed while writing rather than by re-reading the file. Re-reading is a second
 * pass over tens of megabytes on a phone, and leaves a window in which the file on disk is not the
 * file that was hashed.
 */
fun downloadApk(
    app: App,
    into: File,
    open: (String) -> InputStream,
): DownloadResult {
    into.parentFile?.mkdirs()

    val actual =
        try {
            open(app.downloadUrl).use { source ->
                into.outputStream().use { sink -> hashingCopy(source, sink) }
            }
        } catch (cause: Exception) {
            into.delete()
            return DownloadResult.Failed(cause.message ?: "the download failed")
        }

    if (!actual.equals(app.sha256, ignoreCase = true)) {
        into.delete()
        return DownloadResult.DigestMismatch(app.sha256, actual)
    }

    return DownloadResult.Ok(into)
}
