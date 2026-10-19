package com.khomenote.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.khomenote.data.local.dto.AssetWithCategory
import com.khomenote.data.local.dto.ConsumableItemWithStatus
import com.khomenote.data.local.dto.RepairRecordWithAsset
import com.khomenote.data.local.entity.CategoryEntity
import com.khomenote.data.repository.AssetRepository
import com.khomenote.data.repository.CategoryRepository
import com.khomenote.data.repository.ConsumableItemRepository
import com.khomenote.data.repository.RepairRecordRepository
import com.khomenote.domain.DueDateCalculator
import com.khomenote.ui.common.KFilterChip
import com.khomenote.ui.common.MoreBelowIndicator
import com.khomenote.ui.theme.CardBorder
import com.khomenote.ui.theme.NumberFontFamily
import com.khomenote.ui.theme.OnceTagBorder
import com.khomenote.ui.theme.OrangeHeaderDot
import com.khomenote.ui.theme.OrangeUrgent
import com.khomenote.ui.theme.OrangeUrgentContainer
import com.khomenote.ui.theme.OrangeUrgentText
import com.khomenote.ui.theme.RowDivider
import com.khomenote.ui.theme.SurfaceWhite
import com.khomenote.ui.theme.TealContainer
import com.khomenote.ui.theme.TealHeaderMuted
import com.khomenote.ui.theme.TealPrimary
import com.khomenote.ui.theme.TextFaint
import com.khomenote.ui.theme.TextSecondary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** 홈 대시보드 한 줄 — 주기성작업(소모품)과 일회성작업(알림 받기를 켠 것만)을 한 목록에 합쳐서 보여준다. */
sealed interface HomeRowUiModel {
    val dueDate: LocalDate
    val daysUntilDue: Long
    val categoryId: Long
    val assetName: String
    val title: String
    val reminderLeadDays: Int

    data class Recurring(
        val status: ConsumableItemWithStatus,
        override val dueDate: LocalDate,
        override val daysUntilDue: Long,
    ) : HomeRowUiModel {
        override val categoryId get() = status.categoryId
        override val assetName get() = status.assetName
        override val title get() = status.item.name
        override val reminderLeadDays get() = status.item.reminderLeadDays
    }

    data class OneTime(
        val record: RepairRecordWithAsset,
        override val dueDate: LocalDate,
        override val daysUntilDue: Long,
    ) : HomeRowUiModel {
        override val categoryId get() = record.categoryId
        override val assetName get() = record.assetName
        override val title get() = record.record.name
        override val reminderLeadDays get() = record.record.reminderLeadDays ?: 0
    }
}

