package com.sproutos.store

import java.security.MessageDigest

data class ReleaseIdentity(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val certificateSha256: String,
)

sealed interface ReleaseVerification {
    data object Ok : ReleaseVerification

    data class Refused(val reason: String) : ReleaseVerification
}

fun certificateSha256(encodedCertificate: ByteArray): String =
    MessageDigest.getInstance("SHA-256")
        .digest(encodedCertificate)
        .joinToString("") { "%02x".format(it) }

/** Shared by the archive and installed-package checks. */
fun verifyReleaseIdentity(expected: ReleaseMetadata, actual: ReleaseIdentity): ReleaseVerification =
    when {
        actual.packageName != expected.packageName ->
            ReleaseVerification.Refused("The APK declares a different package name.")
        actual.versionName != expected.versionName ->
            ReleaseVerification.Refused("The APK declares a different version name.")
        actual.versionCode != expected.versionCode ->
            ReleaseVerification.Refused("The APK declares a different version code.")
        actual.certificateSha256 != expected.certificateSha256 ->
            ReleaseVerification.Refused("The APK was signed by a different application key.")
        else -> ReleaseVerification.Ok
    }

enum class InstallAction {
    Install,
    Update,
    Open,
    RefuseDowngrade,
    RefuseDifferentSigner,
}

fun installAction(
    availableVersion: Long,
    installedVersion: Long?,
    signerMatches: Boolean = true,
): InstallAction =
    when {
        installedVersion == null -> InstallAction.Install
        !signerMatches -> InstallAction.RefuseDifferentSigner
        installedVersion < availableVersion -> InstallAction.Update
        installedVersion == availableVersion -> InstallAction.Open
        else -> InstallAction.RefuseDowngrade
    }

/** The same gate is used by catalogue rows and by the SproutOS self-update banner. */
fun installButtonEnabled(operation: InstallState, action: InstallAction): Boolean =
    operation !is InstallState.Downloading &&
        operation !is InstallState.Verifying &&
        action != InstallAction.RefuseDowngrade &&
        action != InstallAction.RefuseDifferentSigner

/** Visible and screen-reader-announced feedback for every asynchronous install stage. */
fun installStatusText(operation: InstallState): String? =
    when (operation) {
        InstallState.Idle -> null
        is InstallState.Downloading -> {
            val percent =
                ((operation.bytes * 100) / operation.total.coerceAtLeast(1)).coerceIn(0, 100)
            "Downloading $percent%"
        }
        InstallState.Verifying -> "Verifying package, version, and signature…"
        InstallState.AwaitingPermission -> "Allow installs from SproutOS, then tap again."
        InstallState.AwaitingInstaller -> "Complete the installation in Android."
        is InstallState.Failed -> operation.reason
    }
