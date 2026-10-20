package com.khomenote.ui.home

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.khomenote.data.local.entity.RepairRecordEntity
import com.khomenote.data.local.entity.ReplacementRecordEntity
import com.khomenote.data.repository.AssetRepository
import com.khomenote.data.repository.CategoryRepository
import com.khomenote.data.repository.ConsumableItemRepository
import com.khomenote.data.repository.RepairRecordRepository
import com.khomenote.data.repository.ReplacementRecordRepository
import com.khomenote.domain.DueDateCalculator
import com.khomenote.photo.PhotoStorage
import com.khomenote.ui.asset.OneTimeDoneSheet
import com.khomenote.ui.common.AppIcons
import com.khomenote.ui.item.ReplacementRecordSheet
import com.khomenote.ui.common.KFilterChip
import com.khomenote.ui.common.MoreBelowIndicator
import com.khomenote.ui.theme.CardBorder
import com.khomenote.ui.theme.NumberFontFamily
import com.khomenote.ui.theme.OnceTagBorder
import com.khomenote.ui.theme.OneTimeYellow
import com.khomenote.ui.theme.OrangeHeaderDot
import com.khomenote.ui.theme.OrangeUrgent
import com.khomenote.ui.theme.OrangeUrgentContainer
import com.khomenote.ui.theme.OrangeUrgentText
import com.khomenote.ui.theme.RowDivider
import com.khomenote.ui.theme.SurfaceWhite
import com.khomenote.ui.theme.TealContainer
import com.khomenote.ui.theme.TealHeaderMuted
import com.khomenote.ui.theme.TealPrimary
import com.khomenote.ui.theme.TealPrimaryDark
import com.khomenote.ui.theme.TextFaint
import com.khomenote.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    private val replacementRecordRepository: ReplacementRecordRepository,
    private val photoStorage: PhotoStorage,
) : ViewModel() {

    /** 홈에서 바로 "완료 기록"을 남길 때, 다음 예정일 미리보기에 쓸 그 작업의 기존 기록 날짜들. */
    suspend fun recordDatesFor(itemId: Long): List<LocalDate> =
        replacementRecordRepository.getByItem(itemId).first().map { it.date }

    /** 상세 화면의 "완료 기록하기"와 같은 저장 경로. */
    fun recordReplacement(itemId: Long, date: LocalDate, price: Long?, vendor: String?, photoUri: String?, memo: String?) {
        viewModelScope.launch {
            replacementRecordRepository.add(
                ReplacementRecordEntity(
                    consumableItemId = itemId,
                    date = date,
                    price = price,
                    vendor = vendor,
                    photoUri = photoUri,
                    memo = memo,
                )
            )
        }
    }

    suspend fun pickPhoto(uri: Uri): String? = photoStorage.copyToInternalStorage(uri)

    fun discardPhoto(path: String) {
        viewModelScope.launch(Dispatchers.IO) { photoStorage.deletePhoto(path) }
    }


    val categories: StateFlow<List<CategoryEntity>> = categoryRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 헤더 "+" 시트에서 작업을 추가할 대상을 고를 때 쓴다. */
    val assets: StateFlow<List<AssetWithCategory>> = assetRepository.getAllWithCategory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 일회성작업 "완료로 표시" — 한 날·가격·메모를 반영하고 알림을 꺼서(reminderLeadDays = null) 홈에서 뺀다. 기록은 대상 상세에 남는다. */
    fun completeOneTime(record: RepairRecordEntity, date: LocalDate, price: Long?, memo: String?) {
        viewModelScope.launch {
            repairRecordRepository.save(record.copy(date = date, price = price, memo = memo, reminderLeadDays = null))
        }
    }

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
    private val replacementRecordRepository: ReplacementRecordRepository,
    private val photoStorage: PhotoStorage,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        HomeViewModel(
            consumableItemRepository, repairRecordRepository, categoryRepository, assetRepository,
            replacementRecordRepository, photoStorage,
        ) as T
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
private fun HomeHeader(
    summary: HomeSummaryCounts,
    viewMode: HomeViewMode,
    onViewModeChange: (HomeViewMode) -> Unit,
    onSummaryClick: (DueBand) -> Unit,
    onAddClick: () -> Unit,
) {
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
            .padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = if (viewMode == HomeViewMode.LIST) 16.dp else 14.dp),
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
            ViewModeToggle(viewMode, onViewModeChange)
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
        // 시안(MonthView.dc.html)의 달력 보기 헤더에는 요약 3칸이 없다.
        if (viewMode == HomeViewMode.LIST) {
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                HomeSummaryTile(
                    "지났어요", summary.overdue, { onSummaryClick(DueBand.OVERDUE) },
                    Modifier.weight(1f), showDot = summary.overdue > 0,
                )
                HomeSummaryTile(
                    "챙길 때", summary.dueSoon, { onSummaryClick(DueBand.DUE_SOON) },
                    Modifier.weight(1f), leftDivider = true,
                )
                HomeSummaryTile(
                    "한 달 안", summary.withinMonth, { onSummaryClick(DueBand.WITHIN_MONTH) },
                    Modifier.weight(1f), leftDivider = true,
                )
            }
        }
    }
}

