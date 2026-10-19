package com.khomenote.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeLabelsTest {

    @Test
    fun `아침 8시는 오전 8시 00분`() {
        assertEquals("오전 8:00", formatReminderTime(8, 0))
    }

    @Test
    fun `자정은 오전 12시`() {
        assertEquals("오전 12:00", formatReminderTime(0, 0))
    }

    @Test
    fun `정오는 오후 12시`() {
        assertEquals("오후 12:00", formatReminderTime(12, 0))
    }

    @Test
    fun `밤 9시 30분`() {
        assertEquals("오후 9:30", formatReminderTime(21, 30))
    }

    @Test
    fun `분은 두 자리로 채운다`() {
        assertEquals("오후 1:05", formatReminderTime(13, 5))
    }
}
