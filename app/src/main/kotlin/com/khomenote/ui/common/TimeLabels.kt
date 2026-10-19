package com.khomenote.ui.common

/** 24시간제 (시, 분)을 "오전 8:00", "오후 9:30" 형태로. 0시는 "오전 12:00", 12시는 "오후 12:00". */
fun formatReminderTime(hour: Int, minute: Int): String {
    val period = if (hour < 12) "오전" else "오후"
    val hour12 = if (hour % 12 == 0) 12 else hour % 12
    return "$period $hour12:${minute.toString().padStart(2, '0')}"
}
