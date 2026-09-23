package com.khomenote.notification

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderSchedulerTest {
    private val zone = ZoneId.of("Asia/Seoul")

    @Test
    fun `nextRunAt returns today when target time has not passed yet`() {
        val now = ZonedDateTime.of(2026, 9, 23, 7, 30, 0, 0, zone)
        val result = ReminderScheduler.nextRunAt(now, hour = 8, minute = 0)
        assertEquals(ZonedDateTime.of(2026, 9, 23, 8, 0, 0, 0, zone), result)
    }

    @Test
    fun `nextRunAt rolls over to tomorrow when target time already passed today`() {
        val now = ZonedDateTime.of(2026, 9, 23, 8, 30, 0, 0, zone)
        val result = ReminderScheduler.nextRunAt(now, hour = 8, minute = 0)
        assertEquals(ZonedDateTime.of(2026, 9, 24, 8, 0, 0, 0, zone), result)
    }

    @Test
    fun `nextRunAt rolls over to tomorrow when now is exactly the target time`() {
        val now = ZonedDateTime.of(2026, 9, 23, 8, 0, 0, 0, zone)
        val result = ReminderScheduler.nextRunAt(now, hour = 8, minute = 0)
        assertEquals(ZonedDateTime.of(2026, 9, 24, 8, 0, 0, 0, zone), result)
    }
}
