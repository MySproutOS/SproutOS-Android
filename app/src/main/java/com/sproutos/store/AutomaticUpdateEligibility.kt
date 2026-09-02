package com.sproutos.store

import android.os.Build

/** Snapshot of Android's install-source relationship, kept separate so the policy is unit tested. */
data class InstallSource(
    val installerPackageName: String?,
    val updateOwnerPackageName: String?,
)

enum class AutomaticUpdateDecision {
    Update,
    NotInstalled,
    AlreadyCurrent,
    RefuseDowngrade,
    RefuseDifferentSigner,
}

/** Automatic work is update-only: absence is never interpreted as permission to install. */
fun automaticUpdateDecision(
    release: ReleaseMetadata,
    installed: ReleaseIdentity?,
): AutomaticUpdateDecision =
    when {
        installed == null -> AutomaticUpdateDecision.NotInstalled
        installed.certificateSha256 != release.certificateSha256 ->
            AutomaticUpdateDecision.RefuseDifferentSigner
        installed.versionCode < release.versionCode -> AutomaticUpdateDecision.Update
        installed.versionCode == release.versionCode -> AutomaticUpdateDecision.AlreadyCurrent
        else -> AutomaticUpdateDecision.RefuseDowngrade
    }

/**
 * Whether Android can accept USER_ACTION_NOT_REQUIRED for this update.
 *
 * The target-SDK floors are from the Android 12-17 PackageInstaller documentation. They advance
 * over time, so an unknown future Android release is treated as unsupported until this table is
 * intentionally updated. Even a true result is only a request: the result receiver must always
 * handle STATUS_PENDING_USER_ACTION.
 */
fun canRequestUpdateWithoutUserAction(
    deviceSdk: Int,
    targetSdk: Int,
    isSelfUpdate: Boolean,
    ourPackageName: String,
    source: InstallSource?,
): Boolean {
    val targetFloor =
        when (deviceSdk) {
            31, 32 -> 29
            33 -> 30
            34 -> 31
            35 -> 33
            36 -> 34
            37 -> 35
            else -> return false
        }
    if (targetSdk < targetFloor) return false
    if (isSelfUpdate) return true
    if (source == null) return false
    return if (
        deviceSdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            source.updateOwnerPackageName != null
    ) {
        source.updateOwnerPackageName == ourPackageName
    } else {
        source.installerPackageName == ourPackageName
    }
}