class HomeViewModel(
    private val consumableItemRepository: ConsumableItemRepository,
    private val repairRecordRepository: RepairRecordRepository,
    categoryRepository: CategoryRepository,
    assetRepository: AssetRepository,
) : ViewModel() {

    val categories: StateFlow<List<CategoryEntity>> = categoryRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 헤더 "+" 시트에서 작업을 추가할 대상을 고를 때 쓴다. */
    val assets: StateFlow<List<AssetWithCategory>> = assetRepository.getAllWithCategory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // remember{}로 화면(Composable)에만 두면 항목 클릭 후 뒤로 왔을 때 화면이
    // 다시 조립되며 필터가 초기화된다 — ViewModel에 둬서 화면을 나갔다 와도 유지되게 한다.
    private val _categoryFilter = MutableStateFlow<Long?>(null)
    val categoryFilter: StateFlow<Long?> = _categoryFilter

    fun setCategoryFilter(categoryId: Long?) {
        _categoryFilter.value = categoryId
    }

    // 정렬 기준이 dueDate 오름차순이라, 연체(과거 날짜) 항목이 자연히
    // 맨 앞에 온다 — 화면에서 별도로 "연체는 위로" 정렬 로직을 두지 않아도 된다.
    val items: StateFlow<List<HomeRowUiModel>> = combine(
        consumableItemRepository.getAllWithStatus(),
        repairRecordRepository.getPendingReminders(),
    ) { statuses, oneTimeTasks ->
        val today = LocalDate.now()
        val recurring = statuses.map { status ->
            val nextDueDate = DueDateCalculator.nextDueDate(
                itemCreatedAt = status.item.createdAt,
                cycleDays = status.item.cycleDays,
                latestRecordDate = status.latestRecordDate,
                monthlyDueDay = status.item.monthlyDueDay,
                yearlyDueMonth = status.item.yearlyDueMonth,
                yearlyDueDay = status.item.yearlyDueDay,
                yearlyDueIsLunar = status.item.isYearlyLunar == true,
            )
            HomeRowUiModel.Recurring(
                status = status,
                dueDate = nextDueDate,
                daysUntilDue = DueDateCalculator.daysUntilDue(nextDueDate, today),
            )
        }
        val oneTime = oneTimeTasks.map { withAsset ->
            HomeRowUiModel.OneTime(
                record = withAsset,
                dueDate = withAsset.record.date,
                daysUntilDue = DueDateCalculator.daysUntilDue(withAsset.record.date, today),
            )
        }
        (recurring + oneTime).sortedBy { it.dueDate }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

class HomeViewModelFactory(
    private val consumableItemRepository: ConsumableItemRepository,
    private val repairRecordRepository: RepairRecordRepository,
    private val categoryRepository: CategoryRepository,
    private val assetRepository: AssetRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        HomeViewModel(consumableItemRepository, repairRecordRepository, categoryRepository, assetRepository) as T
}

internal data class DDayStyle(val text: String, val background: Color, val textColor: Color, val large: Boolean)

// 생활달력 리디자인(2026-10-18 redesign-brief.md 1장): 색을 청록 한 가지 + 급한 것만 주황으로 줄였다.
// 지난 날짜(연체)는 주황 바탕에 흰 글자로 "D+N". 그 외는 남은 날짜에 따라 소모품별 알림 설정일
// (reminderLeadDays) 이내는 연한주황, 31일 이하는 연한청록, 100일 이하/그 뒤는 바탕 없이 보조 글자색만
// 두 단계로 흐리게 — 급하지 않은 구간은 눈에 크게 띌 이유가 없어 폰트를 작게 둔다.
internal fun dDayStyle(daysUntilDue: Long, reminderLeadDays: Int): DDayStyle {
    val overdueText = if (daysUntilDue == 0L) "오늘" else "D-$daysUntilDue"
    return when (dueBandFor(daysUntilDue, reminderLeadDays)) {
        DueBand.OVERDUE -> DDayStyle("D+${-daysUntilDue}", OrangeUrgent, Color.White, large = true)
        DueBand.DUE_SOON -> DDayStyle(overdueText, OrangeUrgentContainer, OrangeUrgentText, large = true)
        DueBand.WITHIN_MONTH -> DDayStyle("D-$daysUntilDue", TealContainer, TealPrimary, large = true)
        DueBand.WITHIN_3_MONTHS -> DDayStyle("D-$daysUntilDue", Color.Transparent, TextSecondary, large = false)
        DueBand.LATER -> DDayStyle("D-$daysUntilDue", Color.Transparent, TextFaint, large = false)
    }
}

private val HEADER_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일")

@Composable
private fun HomeHeader(summary: HomeSummaryCounts, onAddClick: () -> Unit) {
    val today = remember { LocalDate.now() }
    val weekday = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
    // 앱이 targetSdk 36이라 상태바 뒤까지 그려지는데, 바깥 Scaffold가 이 화면에는 상태바 높이를 패딩으로 주지 않는다
    // (AppRoot 참고) — 청록 바탕이 상태바 영역까지 덮고, 그 위 흰 상태바 아이콘이 잘 보이도록 헤더가 직접 상태바 높이만큼 내려앉는다.
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TealPrimary, RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
            .padding(top = statusBarTop)
            .padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("생활달력", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TealHeaderMuted)
                Text(
                    "${today.format(HEADER_DATE_FORMAT)} $weekday",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(1.dp, HEADER_BUTTON_BORDER, CircleShape)
                    .clickable(onClick = onAddClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Add, contentDescription = "추가", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            HomeSummaryTile("지났어요", summary.overdue, Modifier.weight(1f), showDot = summary.overdue > 0)
            HomeSummaryTile("챙길 때", summary.dueSoon, Modifier.weight(1f), leftDivider = true)
            HomeSummaryTile("한 달 안", summary.withinMonth, Modifier.weight(1f), leftDivider = true)
        }
    }
}

private val HEADER_BUTTON_BORDER = Color(0xFF4F8C90)
private val HEADER_DIVIDER = Color(0xFF2E7277)

@Composable
private fun HomeSummaryTile(
    label: String,
    count: Int,
    modifier: Modifier = Modifier,
    showDot: Boolean = false,
    leftDivider: Boolean = false,
) {
    Column(
        modifier = modifier
            .then(
                if (leftDivider) {
                    Modifier.drawBehind {
                        drawLine(HEADER_DIVIDER, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1.dp.toPx())
                    }.padding(start = 16.dp)
                } else {
                    Modifier
                },
            ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "$count",
                fontSize = 28.sp,
                lineHeight = 28.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontFamily = NumberFontFamily,
            )
            if (showDot) {
                Box(modifier = Modifier.size(8.dp).background(OrangeHeaderDot, CircleShape))
            }
        }
        Text(label, fontSize = 12.sp, color = TealHeaderMuted)
    }
}

@Composable
private fun HomeRow(uiModel: HomeRowUiModel, onClick: () -> Unit) {
    val subtitle = when (uiModel) {
        is HomeRowUiModel.Recurring -> formatRecurringSubtitle(uiModel.status.item, uiModel.status.latestRecordDate)
        is HomeRowUiModel.OneTime -> formatOneTimeSubtitle(uiModel.record.record)
    }
    val dStyle = dDayStyle(uiModel.daysUntilDue, uiModel.reminderLeadDays)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${uiModel.assetName} · ${uiModel.title}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (uiModel is HomeRowUiModel.OneTime) {
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
            Text(subtitle, fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            modifier = Modifier
                .widthIn(min = 58.dp)
                .height(36.dp)
                .background(dStyle.background, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                dStyle.text,
                color = dStyle.textColor,
                fontWeight = FontWeight.Bold,
                fontFamily = NumberFontFamily,
                fontSize = if (dStyle.large) 18.sp else 15.sp,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onItemClick: (Long) -> Unit,
    onOneTimeTaskClick: (Long) -> Unit,
    onAddAsset: () -> Unit,
    onAddRecurringItem: (Long) -> Unit,
    onAddOneTimeTask: (Long) -> Unit,
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.categoryFilter.collectAsStateWithLifecycle()
    val assets by viewModel.assets.collectAsStateWithLifecycle()
    var showAddSheet by remember { mutableStateOf(false) }

    val filteredItems = remember(items, categoryFilter) {
        items.filter { categoryFilter == null || it.categoryId == categoryFilter }
    }
    val listState = rememberLazyListState()

    if (showAddSheet) {
        HomeAddSheet(
            assets = assets,
            onDismiss = { showAddSheet = false },
            onAddAsset = { showAddSheet = false; onAddAsset() },
            onAddRecurring = { assetId -> showAddSheet = false; onAddRecurringItem(assetId) },
            onAddOneTime = { assetId -> showAddSheet = false; onAddOneTimeTask(assetId) },
        )
    }

    Scaffold(
        topBar = {
            HomeHeader(
                summary = items.summaryCounts(),
                onAddClick = { showAddSheet = true },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KFilterChip(
                    selected = categoryFilter == null,
                    label = "전체",
                    onClick = { viewModel.setCategoryFilter(null) },
                )
                categories.forEach { category ->
                    KFilterChip(
                        selected = categoryFilter == category.id,
                        label = "${category.emoji} ${category.name}",
                        onClick = { viewModel.setCategoryFilter(category.id) },
                    )
                }
            }
            if (filteredItems.isEmpty()) {
                Text(
                    "등록된 작업이 없습니다. 오른쪽 위 \"+\" 버튼으로 주기성작업/일회성작업을 추가하세요.",
                    modifier = Modifier.padding(16.dp),
                )
                return@Scaffold
            }
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                ) {
                    filteredItems.groupedBySection().forEach { (band, rowsInBand) ->
                        item(key = "section-${band.name}") {
                            Text(
                                band.sectionTitle,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TealPrimary,
                                modifier = Modifier.padding(start = 4.dp, top = 12.dp, end = 4.dp, bottom = 4.dp),
                            )
                        }
                        item(key = "card-${band.name}") {
                            val cardShape = RoundedCornerShape(18.dp)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(cardShape)
                                    .background(SurfaceWhite)
                                    .border(1.dp, CardBorder, cardShape),
                            ) {
                                rowsInBand.forEachIndexed { index, uiModel ->
                                    if (index > 0) HorizontalDivider(color = RowDivider)
                                    HomeRow(uiModel) {
                                        when (uiModel) {
                                            is HomeRowUiModel.Recurring -> onItemClick(uiModel.status.item.id)
                                            is HomeRowUiModel.OneTime -> onOneTimeTaskClick(uiModel.record.record.assetId)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item(key = "bottom-space") { Spacer(Modifier.height(16.dp)) }
                }
                MoreBelowIndicator(
                    listState = listState,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                )
            }
        }
    }
}
