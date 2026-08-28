package com.sproutos.store

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

private fun PackageInfo.versionCodeCompat(): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        longVersionCode
    } else {
        @Suppress("DEPRECATION")
        versionCode.toLong()
    }

private fun PackageInfo.signerCertificates(): List<ByteArray> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val info = signingInfo ?: return emptyList()
        val signers =
            if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
        signers.orEmpty().map { it.toByteArray() }
    } else {
        @Suppress("DEPRECATION")
        signatures.orEmpty().map { it.toByteArray() }
    }

private fun PackageInfo.releaseIdentity(): ReleaseIdentity? {
    val certificates = signerCertificates()
    // SproutOS provisions one independent application key. Multiple current/history certificates
    // are not silently accepted until key rotation has an explicit platform contract.
    if (certificates.size != 1) return null
    return ReleaseIdentity(
        packageName,
        versionName.orEmpty(),
        versionCodeCompat(),
        certificateSha256(certificates.single()),
    )
}

@Suppress("DEPRECATION")
private fun flags(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        PackageManager.GET_SIGNATURES
    }

fun verifyDownloadedApk(context: Context, app: ReleaseMetadata, file: File): ReleaseVerification {
    val info =
        context.packageManager.getPackageArchiveInfo(file.absolutePath, flags())
            ?: return ReleaseVerification.Refused("Android could not read this APK.")
    val identity =
        info.releaseIdentity()
            ?: return ReleaseVerification.Refused(
                "The APK does not have exactly one trusted signer.",
            )
    return verifyReleaseIdentity(app, identity)
}

fun installedRelease(context: Context, app: ReleaseMetadata): ReleaseIdentity? =
    try {
        context.packageManager.getPackageInfo(app.packageName, flags()).releaseIdentity()
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
