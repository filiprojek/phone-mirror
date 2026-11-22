package cz.filiprojek.phonemirror.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import cz.filiprojek.phonemirror.MainActivity
import cz.filiprojek.phonemirror.R
import cz.filiprojek.phonemirror.core.net.ViewerInfo

object Notifications {
    const val STREAMING_ID = 1
    const val APPROVAL_ID = 2
    private const val CHANNEL_STREAMING = "streaming"
    private const val CHANNEL_APPROVAL = "approval"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_STREAMING, "Camera streaming", NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_APPROVAL, "Display requests", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    /** Heads-up prompt letting the camera owner allow or deny a display while the app is in the background. */
    fun approval(context: Context, viewer: ViewerInfo): Notification {
        ensureChannels(context)
        return Notification.Builder(context, CHANNEL_APPROVAL)
            .setSmallIcon(R.drawable.ic_stat_stream)
            .setContentTitle("Display wants to connect")
            .setContentText("${viewer.deviceName} (${viewer.address}) wants to see your camera.")
            .setCategory(Notification.CATEGORY_CALL)
            .setContentIntent(openApp(context))
            .setTimeoutAfter(30_000)
            .setAutoCancel(true)
            .addAction(action(context, "Allow", StreamingService.ACTION_ALLOW, 1))
            .addAction(action(context, "Deny", StreamingService.ACTION_DENY, 2))
            .build()
    }

    fun streaming(context: Context, viewerName: String?): Notification {
        ensureChannels(context)
        return Notification.Builder(context, CHANNEL_STREAMING)
            .setSmallIcon(R.drawable.ic_stat_stream)
            .setContentTitle("PhoneMirror camera is on")
            .setContentText(viewerName?.let { "Streaming to $it" } ?: "Waiting for a display to connect")
            .setOngoing(true)
            .setContentIntent(openApp(context))
            .addAction(action(context, "Stop", StreamingService.ACTION_STOP, 3))
            .build()
    }

    private fun action(context: Context, title: String, intentAction: String, requestCode: Int): Notification.Action {
        val intent = Intent(context, StreamingService::class.java).setAction(intentAction)
        val pending = PendingIntent.getService(
            context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_stat_stream), title, pending)
            .build()
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )
}
