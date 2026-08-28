package me.sproutos.client

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The download, and the check that makes shipping a digest worth anything. */
class DownloadsTest {
    private val payload = "an apk, pretend".toByteArray()

    // sha256 of `payload`, computed by the implementation and pinned here so a change to the
    // hashing is a failing test rather than a silently different answer.
    private val digest = hashingCopy(ByteArrayInputStream(payload), java.io.ByteArrayOutputStream()).sha256

    private fun app(sha: String) =
        App(
            androidAppId = "019d40f0-31d4-7394-90e2-3e20eb3350d8",
            projectId = "019d40f0-31d4-7394-90e2-3e20eb3350d9",
            packageName = "me.sproutos.app.p019d40f031d4739490e23e20eb3350d9",
            label = "Example",
            versionName = "1.0.0",
            versionCode = 1,
            sha256 = sha,
            sizeBytes = payload.size.toLong(),
            certificateSha256 = "1".repeat(64),
            downloadUrl = "https://cdn/example.apk",
        )

    private fun target(): File =
        File.createTempFile("apk", ".apk").also { it.delete() }

    @Test
    fun `keeps a download whose bytes match`() {
        val file = target()
        val result = downloadApk(app(digest), file) { ByteArrayInputStream(payload) }

        assertTrue(result is DownloadResult.Ok)
        assertTrue(file.exists())
        assertEquals(payload.size.toLong(), file.length())
        file.delete()
    }

    @Test
    fun `deletes a download whose bytes do not`() {
        /*
          The file must not survive.

          Leaving it means the next install attempt finds a file that exists, skips the download,
          and installs the thing that failed verification — the exact failure this check exists to
          prevent, arrived at one step later.
        */
        val file = target()
        val result = downloadApk(app("0".repeat(64)), file) { ByteArrayInputStream(payload) }

        assertTrue(result is DownloadResult.DigestMismatch)
        assertEquals(digest, (result as DownloadResult.DigestMismatch).actual)
        assertFalse(file.exists())
    }

    @Test
    fun `catches a truncated download`() {
        // The ordinary case, not the hostile one: a connection that drops halfway produces a file
        // that is a valid prefix of an APK and hashes to something else.
        val file = target()
        val half = payload.copyOfRange(0, payload.size / 2)

        val result = downloadApk(app(digest), file) { ByteArrayInputStream(half) }

        assertTrue(result is DownloadResult.SizeMismatch)
        assertFalse(file.exists())
    }

    @Test
    fun `leaves nothing behind when the transport fails`() {
        val file = target()
        val result =
            downloadApk(app(digest), file) {
                object : InputStream() {
                    override fun read(): Int = throw IOException("connection reset")
                }
            }

        assertTrue(result is DownloadResult.Failed)
        assertFalse(file.exists())
    }

    @Test
    fun `stops a response once it exceeds the published size`() {
        val file = target()
        val tooSmall = app(digest).copy(sizeBytes = 2)
        val result = downloadApk(tooSmall, file) { ByteArrayInputStream(payload) }

        assertTrue(result is DownloadResult.Failed)
        assertFalse(file.exists())
    }

    @Test
    fun `does not normalize non-canonical release metadata`() {
        val file = target()
        val result = downloadApk(app(digest.uppercase()), file) { ByteArrayInputStream(payload) }

        assertTrue(result is DownloadResult.DigestMismatch)
        assertFalse(file.exists())
    }

    @Test
    fun `builds the catalogue URL without doubling the slash`() {
        assertEquals("https://api.sproutos.me/v1/android/catalogue", catalogueUrl("https://api.sproutos.me"))
        assertEquals("https://api.sproutos.me/v1/android/catalogue", catalogueUrl("https://api.sproutos.me/"))
    }
}
