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
private const val EXTRA_ONE_TIME_TASK_ASSET_ID = "oneTimeTaskAssetId"

// 일회성작업(repair_records) 알림은 consumable_items와 전혀 다른 기본키 체계라, 같은 양의 정수 ID를
// 그대로 쓰면 PendingIntent/notify()의 고유 ID가 서로 겹칠 수 있다 — 음수로 뒤집어서 네임스페이스를 분리한다.
private fun oneTimeNotificationId(repairRecordId: Long): Int = -repairRecordId.toInt()

fun createReminderChannel(context: Context) {
    val channel = NotificationChannel(
        REMINDER_CHANNEL_ID,
        "교체 알림",
        NotificationManager.IMPORTANCE_DEFAULT,
    ).apply { description = "소모품 교체·일회성작업 예정일 알림" }
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(channel)
}

/** 소모품(주기성작업) 알림 — 탭하면 그 소모품 상세 화면으로 이동한다. */
fun showReminderNotification(context: Context, consumableItemId: Long, title: String, contentText: String): Boolean {
    val intent = Intent(context, MainActivity::class.java).apply {
        putExtra(EXTRA_CONSUMABLE_ITEM_ID, consumableItemId)
    }
    return showReminderNotification(context, notificationId = consumableItemId.toInt(), intent = intent, title = title, contentText = contentText)
}

/** 일회성작업 알림 — 탭하면 그 작업이 속한 자산 상세 화면으로 이동한다(별도 상세 화면이 없어서). */
fun showOneTimeTaskReminderNotification(
    context: Context,
    repairRecordId: Long,
    assetId: Long,
    title: String,
    contentText: String,
): Boolean {
    val intent = Intent(context, MainActivity::class.java).apply {
        putExtra(EXTRA_ONE_TIME_TASK_ASSET_ID, assetId)
    }
    return showReminderNotification(context, notificationId = oneTimeNotificationId(repairRecordId), intent = intent, title = title, contentText = contentText)
}

private fun showReminderNotification(context: Context, notificationId: Int, intent: Intent, title: String, contentText: String): Boolean {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED
    ) {
        return false
    }
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    val pendingIntent = PendingIntent.getActivity(
        context,
        notificationId,
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
    manager.notify(notificationId, notification)
    return true
}

fun consumableItemIdFromIntent(intent: Intent?): Long? =
    intent?.getLongExtra(EXTRA_CONSUMABLE_ITEM_ID, -1L)?.takeIf { it != -1L }

fun consumeConsumableItemIdFromIntent(intent: Intent): Long? =
    consumableItemIdFromIntent(intent)?.also { intent.removeExtra(EXTRA_CONSUMABLE_ITEM_ID) }

fun oneTimeTaskAssetIdFromIntent(intent: Intent?): Long? =
    intent?.getLongExtra(EXTRA_ONE_TIME_TASK_ASSET_ID, -1L)?.takeIf { it != -1L }

fun consumeOneTimeTaskAssetIdFromIntent(intent: Intent): Long? =
    oneTimeTaskAssetIdFromIntent(intent)?.also { intent.removeExtra(EXTRA_ONE_TIME_TASK_ASSET_ID) }
