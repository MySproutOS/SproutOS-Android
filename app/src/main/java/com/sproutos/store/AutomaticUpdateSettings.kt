package com.sproutos.store

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

data class AutomaticUpdateSettings(
    val client: Boolean,
    val installedApps: Boolean,
)

/** User-controlled settings only; OAuth credentials remain in the encrypted session store. */
class AutomaticUpdatePreferences(context: Context) {
    private val preferences =
        context.getSharedPreferences("sproutos.automatic-updates", Context.MODE_PRIVATE)

    fun read(): AutomaticUpdateSettings =
        AutomaticUpdateSettings(
            client = preferences.getBoolean(CLIENT, true),
            installedApps = preferences.getBoolean(APPS, true),
        )

    fun setClient(enabled: Boolean) {
        preferences.edit().putBoolean(CLIENT, enabled).apply()
    }

    fun setInstalledApps(enabled: Boolean) {
        preferences.edit().putBoolean(APPS, enabled).apply()
    }

    fun pendingMessage(): String? = preferences.getString(PENDING_MESSAGE, null)

    fun setPendingMessage(message: String?) {
        preferences.edit().apply {
            if (message == null) remove(PENDING_MESSAGE) else putString(PENDING_MESSAGE, message)
        }.apply()
    }

    fun recordSession(
        sessionId: Int,
        file: String,
        packageName: String,
        createdAtMillis: Long = System.currentTimeMillis(),
    ) {
        preferences.edit()
            .putString("$SESSION_PREFIX$sessionId", "$createdAtMillis\n$file\n$packageName")
            .apply()
    }

    fun removeSession(sessionId: Int) {
        preferences.edit().remove("$SESSION_PREFIX$sessionId").apply()
    }

    fun staleSessions(
        nowMillis: Long = System.currentTimeMillis(),
        maximumAgeMillis: Long = 48 * 60 * 60 * 1000L,
    ): List<TrackedInstallSession> =
        preferences.all.mapNotNull { (key, value) ->
            if (!key.startsWith(SESSION_PREFIX) || value !is String) return@mapNotNull null
            val sessionId = key.removePrefix(SESSION_PREFIX).toIntOrNull() ?: return@mapNotNull null
            val created = value.substringBefore('\n').toLongOrNull() ?: return@mapNotNull null
            val remainder = value.substringAfter('\n', "")
            val file = remainder.substringBefore('\n')
            val packageName = remainder.substringAfter('\n', "")
            if (nowMillis - created >= maximumAgeMillis) {
                TrackedInstallSession(sessionId, file, packageName)
            } else {
                null
            }
        }

    private companion object {
        const val CLIENT = "client"
        const val APPS = "apps"
        const val PENDING_MESSAGE = "pending_message"
        const val SESSION_PREFIX = "session."
    }
}

data class TrackedInstallSession(val sessionId: Int, val file: String, val packageName: String)

/** An ignored confirmation must not occupy a PackageInstaller slot and cache space forever. */
fun cleanStaleInstallSessions(context: Context) {
    val preferences = AutomaticUpdatePreferences(context)
    for (record in preferences.staleSessions()) {
        runCatching { context.packageManager.packageInstaller.abandonSession(record.sessionId) }
        if (record.file.isNotBlank()) java.io.File(record.file).delete()
        if (record.packageName.isNotBlank()) {
            androidx.core.app.NotificationManagerCompat.from(context)
                .cancel(record.packageName.hashCode())
        }
        preferences.removeSession(record.sessionId)
    }
}

object AutomaticUpdateScheduler {
    internal const val UNIQUE_WORK_NAME = "sproutos-automatic-updates"

    /**
     * A unique daily job avoids duplicate schedules across launches. WorkManager is deliberately
     * inexact; update discovery has no user-visible deadline and should cooperate with Doze.
     */
    fun reconcile(context: Context, settings: AutomaticUpdateSettings) {
        val manager = WorkManager.getInstance(context)
        if (!settings.client && !settings.installedApps) {
            manager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }

        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()
        val work =
            PeriodicWorkRequestBuilder<AutomaticUpdateWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
        manager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            work,
        )
    }
}
