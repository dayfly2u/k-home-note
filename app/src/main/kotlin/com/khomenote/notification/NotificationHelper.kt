package com.khomenote.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.khomenote.MainActivity
import com.khomenote.R

const val REMINDER_CHANNEL_ID = "reminder"
private const val EXTRA_CONSUMABLE_ITEM_ID = "consumableItemId"

fun createReminderChannel(context: Context) {
    val channel = NotificationChannel(
        REMINDER_CHANNEL_ID,
        "교체 알림",
        NotificationManager.IMPORTANCE_DEFAULT,
    ).apply { description = "소모품 교체 예정일 알림" }
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(channel)
}

fun showReminderNotification(context: Context, consumableItemId: Long, title: String, contentText: String) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_CONSUMABLE_ITEM_ID, consumableItemId)
    }
    val pendingIntent = PendingIntent.getActivity(
        context,
        consumableItemId.toInt(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(title)
        .setContentText(contentText)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.notify(consumableItemId.toInt(), notification)
}

fun consumableItemIdFromIntent(intent: Intent?): Long? =
    intent?.getLongExtra(EXTRA_CONSUMABLE_ITEM_ID, -1L)?.takeIf { it != -1L }

fun consumeConsumableItemIdFromIntent(intent: Intent): Long? =
    consumableItemIdFromIntent(intent)?.also { intent.removeExtra(EXTRA_CONSUMABLE_ITEM_ID) }
