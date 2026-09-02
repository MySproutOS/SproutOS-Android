package com.sproutos.store

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.SessionParams
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

data class SessionInstallRequest(
    val release: ReleaseMetadata,
    val file: File,
    val automatic: Boolean,
    val userInitiated: Boolean,
)

sealed interface SessionInstallResult {
    data class Committed(val sessionId: Int, val requestedNoUserAction: Boolean) :
        SessionInstallResult

    data class Failed(val reason: String) : SessionInstallResult
}

/**
 * Writes one already-verified APK into Android's PackageInstaller.
 *
 * This is used for foreground installs too: once SproutOS performs an initial install through a
 * session, Android can record it as the installer/update owner and later evaluate eligible update
 * requests without another confirmation screen.
 */
fun commitPackageSession(context: Context, request: SessionInstallRequest): SessionInstallResult {
    val installer = context.packageManager.packageInstaller
    var sessionId: Int? = null
    try {
        val targetSdk =
            downloadedTargetSdk(context, request.file)
                ?: return SessionInstallResult.Failed("Android could not read the APK target SDK.")
        val source = installSource(context, request.release.packageName)
        val noUserAction =
            request.automatic &&
                canRequestUpdateWithoutUserAction(
                    deviceSdk = Build.VERSION.SDK_INT,
                    targetSdk = targetSdk,
                    isSelfUpdate = request.release.packageName == context.packageName,
                    ourPackageName = context.packageName,
                    source = source,
                )

        val params =
            SessionParams(SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(request.release.packageName)
                setSize(request.release.sizeBytes)
                setInstallReason(PackageManager.INSTALL_REASON_USER)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(
                        if (noUserAction) {
                            SessionParams.USER_ACTION_NOT_REQUIRED
                        } else {
                            SessionParams.USER_ACTION_REQUIRED
                        },
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    // Effective only on the initial install. On an update Android treats this as a
                    // no-op, so it is safe to set for both paths.
                    setRequestUpdateOwnership(true)
                }
            }

        sessionId = installer.createSession(params)
        val preferences = AutomaticUpdatePreferences(context)
        preferences.recordSession(
            sessionId,
            request.file.absolutePath,
            request.release.packageName,
        )
        installer.openSession(sessionId).use { session ->
            request.file.inputStream().use { sourceStream ->
                session.openWrite("base.apk", 0, request.release.sizeBytes).use { output ->
                    sourceStream.copyTo(output)
                    session.fsync(output)
                }
            }

            val callback =
                Intent(context, InstallResultReceiver::class.java).apply {
                    action = "${context.packageName}.INSTALL_RESULT.$sessionId"
                    putExtra(InstallResultReceiver.EXTRA_LABEL, request.release.label)
                    putExtra(InstallResultReceiver.EXTRA_PACKAGE, request.release.packageName)
                    putExtra(InstallResultReceiver.EXTRA_APK_PATH, request.file.absolutePath)
                    putExtra(InstallResultReceiver.EXTRA_USER_INITIATED, request.userInitiated)
                }
            val flags =
                PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.FLAG_MUTABLE
                    } else {
                        0
                    }
            val pending = PendingIntent.getBroadcast(context, sessionId, callback, flags)
            session.commit(pending.intentSender)
        }
        return SessionInstallResult.Committed(sessionId, noUserAction)
    } catch (cause: Exception) {
        sessionId?.let {
            runCatching { installer.abandonSession(it) }
            AutomaticUpdatePreferences(context).removeSession(it)
        }
        return SessionInstallResult.Failed(cause.message ?: "Android rejected the install session.")
    }
}

private fun installSource(context: Context, packageName: String): InstallSource? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
    return try {
        context.packageManager.getInstallSourceInfo(packageName).let {
            InstallSource(
                installerPackageName = it.installingPackageName,
                updateOwnerPackageName =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        it.updateOwnerPackageName
                    } else {
                        null
                    },
            )
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}
