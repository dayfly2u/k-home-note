package com.khomenote.ui.item

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemFormSupportTest {

    @Test
    fun `알림 7일 전은 7일 3일 1일 전 시점을 보여준다`() {
        assertEquals("7일 전, 3일 전, 1일 전 아침 8시에 알림이 와요", reminderExplanation(7, 8))
    }

    @Test
    fun `알림 14일 전은 14 7 3 1일 전 시점을 보여준다`() {
        assertEquals("14일 전, 7일 전, 3일 전, 1일 전 아침 8시에 알림이 와요", reminderExplanation(14, 8))
    }

    @Test
    fun `알림 1일 전은 하루 전 한 번만 보여준다`() {
        assertEquals("1일 전 아침 8시에 알림이 와요", reminderExplanation(1, 8))
    }

    @Test
    fun `알림 0일은 당일 알림이다`() {
        assertEquals("당일 아침 8시에 알림이 와요", reminderExplanation(0, 8))
    }

    @Test
    fun `음력 매년 설명은 양력으로 바꿔준다고 알린다`() {
        assertEquals(
            "음력 1월 3일을 매년 양력으로 바꿔서 알려드려요",
            nextDateNote(CycleMode.YEARLY, isLunar = true, month = 1, day = 3, monthlyDay = null),
        )
    }

    @Test
    fun `양력 매년 설명`() {
        assertEquals(
            "매년 11월 14일에 돌아와요",
            nextDateNote(CycleMode.YEARLY, isLunar = false, month = 11, day = 14, monthlyDay = null),
        )
    }

    @Test
    fun `매달 설명은 말일 조정을 알린다`() {
        val note = nextDateNote(CycleMode.MONTHLY, isLunar = false, month = null, day = null, monthlyDay = 25)
        assertTrue(note.startsWith("매달 25일에 돌아와요"))
    }

    @Test
    fun `빠른 선택 목록은 모두 저장 가능한 값이다`() {
        QUICK_PICKS.forEach { pick ->
            assertTrue(pick.name.isNotBlank())
            assertTrue(pick.reminderLeadDays >= 1)
            when (pick.mode) {
                CycleMode.INTERVAL -> assertTrue("${pick.chipLabel}: 주기 값이 필요해요", pick.cycleValue > 0)
                CycleMode.MONTHLY -> assertTrue("${pick.chipLabel}: 매달 날짜가 필요해요", pick.monthlyDay in 1..31)
                CycleMode.YEARLY -> Unit
            }
        }
    }
}
