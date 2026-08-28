package me.sproutos.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVerificationTest {
    private val app =
        App(
            androidAppId = "019d40f0-31d4-7394-90e2-3e20eb3350d1",
            projectId = "019d40f0-31d4-7394-90e2-3e20eb3350d2",
            packageName = "me.sproutos.app.p019d40f031d4739490e23e20eb3350d2",
            label = "Notes",
            versionName = "1.2.0",
            versionCode = 7,
            sha256 = "a".repeat(64),
            sizeBytes = 1024,
            certificateSha256 = "b".repeat(64),
            downloadUrl = "https://cdn/notes.apk",
        )

    @Test
    fun `accepts only the published package version and certificate`() {
        val actual =
            ReleaseIdentity(
                app.packageName,
                app.versionName,
                app.versionCode,
                app.certificateSha256,
            )
        assertEquals(ReleaseVerification.Ok, verifyReleaseIdentity(app, actual))
        assertTrue(
            verifyReleaseIdentity(app, actual.copy(packageName = "me.attacker.app"))
                is ReleaseVerification.Refused,
        )
        assertTrue(
            verifyReleaseIdentity(app, actual.copy(versionName = "7.0"))
                is ReleaseVerification.Refused,
        )
        assertTrue(
            verifyReleaseIdentity(app, actual.copy(versionCode = 8)) is ReleaseVerification.Refused,
        )
        assertTrue(
            verifyReleaseIdentity(app, actual.copy(certificateSha256 = "c".repeat(64)))
                is ReleaseVerification.Refused,
        )
    }

    @Test
    fun `install actions never downgrade`() {
        assertEquals(InstallAction.Install, installAction(7, null))
        assertEquals(InstallAction.Update, installAction(7, 6))
        assertEquals(InstallAction.Open, installAction(7, 7))
        assertEquals(InstallAction.RefuseDowngrade, installAction(7, 8))
        assertEquals(InstallAction.RefuseDifferentSigner, installAction(7, 6, signerMatches = false))
    }

    @Test
    fun `certificate fingerprints are lowercase sha256`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            certificateSha256("abc".toByteArray()),
        )
    }

    @Test
    fun `active installs disable both app rows and self update`() {
        assertTrue(!installButtonEnabled(InstallState.Downloading(1, 2), InstallAction.Update))
        assertTrue(!installButtonEnabled(InstallState.Verifying, InstallAction.Install))
        assertTrue(installButtonEnabled(InstallState.AwaitingPermission, InstallAction.Update))
        assertTrue(!installButtonEnabled(InstallState.Idle, InstallAction.RefuseDowngrade))
    }

    @Test
    fun `install progress is bounded and every waiting state explains itself`() {
        assertEquals("Downloading 50%", installStatusText(InstallState.Downloading(1, 2)))
        assertEquals("Downloading 100%", installStatusText(InstallState.Downloading(4, 2)))
        assertTrue(installStatusText(InstallState.Verifying)!!.contains("Verifying"))
        assertTrue(installStatusText(InstallState.AwaitingPermission)!!.contains("Allow installs"))
        assertTrue(installStatusText(InstallState.AwaitingInstaller)!!.contains("installation"))
        assertEquals("network failed", installStatusText(InstallState.Failed("network failed")))
        assertEquals(null, installStatusText(InstallState.Idle))
    }
}
