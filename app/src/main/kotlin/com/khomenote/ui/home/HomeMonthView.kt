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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khomenote.ui.common.AppIcons
import com.khomenote.ui.theme.CardBorder
import com.khomenote.ui.theme.NumberFontFamily
import com.khomenote.ui.theme.OnceTagBorder
import com.khomenote.ui.theme.OneTimeYellowRing
import com.khomenote.ui.theme.OneTimeYellowRingLight
import com.khomenote.ui.theme.RowDivider
import com.khomenote.ui.theme.SurfaceWhite
import com.khomenote.ui.theme.TealContainer
import com.khomenote.ui.theme.TealPrimary
import com.khomenote.ui.theme.TextFaint
import com.khomenote.ui.theme.TextPrimary
import com.khomenote.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val URGENT_DOT_COLOR = Color(0xFFE0702A)
private val HISTORY_RING_COLOR = Color(0xFF7FA8AB)
private val PAST_DAY_COLOR = Color(0xFFA3AAAB)
private val WEEKDAY_LABELS = listOf("일", "월", "화", "수", "목", "금", "토")

/**
 * 홈의 월간 달력 보기(시안 MonthView.dc.html). 날짜 밑 **채운 점**은 그날이 다음 예정일인 작업 — 지남·알림 기간은 주황, 그 외 청록.
 * **속이 빈 작은 원**은 그날 남긴 지난 이력(주기성작업의 교체/구매 기록, 끝낸 일회성작업)이다.
 * 날짜를 누르면 아래에 그날 예정 작업이 홈 목록과 같은 행으로, 그 아래에 "한 일"(지난 이력)이 나온다.
 * 예정 데이터는 홈 목록과 같은 "다음 예정일"뿐이라 앞으로 달의 매달/매년 반복 날짜까지 미리 계산해 보여주지는 않는다.
 * 보는 달·선택한 날([position])은 ViewModel이 들고 있다 — 한 일 줄을 눌러 상세에 갔다 돌아와도 그대로다.
 */
@Composable
internal fun HomeMonthView(
    items: List<HomeRowUiModel>,
    history: List<HistoryRowUiModel>,
    position: CalendarPosition,
    onMoveMonth: (deltaMonths: Long) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onRowClick: (HomeRowUiModel) -> Unit,
    onRowLongClick: (HomeRowUiModel) -> (() -> Unit)?,
    onHistoryClick: (HistoryRowUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    val visibleMonth = position.month
    val selected = position.selected
    val itemsByDate = remember(items) { items.groupBy { it.dueDate } }
    val historyByDate = remember(history) { history.groupBy { it.date } }
    val cells = remember(visibleMonth) { monthCells(visibleMonth) }

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
                IconButton(onClick = { onMoveMonth(-1) }, modifier = Modifier.size(44.dp)) {
                    Icon(AppIcons.ChevronLeft, contentDescription = "이전 달", tint = TealPrimary, modifier = Modifier.size(20.dp))
                }
                Text(
                    monthTitle(visibleMonth),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = { onMoveMonth(1) }, modifier = Modifier.size(44.dp)) {
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
                        val rowsOfCell = date?.let { itemsByDate[it] }.orEmpty()
                        DayCell(
                            date = date,
                            today = today,
                            isSelected = date != null && date == selected,
                            markers = dayMarkers(
                                bands = rowsOfCell.map { dueBandFor(it.daysUntilDue, it.reminderLeadDays) },
                                historyCount = date?.let { historyByDate[it]?.size } ?: 0,
                            ),
                            hasOneTime = rowsOfCell.any { it is HomeRowUiModel.OneTime },
                            hasOneTimeDone = date?.let { d -> historyByDate[d].orEmpty().any { it.kind == HistoryKind.ONE_TIME } } ?: false,
                            onClick = { date?.let(onSelectDate) },
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
        val historyOfDay = historyByDate[selected].orEmpty()
        val rowsShape = RoundedCornerShape(18.dp)
        if (rowsOfDay.isEmpty() && historyOfDay.isEmpty()) {
            Text("이 날은 예정도 기록도 없어요", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 4.dp))
        }
        if (rowsOfDay.isNotEmpty()) {
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
        if (historyOfDay.isNotEmpty()) {
            Text(
                "한 일",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TealPrimary,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(rowsShape)
                    .background(SurfaceWhite)
                    .border(1.dp, CardBorder, rowsShape),
            ) {
                historyOfDay.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider(color = RowDivider)
                    HistoryRow(row = row, onClick = { onHistoryClick(row) })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** 달력 "한 일" 한 줄 — 체크 표시 + "대상 · 작업 이름", 둘째 줄에 가격·구매처. 누르면 그 작업(대상)의 상세 화면으로 간다. */
@Composable
private fun HistoryRow(row: HistoryRowUiModel, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 12.dp, end = 14.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape).background(TealContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Check, contentDescription = "기록함", tint = TealPrimary, modifier = Modifier.size(16.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (row.kind == HistoryKind.ONE_TIME) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "한 번",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TealPrimary,
                        modifier = Modifier
                            .border(1.dp, OnceTagBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
            Text(row.subtitle, fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("›", fontSize = 20.sp, color = TextFaint)
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    today: LocalDate,
    isSelected: Boolean,
    markers: List<DayMarker>,
    hasOneTime: Boolean,
    hasOneTimeDone: Boolean,
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
                        // 예정된 일회성작업이 있는 날은 노란 원 테두리(선택된 날에도 청록 원 둘레에 보인다). 오늘 표시(청록 테두리)보다 우선한다.
                        hasOneTime -> Modifier.border(2.dp, OneTimeYellowRing, CircleShape)
                        // 이미 끝낸 일회성작업만 있는 날은 같은 원을 연한 노란색으로 — 주기성작업 이력만 있는 날에는 원을 두르지 않는다.
                        hasOneTimeDone -> Modifier.border(2.dp, OneTimeYellowRingLight, CircleShape)
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
            markers.forEach { marker ->
                when (marker) {
                    DayMarker.URGENT -> Box(modifier = Modifier.size(6.dp).background(URGENT_DOT_COLOR, CircleShape))
                    DayMarker.UPCOMING -> Box(modifier = Modifier.size(6.dp).background(TealPrimary, CircleShape))
                    // 지난 이력 — 예정(채운 점)과 헷갈리지 않게 속이 빈 작은 원.
                    DayMarker.HISTORY -> Box(modifier = Modifier.size(6.dp).border(1.2.dp, HISTORY_RING_COLOR, CircleShape))
                }
            }
        }
    }
}
