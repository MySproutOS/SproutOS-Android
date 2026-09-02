package com.sproutos.store

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AutomaticUpdateWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            cleanStaleInstallSessions(applicationContext)
            val settings = AutomaticUpdatePreferences(applicationContext).read()
            if (!settings.client && !settings.installedApps) return@withContext Result.success()

            when (val result = authenticatedCatalogue(applicationContext)) {
                is CatalogueResult.Ok -> updateFrom(result.catalogue, settings)
                CatalogueResult.Unauthorized -> Result.retry()
                is CatalogueResult.Malformed -> Result.retry()
                is CatalogueResult.UnsupportedVersion -> Result.failure()
            }
        }

    private fun updateFrom(
        catalogue: Catalogue,
        settings: AutomaticUpdateSettings,
    ): Result {
        var transientFailure = false
        if (settings.installedApps) {
            val releases =
                (catalogue.public.apps + catalogue.personal.apps)
                    .groupBy { it.packageName }
                    .mapNotNull { (_, versions) -> versions.maxByOrNull { it.versionCode } }
            for (release in releases) {
                when (queueVerifiedUpdate(release)) {
                    QueueResult.TransientFailure -> transientFailure = true
                    QueueResult.NotAnUpdate, QueueResult.Queued, QueueResult.Refused -> Unit
                }
            }
        }

        // Commit the client update last: a successful self-update can stop this process.
        if (settings.client) {
            catalogue.clientUpdate?.let { release ->
                if (queueVerifiedUpdate(release) == QueueResult.TransientFailure) {
                    transientFailure = true
                }
            }
        }
        return if (transientFailure) Result.retry() else Result.success()
    }

    private fun queueVerifiedUpdate(release: ReleaseMetadata): QueueResult {
        if (
            applicationContext.packageManager.packageInstaller.mySessions.any {
                it.appPackageName == release.packageName
            }
        ) {
            return QueueResult.Queued
        }
        val installed = installedRelease(applicationContext, release)
        when (automaticUpdateDecision(release, installed)) {
            AutomaticUpdateDecision.Update -> Unit
            AutomaticUpdateDecision.RefuseDifferentSigner -> return QueueResult.Refused
            AutomaticUpdateDecision.NotInstalled,
            AutomaticUpdateDecision.AlreadyCurrent,
            AutomaticUpdateDecision.RefuseDowngrade -> return QueueResult.NotAnUpdate
        }

        val file =
            File(
                applicationContext.cacheDir,
                "automatic-apks/${release.packageName}-${release.versionCode}.apk",
            )
        when (downloadApk(release, file, open = ::openDownload)) {
            is DownloadResult.Ok -> Unit
            is DownloadResult.DigestMismatch, is DownloadResult.SizeMismatch -> return QueueResult.Refused
            is DownloadResult.Failed -> return QueueResult.TransientFailure
        }
        if (verifyDownloadedApk(applicationContext, release, file) != ReleaseVerification.Ok) {
            file.delete()
            return QueueResult.Refused
        }

        return when (
            commitPackageSession(
                applicationContext,
                SessionInstallRequest(
                    release = release,
                    file = file,
                    automatic = true,
                    userInitiated = false,
                ),
            )
        ) {
            is SessionInstallResult.Committed -> QueueResult.Queued
            is SessionInstallResult.Failed -> {
                file.delete()
                QueueResult.TransientFailure
            }
        }
    }

    private enum class QueueResult {
        NotAnUpdate,
        Queued,
        Refused,
        TransientFailure,
    }
}

private fun authenticatedCatalogue(context: Context): CatalogueResult {
    val sessionStore = SessionStore(context)
    var session = sessionStore.read()
    val oauth = session.oauth
    if (oauth != null && session.accessToken() == null) {
        val refreshed = refreshSession(context.getString(R.string.api_base), OAUTH_CLIENT_ID, oauth)
        if (refreshed != null) {
            sessionStore.write(refreshed)
            session = Session(refreshed)
        }
    }

    val first = fetchCatalogue(context.getString(R.string.api_base), session)
    // A revoked/expired private session must not prevent public app or client updates.
    return if (first == CatalogueResult.Unauthorized) {
        fetchCatalogue(context.getString(R.string.api_base), Session(null))
    } else {
        first
    }
}
