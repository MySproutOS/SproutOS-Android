package com.sproutos.store

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

data class CopyResult(val sha256: String, val bytes: Long)

/** Lowercase hex and byte count, computed while the stream is written. */
fun hashingCopy(
    source: InputStream,
    sink: OutputStream,
    progress: (Long) -> Unit = {},
): CopyResult {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    var copied = 0L

    while (true) {
        val read = source.read(buffer)
        if (read <= 0) break
        digest.update(buffer, 0, read)
        sink.write(buffer, 0, read)
        copied += read
        progress(copied)
    }
    sink.flush()

    return CopyResult(digest.digest().joinToString("") { "%02x".format(it) }, copied)
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

    data class SizeMismatch(val expected: Long, val actual: Long) : DownloadResult

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
    app: ReleaseMetadata,
    into: File,
    progress: (Long, Long) -> Unit = { _, _ -> },
    open: (String) -> InputStream,
): DownloadResult {
    into.parentFile?.mkdirs()
    val partial = File(into.parentFile, "${into.name}.part")
    partial.delete()

    val copied =
        try {
            open(app.downloadUrl).use { source ->
                partial.outputStream().use { sink ->
                    hashingCopy(source, sink) { bytes ->
                        if (bytes > app.sizeBytes) {
                            throw IllegalArgumentException("download exceeded its published size")
                        }
                        progress(bytes, app.sizeBytes)
                    }
                }
            }
        } catch (cause: Exception) {
            partial.delete()
            return DownloadResult.Failed(cause.message ?: "the download failed")
        }

    if (copied.bytes != app.sizeBytes) {
        partial.delete()
        return DownloadResult.SizeMismatch(app.sizeBytes, copied.bytes)
    }
    if (copied.sha256 != app.sha256) {
        partial.delete()
        return DownloadResult.DigestMismatch(app.sha256, copied.sha256)
    }
    if (into.exists() && !into.delete()) {
        partial.delete()
        return DownloadResult.Failed("could not replace the cached release")
    }
    if (!partial.renameTo(into)) {
        partial.delete()
        return DownloadResult.Failed("could not finish the downloaded release")
    }

    return DownloadResult.Ok(into)
}
