package com.sproutos.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticUpdateEligibilityTest {
    private val release =
        App(
            androidAppId = "019d40f0-31d4-7394-90e2-3e20eb3350d1",
            projectId = "019d40f0-31d4-7394-90e2-3e20eb3350d2",
            packageName = "me.sproutos.app.p019d40f031d4739490e23e20eb3350d2",
            label = "Notes",
            versionName = "2.0",
            versionCode = 2,
            sha256 = "a".repeat(64),
            sizeBytes = 1024,
            certificateSha256 = "b".repeat(64),
            downloadUrl = "https://cdn/notes.apk",
        )

    @Test
    fun `automatic work never installs a missing app or replaces a different signer`() {
        assertEquals(AutomaticUpdateDecision.NotInstalled, automaticUpdateDecision(release, null))
        assertEquals(
            AutomaticUpdateDecision.RefuseDifferentSigner,
            automaticUpdateDecision(
                release,
                ReleaseIdentity(release.packageName, "1.0", 1, "c".repeat(64)),
            ),
        )
    }

    @Test
    fun `automatic work only accepts a higher version with the same signer`() {
        fun installed(version: Long) =
            ReleaseIdentity(release.packageName, version.toString(), version, release.certificateSha256)

        assertEquals(AutomaticUpdateDecision.Update, automaticUpdateDecision(release, installed(1)))
        assertEquals(
            AutomaticUpdateDecision.AlreadyCurrent,
            automaticUpdateDecision(release, installed(2)),
        )
        assertEquals(
            AutomaticUpdateDecision.RefuseDowngrade,
            automaticUpdateDecision(release, installed(3)),
        )
    }

    @Test
    fun `target sdk floor advances with each documented Android release`() {
        val ours = "com.sproutos.store"
        val source = InstallSource(installerPackageName = ours, updateOwnerPackageName = null)
        assertTrue(canRequestUpdateWithoutUserAction(31, 29, false, ours, source))
        assertFalse(canRequestUpdateWithoutUserAction(33, 29, false, ours, source))
        assertTrue(canRequestUpdateWithoutUserAction(33, 30, false, ours, source))
        assertFalse(canRequestUpdateWithoutUserAction(35, 32, false, ours, source))
        assertTrue(canRequestUpdateWithoutUserAction(35, 33, false, ours, source))
        assertTrue(canRequestUpdateWithoutUserAction(36, 34, false, ours, source))
        assertFalse(canRequestUpdateWithoutUserAction(37, 34, false, ours, source))
        assertTrue(canRequestUpdateWithoutUserAction(37, 35, false, ours, source))
        assertFalse(canRequestUpdateWithoutUserAction(38, 100, false, ours, source))
    }

    @Test
    fun `only self installer of record or update owner is eligible`() {
        val ours = "com.sproutos.store"
        assertTrue(canRequestUpdateWithoutUserAction(35, 35, true, ours, null))
        assertFalse(canRequestUpdateWithoutUserAction(35, 35, false, ours, null))
        assertFalse(
            canRequestUpdateWithoutUserAction(
                35,
                35,
                false,
                ours,
                InstallSource("com.other.store", "com.other.store"),
            ),
        )
        assertTrue(
            canRequestUpdateWithoutUserAction(
                35,
                35,
                false,
                ours,
                InstallSource("com.other.store", ours),
            ),
        )
        assertFalse(
            canRequestUpdateWithoutUserAction(
                35,
                35,
                false,
                ours,
                InstallSource(ours, "com.other.store"),
            ),
        )
    }

    @Test
    fun `installed app candidates exclude client package and retain highest app version`() {
        val clientEntry = release.copy(packageName = "com.sproutos.store", versionCode = 99)
        val old = release.copy(versionCode = 1)
        val current = release.copy(versionCode = 2)
        val catalogue =
            Catalogue(
                version = SUPPORTED_VERSION,
                generatedAt = "2026-09-01T00:00:00Z",
                expiresAt = "2026-09-02T00:00:00Z",
                public = PublicSection(apps = listOf(clientEntry, old)),
                personal = PersonalSection(apps = listOf(current)),
            )

        assertEquals(
            listOf(current),
            selectInstalledAppUpdateCandidates(catalogue, "com.sproutos.store"),
        )
    }
}
