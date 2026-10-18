package com.khomenote.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSummaryTest {

    @Test
    fun `dueBandFor returns OVERDUE when days until due is negative`() {
        assertEquals(DueBand.OVERDUE, dueBandFor(daysUntilDue = -1, reminderLeadDays = 7))
    }

    @Test
    fun `dueBandFor returns DUE_SOON on the due day itself`() {
        assertEquals(DueBand.DUE_SOON, dueBandFor(daysUntilDue = 0, reminderLeadDays = 7))
    }

    @Test
    fun `dueBandFor returns DUE_SOON when within the item's own reminder lead days`() {
        assertEquals(DueBand.DUE_SOON, dueBandFor(daysUntilDue = 7, reminderLeadDays = 7))
    }

    @Test
    fun `dueBandFor returns WITHIN_MONTH just past the reminder lead days but within 31 days`() {
        assertEquals(DueBand.WITHIN_MONTH, dueBandFor(daysUntilDue = 8, reminderLeadDays = 7))
        assertEquals(DueBand.WITHIN_MONTH, dueBandFor(daysUntilDue = 31, reminderLeadDays = 7))
    }

    @Test
    fun `dueBandFor returns WITHIN_3_MONTHS between 32 and 100 days`() {
        assertEquals(DueBand.WITHIN_3_MONTHS, dueBandFor(daysUntilDue = 32, reminderLeadDays = 7))
        assertEquals(DueBand.WITHIN_3_MONTHS, dueBandFor(daysUntilDue = 100, reminderLeadDays = 7))
    }

    @Test
    fun `dueBandFor returns LATER beyond 100 days`() {
        assertEquals(DueBand.LATER, dueBandFor(daysUntilDue = 101, reminderLeadDays = 7))
    }

    @Test
    fun `dueBandFor with a long reminder lead days can cover the whole WITHIN_MONTH range`() {
        // 소모품의 reminderLeadDays가 31보다 크면(예: 40일 전 알림), DUE_SOON이 WITHIN_MONTH보다 먼저 와야 한다.
        assertEquals(DueBand.DUE_SOON, dueBandFor(daysUntilDue = 40, reminderLeadDays = 40))
    }
}
