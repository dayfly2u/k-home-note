package com.khomenote.ui.item

import com.khomenote.domain.ReminderTargetSelector

/** 주기성작업 추가 화면 맨 위 "빠른 선택" 칩 — 누르면 이름·주기·알림 기본값을 채운다. 날짜가 null이면 그 칸은 건드리지 않는다. */
internal data class QuickPick(
    val chipLabel: String,
    val name: String,
    val mode: CycleMode,
    val cycleValue: Int = 0,
    val cycleUnit: CycleUnit = CycleUnit.MONTHS,
    val monthlyDay: Int? = null,
    val yearlyMonth: Int? = null,
    val yearlyDay: Int? = null,
    val reminderLeadDays: Int = 7,
)

internal val QUICK_PICKS = listOf(
    QuickPick("생일 · 매년", "생일", CycleMode.YEARLY, reminderLeadDays = 7),
    QuickPick("카드 실적 · 매달", "카드 실적 확인", CycleMode.MONTHLY, monthlyDay = 25, reminderLeadDays = 3),
    QuickPick("엔진오일 · 6개월", "엔진오일 교체", CycleMode.INTERVAL, cycleValue = 6, cycleUnit = CycleUnit.MONTHS, reminderLeadDays = 14),
    QuickPick("정수기 필터 · 4개월", "정수기 필터 교체", CycleMode.INTERVAL, cycleValue = 4, cycleUnit = CycleUnit.MONTHS, reminderLeadDays = 7),
    QuickPick("에어컨 필터 · 1년", "에어컨 필터 교체", CycleMode.INTERVAL, cycleValue = 1, cycleUnit = CycleUnit.YEARS, reminderLeadDays = 14),
    QuickPick("자동차보험 · 매년", "자동차보험 갱신", CycleMode.YEARLY, reminderLeadDays = 30),
)

/** "7일 전, 3일 전, 1일 전 아침 8시에 알림이 와요" — 실제 알림 시점(ReminderTargetSelector)을 그대로 보여준다. */
internal fun reminderExplanation(leadDays: Int, hour: Int): String {
    if (leadDays <= 0) return "당일 아침 ${hour}시에 알림이 와요"
    val points = ReminderTargetSelector.reminderMilestones(leadDays).joinToString(", ") { "${it}일 전" }
    return "$points 아침 ${hour}시에 알림이 와요"
}

/** 다음 날짜 미리보기 카드의 보조 설명 한 줄. */
internal fun nextDateNote(mode: CycleMode, isLunar: Boolean, month: Int?, day: Int?, monthlyDay: Int?): String = when (mode) {
    CycleMode.YEARLY ->
        if (isLunar) "음력 ${month}월 ${day}일을 매년 양력으로 바꿔서 알려드려요" else "매년 ${month}월 ${day}일에 돌아와요"
    CycleMode.MONTHLY -> "매달 ${monthlyDay}일에 돌아와요. 말일이 없는 달은 그 달의 마지막 날이에요"
    CycleMode.INTERVAL -> "완료 기록을 남기면 그 날부터 다시 세요"
}
