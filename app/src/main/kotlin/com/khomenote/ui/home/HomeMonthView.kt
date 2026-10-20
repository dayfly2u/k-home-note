package com.khomenote.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khomenote.ui.common.AppIcons
import com.khomenote.ui.theme.CardBorder
import com.khomenote.ui.theme.NumberFontFamily
import com.khomenote.ui.theme.OneTimeYellowRing
import com.khomenote.ui.theme.RowDivider
import com.khomenote.ui.theme.SurfaceWhite
import com.khomenote.ui.theme.TealPrimary
import com.khomenote.ui.theme.TextFaint
import com.khomenote.ui.theme.TextPrimary
import com.khomenote.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

private val URGENT_DOT_COLOR = Color(0xFFE0702A)
private val PAST_DAY_COLOR = Color(0xFFA3AAAB)
private val WEEKDAY_LABELS = listOf("일", "월", "화", "수", "목", "금", "토")

/**
 * 홈의 월간 달력 보기(시안 MonthView.dc.html). 날짜 밑 점은 그날이 다음 예정일인 작업 — 지남·알림 기간은 주황, 그 외 청록.
 * 날짜를 누르면 아래에 그날 작업이 홈 목록과 같은 행으로 나온다. 데이터는 홈 목록과 같은 "다음 예정일"뿐이라
 * 앞으로 달의 매달/매년 반복 날짜까지 미리 계산해 보여주지는 않는다.
 */
@Composable
internal fun HomeMonthView(
    items: List<HomeRowUiModel>,
    onRowClick: (HomeRowUiModel) -> Unit,
    onRowLongClick: (HomeRowUiModel) -> (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    var visibleMonth by remember { mutableStateOf(YearMonth.from(today)) }
    var selected by remember { mutableStateOf(today) }
    val itemsByDate = remember(items) { items.groupBy { it.dueDate } }
    val cells = remember(visibleMonth) { monthCells(visibleMonth) }

    fun moveMonth(delta: Long) {
        visibleMonth = visibleMonth.plusMonths(delta)
        selected = if (visibleMonth == YearMonth.from(today)) today else visibleMonth.atDay(1)
    }

    val cardShape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(SurfaceWhite)
                .border(1.dp, CardBorder, cardShape)
                .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { moveMonth(-1) }, modifier = Modifier.size(44.dp)) {
                    Icon(AppIcons.ChevronLeft, contentDescription = "이전 달", tint = TealPrimary, modifier = Modifier.size(20.dp))
                }
                Text(
                    monthTitle(visibleMonth),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = { moveMonth(1) }, modifier = Modifier.size(44.dp)) {
                    Icon(AppIcons.ChevronRight, contentDescription = "다음 달", tint = TealPrimary, modifier = Modifier.size(20.dp))
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                WEEKDAY_LABELS.forEach { label ->
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextFaint,
                    )
                }
            }
            cells.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        DayCell(
                            date = date,
                            today = today,
                            isSelected = date != null && date == selected,
                            bands = date?.let { itemsByDate[it].orEmpty() }.orEmpty()
                                .map { dueBandFor(it.daysUntilDue, it.reminderLeadDays) },
                            hasOneTime = date?.let { d -> itemsByDate[d].orEmpty().any { it is HomeRowUiModel.OneTime } } ?: false,
                            onClick = { date?.let { selected = it } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(dayTitle(selected), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(relativeDayText(ChronoUnit.DAYS.between(today, selected)), fontSize = 12.sp, color = TextSecondary)
        }
        val rowsOfDay = itemsByDate[selected].orEmpty()
        if (rowsOfDay.isEmpty()) {
            Text("이 날 예정된 작업이 없어요", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 4.dp))
        } else {
            val rowsShape = RoundedCornerShape(18.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(rowsShape)
                    .background(SurfaceWhite)
                    .border(1.dp, CardBorder, rowsShape),
            ) {
                rowsOfDay.forEachIndexed { index, uiModel ->
                    if (index > 0) HorizontalDivider(color = RowDivider)
                    HomeRow(uiModel = uiModel, onClick = { onRowClick(uiModel) }, onLongClick = onRowLongClick(uiModel))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    today: LocalDate,
    isSelected: Boolean,
    bands: List<DueBand>,
    hasOneTime: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (date == null) {
        Box(modifier = modifier.height(50.dp))
        return
    }
    val isToday = date == today
    Column(
        modifier = modifier.height(50.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (isSelected) TealPrimary else Color.Transparent)
                .then(
                    when {
                        // 일회성작업이 있는 날은 노란 원 테두리(선택된 날에도 청록 원 둘레에 보인다). 오늘 표시(청록 테두리)보다 우선한다.
                        hasOneTime -> Modifier.border(2.dp, OneTimeYellowRing, CircleShape)
                        isToday && !isSelected -> Modifier.border(1.5.dp, TealPrimary, CircleShape)
                        else -> Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                fontFamily = NumberFontFamily,
                fontSize = 15.sp,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    isSelected -> Color.White
                    date.isBefore(today) -> PAST_DAY_COLOR
                    else -> TextPrimary
                },
            )
        }
        Row(modifier = Modifier.height(6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            dotUrgencies(bands).forEach { urgent ->
                Box(modifier = Modifier.size(6.dp).background(if (urgent) URGENT_DOT_COLOR else TealPrimary, CircleShape))
            }
        }
    }
}
