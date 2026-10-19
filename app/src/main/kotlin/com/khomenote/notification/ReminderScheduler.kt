package com.khomenote.notification

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * "대략 24시간마다"가 아니라 "매일 이 시각(기본 08:00 KST)에 최대한 정확히" 실행되도록,
 * 매 실행이 끝날 때마다 다음 목표 시각까지의 지연을 다시 계산해 OneTimeWorkRequest를
 * 재예약한다 — PeriodicWorkRequest는 배터리 최적화로 실행이 늦춰지면 그 지연이 다음
 * 주기에도 누적될 수 있다. workmanager-daily-schedule skill 참고.
 */
object ReminderScheduler {
    private const val UNIQUE_WORK_NAME = "reminder_daily_check"
    private const val DEFAULT_HOUR = 8
    private const val DEFAULT_MINUTE = 0
    private val ZONE: ZoneId = ZoneId.of("Asia/Seoul") // 기기 타임존에 기대지 않고 명시

    // 이 앱은 원래 같은 이름(UNIQUE_WORK_NAME)으로 PeriodicWorkRequest(24시간 간격)를 썼다.
    // ensureScheduled가 KEEP을 쓰기 때문에, 이미 설치되어 있던 기기에서는 그 예전 주기적 작업이
    // 영원히 그대로 남아 새 정시(OneTimeWorkRequest) 방식으로 절대 안 넘어간다 — 최초 1회만
    // 강제로 취소하고 새로 예약해서 마이그레이션한다.
    private const val PREFS_NAME = "reminder_scheduler_prefs"
    private const val KEY_MIGRATED_TO_ONE_TIME = "migrated_to_one_time_v1"
    private const val KEY_HOUR = "reminder_hour"
    private const val KEY_MINUTE = "reminder_minute"

    /** 설정 화면에서 고른 "매일 알림 확인 시각" (시, 분). 아직 안 골랐으면 기본 08:00. */
    fun getTime(context: Context): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_HOUR, DEFAULT_HOUR) to prefs.getInt(KEY_MINUTE, DEFAULT_MINUTE)
    }

    /** 확인 시각을 저장하고 곧바로 그 시각으로 다시 예약한다(이미 예약된 작업은 REPLACE). */
    fun setTime(context: Context, hour: Int, minute: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_HOUR, hour)
            .putInt(KEY_MINUTE, minute)
            .apply()
        enqueue(context, hour, minute, ExistingWorkPolicy.REPLACE)
    }

    /** 앱 시작 시 호출. 이미 예약된 작업이 있으면 건드리지 않는다(KEEP) — 단, 예전 방식에서
     * 아직 마이그레이션 안 됐으면 최초 1회 강제로 다시 예약한다. */
    fun ensureScheduled(context: Context, hour: Int = getTime(context).first, minute: Int = getTime(context).second) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_MIGRATED_TO_ONE_TIME, false)) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            prefs.edit().putBoolean(KEY_MIGRATED_TO_ONE_TIME, true).apply()
        }
        enqueue(context, hour, minute, ExistingWorkPolicy.KEEP)
    }

    /** 워커 실행이 끝난 직후(성공/실패 무관) 호출. 다음 목표 시각을 다시 계산해 예약을 REPLACE한다. */
    fun scheduleNext(context: Context, hour: Int = getTime(context).first, minute: Int = getTime(context).second) {
        enqueue(context, hour, minute, ExistingWorkPolicy.REPLACE)
    }

    private fun enqueue(context: Context, hour: Int, minute: Int, policy: ExistingWorkPolicy) {
        val now = ZonedDateTime.now(ZONE)
        val nextRun = nextRunAt(now, hour, minute)
        val delayMillis = Duration.between(now, nextRun).toMillis()

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, policy, request)
    }

    /** 다음 hour:minute 실행 시각을 계산한다. internal로 열어둬서 순수 함수 단위 테스트가 가능하게 한다. */
    internal fun nextRunAt(now: ZonedDateTime, hour: Int, minute: Int): ZonedDateTime {
        var nextRun = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!nextRun.isAfter(now)) nextRun = nextRun.plusDays(1)
        return nextRun
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}
