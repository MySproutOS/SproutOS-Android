package com.sproutos.store

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AutomaticUpdateSettingsInstrumentedTest {
    @Test
    fun automaticUpdatesUseTheDocumentedHourlyCadence() {
        assertEquals(1L, AutomaticUpdateScheduler.REPEAT_INTERVAL_HOURS)
    }

    @Test
    fun settingsArePersistedAndIndependentlyControllable() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("sproutos.automatic-updates", 0).edit().clear().commit()
        val preferences = AutomaticUpdatePreferences(context)

        assertEquals(AutomaticUpdateSettings(client = true, installedApps = true), preferences.read())
        preferences.setClient(false)
        assertFalse(AutomaticUpdatePreferences(context).read().client)
        assertTrue(AutomaticUpdatePreferences(context).read().installedApps)

        preferences.recordSession(42, "/cache/app.apk", "me.sproutos.app", createdAtMillis = 1_000)
        assertEquals(emptyList<TrackedInstallSession>(), preferences.staleSessions(nowMillis = 1_500))
        assertEquals(
            listOf(TrackedInstallSession(42, "/cache/app.apk", "me.sproutos.app")),
            preferences.staleSessions(nowMillis = 3_000, maximumAgeMillis = 2_000),
        )
        preferences.removeSession(42)
        assertEquals(
            emptyList<TrackedInstallSession>(),
            preferences.staleSessions(nowMillis = 10_000, maximumAgeMillis = 0),
        )
    }

    @Test
    fun pendingMessageObserverSeesReceiverStyleSetAndClear() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("sproutos.automatic-updates", 0).edit().clear().commit()
        val preferences = AutomaticUpdatePreferences(context)
        val observed = mutableListOf<String?>()
        val pendingChange = CountDownLatch(1)
        val clearedChange = CountDownLatch(1)
        val subscription =
            preferences.observePendingMessage { message ->
                observed += message
                if (observed.size == 1) pendingChange.countDown() else clearedChange.countDown()
            }

        try {
            preferences.setPendingMessage("Android needs confirmation.")
            assertTrue(pendingChange.await(5, TimeUnit.SECONDS))
            assertEquals(listOf("Android needs confirmation."), observed)

            // PackageInstaller reports success only after the user has handled the pending action;
            // wait for that first receiver-style callback before exercising the later clear.
            preferences.setPendingMessage(null)
            assertTrue(clearedChange.await(5, TimeUnit.SECONDS))
            assertEquals(listOf("Android needs confirmation.", null), observed)
        } finally {
            subscription.close()
        }
    }

    @Test
    fun manifestDeclaresUpdaterPermissionsAndKeepsCallbackPrivate() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val packageInfo =
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS or PackageManager.GET_RECEIVERS,
            )
        val permissions = packageInfo.requestedPermissions.orEmpty().toSet()
        assertTrue(Manifest.permission.REQUEST_INSTALL_PACKAGES in permissions)
        assertTrue(Manifest.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION in permissions)
        assertTrue(Manifest.permission.ENFORCE_UPDATE_OWNERSHIP in permissions)
        assertTrue(Manifest.permission.POST_NOTIFICATIONS in permissions)

        val receiver =
            context.packageManager.getReceiverInfo(
                ComponentName(context, InstallResultReceiver::class.java),
                0,
            )
        assertFalse(receiver.exported)
    }
}
