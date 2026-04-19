package com.vibepad.keyboard.hid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.vibepad.keyboard.R
import com.vibepad.keyboard.ui.MainActivity

/**
 * Creates and updates the persistent notification shown while the HID foreground
 * service is running.
 *
 * The title line reflects the current [HidLinkState]; the subtitle surfaces the host
 * name when connected. A single "Disconnect" action lets the user tear down the link
 * straight from the shade.
 */
internal object HidNotifications {

    const val CHANNEL_ID = "hid_link"
    const val NOTIFICATION_ID = 42

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun build(context: Context, state: HidLinkState): Notification {
        val title = context.getString(R.string.notification_title)
        val text = when (state) {
            is HidLinkState.Connected ->
                context.getString(R.string.notification_text_connected, state.host.name)
            HidLinkState.Advertising -> context.getString(R.string.state_advertising)
            is HidLinkState.Reconnecting -> context.getString(R.string.state_reconnecting)
            is HidLinkState.Unavailable -> context.getString(R.string.state_unavailable)
            is HidLinkState.Failed -> context.getString(R.string.state_failed)
            HidLinkState.Proxying -> context.getString(R.string.state_proxying)
            HidLinkState.Idle -> context.getString(R.string.notification_text_idle)
        }

        val openAppIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP,
            ),
            pendingIntentFlagsImmutable(),
        )

        val disconnectIntent = PendingIntent.getService(
            context,
            1,
            HidForegroundService.stopIntent(context),
            pendingIntentFlagsImmutable(),
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.notification_action_disconnect),
                disconnectIntent,
            )
            .build()
    }

    fun update(context: Context, state: HidLinkState) {
        val notification = build(context, state)
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun pendingIntentFlagsImmutable(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
    }
}
