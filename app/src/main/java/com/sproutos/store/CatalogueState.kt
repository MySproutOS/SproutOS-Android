package com.sproutos.store

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * What the screen reads, and the two things it can do.
 *
 * Deliberately not a ViewModel with a coroutine scope: the fetch and the install are the only
 * asynchronous work in this app, and both are one call. The state is a plain holder so the screen
 * can be previewed and the logic tested without Android.
 */
class CatalogueState {
    private val mainHandler = Handler(Looper.getMainLooper())
    var catalogue: Catalogue? by mutableStateOf(null)
        private set

    var error: String? by mutableStateOf(null)
        private set

    var loading: Boolean by mutableStateOf(false)
        private set

    var installState: Map<String, InstallState> by mutableStateOf(emptyMap())
        private set

    var automaticUpdateSettings: AutomaticUpdateSettings by
        mutableStateOf(AutomaticUpdateSettings(client = true, installedApps = true))

    var automaticUpdateMessage: String? by mutableStateOf(null)

    var updateNotificationsNeedPermission: Boolean by mutableStateOf(false)

    fun beginRefresh() {
        loading = true
        error = null
    }

    fun accept(result: CatalogueResult) {
        loading = false
        when (result) {
            is CatalogueResult.Ok -> {
                catalogue = result.catalogue
                error = null
            }

            is CatalogueResult.UnsupportedVersion -> {
                catalogue = null
                error =
                    if (result.version > SUPPORTED_VERSION) {
                        "This version of SproutOS is too old to read the catalogue. Update the app."
                    } else {
                        "SproutOS returned an obsolete catalogue. Try again in a moment."
                    }
            }

            CatalogueResult.Unauthorized -> {
                catalogue = null
                error = "Your sign-in expired. Sign in again to see personal apps."
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

    /** Set by the activity. Null in tests and previews, where no Android action is exercised. */
    var context: Context? = null

    var session: Session by mutableStateOf(Session(null))
        private set

    val signedIn: Boolean get() = session.oauth != null

    fun restore(store: SessionStore) {
        session = store.read()
    }

    /**
     * Open the system browser at the sign-in page.
     *
     * A Custom Tab, not a WebView: a WebView in this app could read the password as it is typed.
     */
    fun signIn(webBase: String, clientId: String, store: SessionStore) {
        val target = context ?: return
        val attempt = beginAuth()
        store.writePending(attempt)

        androidx.browser.customtabs.CustomTabsIntent.Builder()
            .build()
            .launchUrl(target, Uri.parse(authorizeUrl(webBase, clientId, attempt)))
    }

    /**
     * Handle the redirect the browser sent back.
     *
     * The pending attempt is cleared whatever the outcome. A verifier that outlives its attempt is one an
     * injected callback could be paired with later, and a state that stays valid is a state that
     * can be replayed.
     */
    fun readSignInCallback(uri: String, store: SessionStore): CallbackResult {
        val result = readCallback(uri, store.takePending())
        if (result is CallbackResult.Denied) error = null
        if (result is CallbackResult.StateMismatch) {
            error = "That sign-in did not come from SproutOS. Nothing was changed."
        }
        return result
    }

    fun acceptSession(oauth: OAuthSession, store: SessionStore) {
        store.write(oauth)
        session = Session(oauth)
        error = null
    }

    fun replaceSession(oauth: OAuthSession, store: SessionStore) {
        store.write(oauth)
        session = Session(oauth)
    }

    fun signInFailed() {
        error = "Signing in did not complete. Try again."
    }

    fun signOut(store: SessionStore) {
        store.clear()
        session = Session(null)
        catalogue = null
        loading = false
    }

    /**
     * Download the APK if it is not already here, verify it, and hand it to the installer.
     *
     * The verification is the reason this is not two lines. A signed URL says the platform issued
     * it, not that what came back is intact — and a truncated response over a bad connection is the
     * ordinary case long before a hostile one is.
     */
    fun prepareInstall(app: ReleaseMetadata): PreparedInstall {
        val target =
            context ?: return PreparedInstall.Refused("Android is not ready to install apps.")
        val installed = installedRelease(target, app)
        if (installed != null) {
            if (installed.certificateSha256 != app.certificateSha256) {
                return PreparedInstall.Refused(
                    "The installed app uses a different signing key. SproutOS will not replace it.",
                )
            }
            when (installAction(app.versionCode, installed.versionCode)) {
                InstallAction.Open -> return PreparedInstall.Open(app.packageName)
                InstallAction.RefuseDowngrade ->
                    return PreparedInstall.Refused("A newer version is already installed.")
                InstallAction.RefuseDifferentSigner ->
                    return PreparedInstall.Refused(
                        "The installed app uses a different signing key. SproutOS will not replace it.",
                    )
                InstallAction.Install, InstallAction.Update -> Unit
            }
        }

        val file = File(target.cacheDir, "apks/${app.packageName}-${app.versionCode}.apk")
        setInstallState(app, InstallState.Downloading(0, app.sizeBytes))

        when (
            val result =
                downloadApk(
                    app,
                    file,
                    progress = { downloaded, total ->
                        setInstallState(app, InstallState.Downloading(downloaded, total))
                    },
                    open = ::openDownload,
                )
        ) {
                is DownloadResult.Ok -> Unit

                is DownloadResult.DigestMismatch -> {
                    // Named for what it is. "Download failed" would send somebody to retry a thing
                    // that will fail the same way, and this is the one error worth looking at.
                    return PreparedInstall.Refused(
                        "That download did not match what SproutOS published. Nothing was installed.",
                    )
                }

                is DownloadResult.SizeMismatch ->
                    return PreparedInstall.Refused(
                        "That download was not the size SproutOS published. Nothing was installed.",
                    )

                is DownloadResult.Failed -> {
                    return PreparedInstall.Refused("Could not download ${app.label}: ${result.reason}")
                }
            }

        setInstallState(app, InstallState.Verifying)
        return when (val verified = verifyDownloadedApk(target, app, file)) {
            ReleaseVerification.Ok -> PreparedInstall.Ready(file)
            is ReleaseVerification.Refused -> {
                file.delete()
                PreparedInstall.Refused(verified.reason)
            }
        }
    }

    fun launchPrepared(app: ReleaseMetadata, prepared: PreparedInstall) {
        val target = context ?: return
        when (prepared) {
            is PreparedInstall.Refused -> {
                setInstallState(app, InstallState.Failed(prepared.reason))
            }

            is PreparedInstall.Open -> {
                val launch = target.packageManager.getLaunchIntentForPackage(prepared.packageName)
                if (launch == null) {
                    setInstallState(app, InstallState.Failed("Android could not open this app."))
                    return
                }
                target.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                setInstallState(app, InstallState.Idle)
            }

            is PreparedInstall.Ready -> launchInstaller(target, app, prepared.file)
        }
    }

    private fun launchInstaller(target: Context, app: ReleaseMetadata, file: File) {
        if (!target.packageManager.canRequestPackageInstalls()) {
            setInstallState(app, InstallState.AwaitingPermission)
            target.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${target.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }

        when (
            val committed =
                commitPackageSession(
                    target,
                    SessionInstallRequest(
                        release = app,
                        file = file,
                        automatic = false,
                        userInitiated = true,
                    ),
                )
        ) {
            is SessionInstallResult.Committed ->
                setInstallState(app, InstallState.AwaitingInstaller)
            is SessionInstallResult.Failed ->
                setInstallState(app, InstallState.Failed(committed.reason))
        }
    }

    fun actionFor(app: ReleaseMetadata): InstallAction {
        val target = context ?: return InstallAction.Install
        val installed = installedRelease(target, app)
        return installAction(
            app.versionCode,
            installed?.versionCode,
            installed == null || installed.certificateSha256 == app.certificateSha256,
        )
    }

    fun setInstallState(app: ReleaseMetadata, value: InstallState) {
        val apply = { installState = installState + (app.packageName to value) }
        if (Looper.myLooper() == Looper.getMainLooper()) apply() else mainHandler.post(apply)
    }

    /** The installer activity has returned; the next action is recalculated from PackageManager. */
    fun clearInstallerWaits() {
        installState =
            installState.mapValues { (_, value) ->
                if (value is InstallState.AwaitingInstaller) InstallState.Idle else value
            }
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

sealed interface PreparedInstall {
    data class Ready(val file: File) : PreparedInstall
    data class Open(val packageName: String) : PreparedInstall
    data class Refused(val reason: String) : PreparedInstall
}

sealed interface InstallState {
    data object Idle : InstallState
    data class Downloading(val bytes: Long, val total: Long) : InstallState
    data object Verifying : InstallState
    data object AwaitingPermission : InstallState
    data object AwaitingInstaller : InstallState
    data class Failed(val reason: String) : InstallState
}
