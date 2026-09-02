package com.sproutos.store

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

internal object AppVisibility {
    private val resumed = AtomicBoolean(false)

    fun activityResumed() = resumed.set(true)

    fun activityPaused() = resumed.set(false)

    fun isActivityResumed(): Boolean = resumed.get()
}

internal fun shouldStartConfirmationDirectly(userInitiated: Boolean, activityResumed: Boolean) =
    userInitiated && activityResumed

/** Receives the final or confirmation-required result for a committed install session. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "app"
        val packageName = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val file = intent.getStringExtra(EXTRA_APK_PATH)?.let(::File)
        val userInitiated = intent.getBooleanExtra(EXTRA_USER_INITIATED, false)
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val preferences = AutomaticUpdatePreferences(context)

        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                file?.delete()
                if (sessionId >= 0) preferences.removeSession(sessionId)
                preferences.setPendingMessage(null)
                NotificationManagerCompat.from(context).cancel(notificationId(packageName))
            }

            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirmation = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirmation == null) {
                    failPending(context, preferences, sessionId, label, file)
                    return
                }

                // A button tap may finish after the user has left SproutOS. Start Android's
                // confirmation directly only while our activity is actually resumed; otherwise
                // background-start limits and user context both require a notification.
                if (shouldStartConfirmationDirectly(userInitiated, AppVisibility.isActivityResumed())) {
                    val started =
                        runCatching {
                            context.startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }.isSuccess
                    if (started) return
                }
                if (!showConfirmationNotification(context, label, packageName, confirmation)) {
                    failPending(context, preferences, sessionId, label, file)
                } else {
                    preferences.setPendingMessage("Android needs confirmation to update $label.")
                }
            }

            else -> {
                file?.delete()
                if (sessionId >= 0) preferences.removeSession(sessionId)
                val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                preferences.setPendingMessage(
                    "Could not update $label${detail?.let { ": $it" }.orEmpty()}",
                )
            }
        }
    }

    private fun failPending(
        context: Context,
        preferences: AutomaticUpdatePreferences,
        sessionId: Int,
        label: String,
        file: File?,
    ) {
        if (sessionId >= 0) runCatching { context.packageManager.packageInstaller.abandonSession(sessionId) }
        if (sessionId >= 0) preferences.removeSession(sessionId)
        file?.delete()
        preferences.setPendingMessage(
            "Android needs confirmation to update $label. Open SproutOS and update it manually.",
        )
    }

    private fun showConfirmationNotification(
        context: Context,
        label: String,
        packageName: String,
        confirmation: Intent,
    ): Boolean {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false

        return runCatching {
            val systemManager = context.getSystemService(NotificationManager::class.java)
            systemManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "App update confirmations",
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
            val requestCode = notificationId(packageName)
            val action =
                PendingIntent.getActivity(
                    context,
                    requestCode,
                    confirmation,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val notification =
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("Confirm $label update")
                    .setContentText(
                        "Android requires your confirmation before SproutOS can update it.",
                    )
                    .setContentIntent(action)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build()
            manager.notify(requestCode, notification)
        }.isSuccess
    }

    private fun notificationId(packageName: String): Int = packageName.hashCode()

    companion object {
        const val EXTRA_LABEL = "label"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_APK_PATH = "apk_path"
        const val EXTRA_USER_INITIATED = "user_initiated"
        private const val CHANNEL_ID = "update-confirmations"
    }
}
