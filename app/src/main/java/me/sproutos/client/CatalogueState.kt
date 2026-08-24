package me.sproutos.client

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import java.io.File

/**
 * What the screen reads, and the two things it can do.
 *
 * Deliberately not a ViewModel with a coroutine scope: the fetch and the install are the only
 * asynchronous work in this app, and both are one call. The state is a plain holder so the screen
 * can be previewed and the logic tested without Android.
 */
class CatalogueState {
    var catalogue: Catalogue? by mutableStateOf(null)
        private set

    var error: String? by mutableStateOf(null)
        private set

    fun accept(result: CatalogueResult) {
        when (result) {
            is CatalogueResult.Ok -> {
                catalogue = result.catalogue
                error = null
            }

            is CatalogueResult.TooNew -> {
                catalogue = null
                // Named, because the fix is the customer's: this build cannot read what the server
                // is sending, and pretending the catalogue is empty would be a lie about their apps.
                error = "This version of SproutOS is too old to read the catalogue. Update the app."
            }

            is CatalogueResult.Malformed -> {
                catalogue = null
                error = "Could not read the catalogue. Try again in a moment."
            }
        }
    }

    fun entriesFor(tab: Tab): List<Entry> {
        val current = catalogue ?: return emptyList()
        return when (tab) {
            Tab.Public -> publicEntries(current)
            Tab.Personal -> personalEntries(current)
        }
    }

    /** Set by the activity. Null in tests and previews, where neither action is exercised. */
    var context: Context? = null

    /**
     * Download the APK if it is not already here, verify it, and hand it to the installer.
     *
     * The verification is the reason this is not two lines. A signed URL says the platform issued
     * it, not that what came back is intact — and a truncated response over a bad connection is the
     * ordinary case long before a hostile one is.
     */
    fun install(app: App) {
        val target = context ?: return
        val file = File(target.cacheDir, "apks/${app.packageName}.apk")

        if (!file.exists()) {
            when (val result = downloadApk(app, file, ::openDownload)) {
                is DownloadResult.Ok -> Unit

                is DownloadResult.DigestMismatch -> {
                    // Named for what it is. "Download failed" would send somebody to retry a thing
                    // that will fail the same way, and this is the one error worth looking at.
                    error = "That download did not match what SproutOS published. Nothing was installed."
                    return
                }

                is DownloadResult.Failed -> {
                    error = "Could not download ${app.label}: ${result.reason}"
                    return
                }
            }
        }

        /*
          A content URI, never a `file://` one.

          Android has rejected `file://` in an intent since 7.0 with a FileUriExposedException —
          the exception is thrown in *this* app, so a mistake here crashes the client rather than
          failing the install.
        */
        val uri: Uri =
            FileProvider.getUriForFile(target, "${target.packageName}.downloads", file)

        target.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    fun open(site: Site) {
        val target = context ?: return
        target.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(site.url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}