/** 헤더 오른쪽의 목록 / 달력 전환 — 선택된 쪽만 흰 바탕. */
@Composable
private fun ViewModeToggle(viewMode: HomeViewMode, onChange: (HomeViewMode) -> Unit) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(22.dp)).background(TealPrimaryDark).padding(3.dp),
    ) {
        listOf(
            Triple(HomeViewMode.LIST, AppIcons.ViewList, "목록"),
            Triple(HomeViewMode.CALENDAR, AppIcons.ViewCalendar, "달력"),
        ).forEach { (mode, icon, description) ->
            val selected = viewMode == mode
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(if (selected) Color.White else Color.Transparent)
                    .clickable { onChange(mode) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = description,
                    tint = if (selected) TealPrimary else TealHeaderMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private val HEADER_BUTTON_BORDER = Color(0xFF4F8C90)
private val HEADER_DIVIDER = Color(0xFF2E7277)

@Composable
private fun HomeSummaryTile(
    label: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDot: Boolean = false,
    leftDivider: Boolean = false,
) {
    Column(
        modifier = modifier
            // 누르면 그 구간의 목록 위치로 스크롤한다 — 클릭 영역은 칸 전체(세로선·여백 포함).
            .clickable(onClick = onClick)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeRow(uiModel: HomeRowUiModel, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val subtitle = when (uiModel) {
        is HomeRowUiModel.Recurring -> formatRecurringSubtitle(uiModel.status.item, uiModel.status.latestRecordDate)
        is HomeRowUiModel.OneTime -> formatOneTimeSubtitle(uiModel.record.record)
    }
    val dStyle = dDayStyle(uiModel.daysUntilDue, uiModel.reminderLeadDays)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 일회성작업은 연한노랑 바탕으로 주기성작업과 구분한다.
            .background(if (uiModel is HomeRowUiModel.OneTime) OneTimeYellow else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
    onEditRecurringItem: (Long) -> Unit,
    onAddAsset: () -> Unit,
    onAddRecurringItem: (Long) -> Unit,
    onAddOneTimeTask: (Long) -> Unit,
    onConvertToRecurring: (assetId: Long, name: String) -> Unit,
    reopenAddSheetForAsset: Long?,
    onReopenHandled: () -> Unit,
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.categoryFilter.collectAsStateWithLifecycle()
    val assets by viewModel.assets.collectAsStateWithLifecycle()
    var showAddSheet by remember { mutableStateOf(false) }
    var addSheetInitialAssetId by remember { mutableStateOf<Long?>(null) }

    // 시트의 "새 대상 만들기"로 대상을 만들고 돌아왔으면 그 대상이 골라진 채로 시트를 다시 연다.
    LaunchedEffect(reopenAddSheetForAsset) {
        if (reopenAddSheetForAsset != null) {
            addSheetInitialAssetId = reopenAddSheetForAsset
            showAddSheet = true
            onReopenHandled()
        }
    }
    var completingOneTime by remember { mutableStateOf<HomeRowUiModel.OneTime?>(null) }
    var recordingRecurring by remember { mutableStateOf<HomeRowUiModel.Recurring?>(null) }
    var recordingOtherDates by remember { mutableStateOf<List<LocalDate>?>(null) }
    LaunchedEffect(recordingRecurring) {
        val target = recordingRecurring
        recordingOtherDates = if (target == null) null else viewModel.recordDatesFor(target.status.item.id)
    }

    // 목록/달력 보기 선택은 앱을 껐다 켜도 유지한다(DB가 아니라 가벼운 설정값이라 SharedPreferences).
    val context = LocalContext.current
    val homePrefs = remember { context.getSharedPreferences("home_prefs", Context.MODE_PRIVATE) }
    var viewMode by remember {
        mutableStateOf(
            runCatching { HomeViewMode.valueOf(homePrefs.getString("view_mode", null) ?: "LIST") }.getOrDefault(HomeViewMode.LIST),
        )
    }
    fun changeViewMode(mode: HomeViewMode) {
        viewMode = mode
        homePrefs.edit().putString("view_mode", mode.name).apply()
    }

    // 목록과 달력 보기가 같은 행 동작을 쓴다.
    // 누르기: 주기성작업은 "완료 기록" 시트, 일회성작업은 "완료로 표시" 시트 — 둘 다 "끝냈다"는 기록을 남기는 동작이다.
    fun handleRowClick(uiModel: HomeRowUiModel) {
        when (uiModel) {
            is HomeRowUiModel.Recurring -> recordingRecurring = uiModel
            is HomeRowUiModel.OneTime -> completingOneTime = uiModel
        }
    }
    // 꾹 누르기: 주기성작업은 수정 화면, 일회성작업은 동작 없음(수정/삭제는 모아보기 → 대상 상세에서).
    fun rowLongClick(uiModel: HomeRowUiModel): (() -> Unit)? = when (uiModel) {
        is HomeRowUiModel.Recurring -> ({ onEditRecurringItem(uiModel.status.item.id) })
        is HomeRowUiModel.OneTime -> null
    }

    val filteredItems = remember(items, categoryFilter) {
        items.filter { categoryFilter == null || it.categoryId == categoryFilter }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 헤더 요약 3칸을 누르면 목록에서 그 구간의 제목 위치로 부드럽게 스크롤한다(그 구간에 작업이 없으면 가만히 둔다).
    fun scrollToBand(band: DueBand) {
        val presentBands = filteredItems.groupedBySection().map { it.first }
        val index = sectionHeaderIndex(presentBands, band) ?: return
        scope.launch { listState.animateScrollToItem(index) }
    }

    if (showAddSheet) {
        HomeAddSheet(
            assets = assets,
            initialAssetId = addSheetInitialAssetId,
            onDismiss = { showAddSheet = false; addSheetInitialAssetId = null },
            onAddAsset = { showAddSheet = false; onAddAsset() },
            onAddRecurring = { assetId -> showAddSheet = false; onAddRecurringItem(assetId) },
            onAddOneTime = { assetId -> showAddSheet = false; onAddOneTimeTask(assetId) },
        )
    }

    recordingRecurring?.let { target ->
        // 기존 기록 날짜를 읽어 오는 동안(아주 짧다)은 시트를 띄우지 않는다.
        recordingOtherDates?.let { otherDates ->
            ReplacementRecordSheet(
                item = target.status.item,
                assetName = target.assetName,
                categoryEmoji = categories.firstOrNull { it.id == target.categoryId }?.emoji.orEmpty(),
                otherRecordDates = otherDates,
                onDismiss = { recordingRecurring = null },
                onSave = { input ->
                    viewModel.recordReplacement(target.status.item.id, input.date, input.price, input.vendor, input.photoUri, input.memo)
                    recordingRecurring = null
                },
                onPickPhoto = { uri -> viewModel.pickPhoto(uri) },
                onDiscardPhoto = { path -> viewModel.discardPhoto(path) },
            )
        }
    }

    completingOneTime?.let { target ->
        OneTimeDoneSheet(
            record = target.record.record,
            assetName = target.assetName,
            onDismiss = { completingOneTime = null },
            onComplete = { date, price, memo ->
                viewModel.completeOneTime(target.record.record, date, price, memo)
                completingOneTime = null
            },
            onConvertToRecurring = { name ->
                completingOneTime = null
                onConvertToRecurring(target.record.record.assetId, name)
            },
        )
    }

    Scaffold(
        topBar = {
            HomeHeader(
                summary = items.summaryCounts(),
                viewMode = viewMode,
                onViewModeChange = { changeViewMode(it) },
                onSummaryClick = { band -> scrollToBand(band) },
                onAddClick = { addSheetInitialAssetId = null; showAddSheet = true },
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
            if (viewMode == HomeViewMode.CALENDAR) {
                HomeMonthView(
                    items = filteredItems,
                    onRowClick = { handleRowClick(it) },
                    onRowLongClick = { rowLongClick(it) },
                    modifier = Modifier.weight(1f),
                )
                return@Scaffold
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
                                    HomeRow(
                                        uiModel = uiModel,
                                        onClick = { handleRowClick(uiModel) },
                                        onLongClick = rowLongClick(uiModel),
                                    )
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
