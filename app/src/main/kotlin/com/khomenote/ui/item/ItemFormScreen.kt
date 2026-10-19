package com.khomenote.ui.item

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.khomenote.data.local.entity.ConsumableItemEntity
import com.khomenote.data.repository.AssetRepository
import com.khomenote.data.repository.CategoryRepository
import com.khomenote.data.repository.ConsumableItemRepository
import com.khomenote.domain.DueDateCalculator
import com.khomenote.notification.ReminderScheduler
import com.khomenote.ui.common.KFilterChip
import com.khomenote.ui.common.KTextField
import com.khomenote.ui.common.NumberBox
import com.khomenote.ui.common.RadioSegment
import com.khomenote.ui.common.SegmentedTabs
import com.khomenote.ui.common.StepperButton
import com.khomenote.ui.common.formatReminderTime
import com.khomenote.ui.home.dDayStyle
import com.khomenote.ui.theme.CardBorder
import com.khomenote.ui.theme.ChipBorderUnselected
import com.khomenote.ui.theme.NumberFontFamily
import com.khomenote.ui.theme.SurfaceWhite
import com.khomenote.ui.theme.TealContainer
import com.khomenote.ui.theme.TealPrimary
import com.khomenote.ui.theme.TealPrimaryDark
import com.khomenote.ui.theme.TextSecondary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class CycleUnit(val days: Int, val label: String) {
    DAYS(1, "일"),
    MONTHS(30, "개월"),
    YEARS(365, "년"),
}

enum class CycleMode(val label: String) {
    INTERVAL("기간"),
    MONTHLY("매달 특정 날짜"),
    YEARLY("매년 특정 날짜"),
}

class ItemFormViewModel(
    private val consumableItemRepository: ConsumableItemRepository,
    private val assetRepository: AssetRepository,
    private val categoryRepository: CategoryRepository,
    assetIdForNew: Long?,
    private val itemId: Long?,
) : ViewModel() {
    var name by mutableStateOf("")
        private set
    var cycleMode by mutableStateOf(CycleMode.INTERVAL)
        private set
    var cycleUnit by mutableStateOf(CycleUnit.MONTHS)
        private set
    var cycleValueText by mutableStateOf("6")
        private set
    var monthlyDueDayText by mutableStateOf("1")
        private set
    var yearlyMonthText by mutableStateOf("1")
        private set
    var yearlyDayText by mutableStateOf("1")
        private set
    var yearlyIsLunar by mutableStateOf(false)
        private set
    var reminderLeadDaysText by mutableStateOf("7")
        private set
    var memo by mutableStateOf("")
        private set

    /** "🎂 우리 가족"처럼 이 작업이 속한 대상 표시용(읽어오는 동안은 null). */
    var assetLabel by mutableStateOf<String?>(null)
        private set

    private var loadedAssetId: Long? = assetIdForNew

    // 수정 시 원래 등록일/정렬 순서를 보존한다 — AssetFormViewModel과 같은 이유로,
    // 안 챙기면 저장할 때마다 등록일이 오늘로 리셋되어 다음 예정일 계산이 틀어진다.
    // 다음 날짜 미리보기가 기존 완료 기록을 반영하도록 최근 완료일도 함께 들고 있는다.
    private var loadedCreatedAt: LocalDate = LocalDate.now()
    private var loadedSortOrder: Int? = null
    private var loadedLatestRecordDate: LocalDate? = null

    val cycleDays: Int
        get() = (cycleValueText.toIntOrNull() ?: 0) * cycleUnit.days

    val monthlyDueDay: Int?
        get() = monthlyDueDayText.toIntOrNull()?.coerceIn(1, 31)

    val yearlyMonth: Int?
        get() = yearlyMonthText.toIntOrNull()?.coerceIn(1, 12)

    val yearlyDay: Int?
        get() = yearlyDayText.toIntOrNull()?.coerceIn(1, 31)

    val reminderLeadDays: Int
        get() = reminderLeadDaysText.toIntOrNull() ?: 7

    /** 지금 입력값대로 저장했을 때 다음 예정일. 입력이 덜 끝났으면 null. */
    val previewNextDueDate: LocalDate?
        get() = runCatching {
            when (cycleMode) {
                CycleMode.INTERVAL ->
                    cycleDays.takeIf { it > 0 }
                        ?.let { DueDateCalculator.nextDueDate(loadedCreatedAt, it, loadedLatestRecordDate) }
                CycleMode.MONTHLY ->
                    monthlyDueDay?.let {
                        DueDateCalculator.nextDueDate(loadedCreatedAt, 30, loadedLatestRecordDate, monthlyDueDay = it)
                    }
                CycleMode.YEARLY -> {
                    val month = yearlyMonth
                    val day = yearlyDay
                    if (month != null && day != null) {
                        DueDateCalculator.nextDueDate(
                            loadedCreatedAt, 30, loadedLatestRecordDate,
                            yearlyDueMonth = month, yearlyDueDay = day, yearlyDueIsLunar = yearlyIsLunar,
                        )
                    } else {
                        null
                    }
                }
            }
        }.getOrNull()

    init {
        assetIdForNew?.let(::loadAssetLabel)
        if (itemId != null) {
            viewModelScope.launch {
                val status = consumableItemRepository.getWithStatusById(itemId).first() ?: return@launch
                val item = status.item
                // 미리보기 계산용 값은 화면에 보이는 입력값보다 먼저 채워 두어야 첫 그리기부터 맞는 날짜가 나온다.
                loadedCreatedAt = item.createdAt
                loadedSortOrder = item.sortOrder
                loadedLatestRecordDate = status.latestRecordDate
                loadedAssetId = item.assetId
                name = item.name
                if (item.monthlyDueDay != null) {
                    cycleMode = CycleMode.MONTHLY
                    monthlyDueDayText = item.monthlyDueDay.toString()
                } else if (item.yearlyDueMonth != null) {
                    cycleMode = CycleMode.YEARLY
                    yearlyMonthText = item.yearlyDueMonth.toString()
                    yearlyDayText = (item.yearlyDueDay ?: 1).toString()
                    yearlyIsLunar = item.isYearlyLunar == true
                } else {
                    cycleMode = CycleMode.INTERVAL
                    cycleUnit = CycleUnit.DAYS
                    cycleValueText = item.cycleDays.toString()
                }
                reminderLeadDaysText = item.reminderLeadDays.toString()
                memo = item.memo ?: ""
                loadAssetLabel(item.assetId)
            }
        }
    }

    private fun loadAssetLabel(assetId: Long) {
        viewModelScope.launch {
            val asset = assetRepository.getById(assetId).first() ?: return@launch
            val emoji = categoryRepository.getAll().first().firstOrNull { it.id == asset.categoryId }?.emoji.orEmpty()
            assetLabel = "$emoji ${asset.name}".trim()
        }
    }

    fun onNameChange(value: String) { name = value }
    fun onCycleModeChange(value: CycleMode) { cycleMode = value }
    fun onCycleUnitChange(value: CycleUnit) { cycleUnit = value }
    fun onCycleValueChange(value: String) { cycleValueText = value.filter(Char::isDigit) }
    fun onMonthlyDueDayChange(value: String) { monthlyDueDayText = value.filter(Char::isDigit) }
    fun onYearlyMonthChange(value: String) { yearlyMonthText = value.filter(Char::isDigit) }
    fun onYearlyDayChange(value: String) { yearlyDayText = value.filter(Char::isDigit) }
    fun onYearlyIsLunarChange(value: Boolean) { yearlyIsLunar = value }
    fun onMemoChange(value: String) { memo = value }

    fun stepReminderLeadDays(delta: Int) {
        reminderLeadDaysText = (reminderLeadDays + delta).coerceIn(0, 365).toString()
    }

    internal fun applyQuickPick(pick: QuickPick) {
        name = pick.name
        cycleMode = pick.mode
        when (pick.mode) {
            CycleMode.INTERVAL -> {
                cycleUnit = pick.cycleUnit
                cycleValueText = pick.cycleValue.toString()
            }
            CycleMode.MONTHLY -> pick.monthlyDay?.let { monthlyDueDayText = it.toString() }
            CycleMode.YEARLY -> {
                pick.yearlyMonth?.let { yearlyMonthText = it.toString() }
                pick.yearlyDay?.let { yearlyDayText = it.toString() }
            }
        }
        reminderLeadDaysText = pick.reminderLeadDays.toString()
    }

    fun canSave(): Boolean = name.isNotBlank() && loadedAssetId != null && when (cycleMode) {
        CycleMode.INTERVAL -> cycleDays > 0
        CycleMode.MONTHLY -> monthlyDueDay != null
        CycleMode.YEARLY -> yearlyMonth != null && yearlyDay != null
    }

    fun save(onDone: () -> Unit) {
        val assetId = loadedAssetId ?: return
        if (!canSave()) return
        viewModelScope.launch {
            // 새 소모품은 항상 그 자산의 목록 맨 끝에 추가되도록 sortOrder를 현재 개수로 준다.
            val sortOrder = loadedSortOrder ?: consumableItemRepository.getByAsset(assetId).first().size
            consumableItemRepository.save(
                ConsumableItemEntity(
                    id = itemId ?: 0,
                    assetId = assetId,
                    name = name,
                    // 매달/매년 고정 주기일 때는 cycleDays가 실제 계산에 쓰이지 않지만, NOT NULL 컬럼이라 30일로 채워둔다.
                    cycleDays = if (cycleMode == CycleMode.INTERVAL) cycleDays else 30,
                    monthlyDueDay = if (cycleMode == CycleMode.MONTHLY) monthlyDueDay else null,
                    yearlyDueMonth = if (cycleMode == CycleMode.YEARLY) yearlyMonth else null,
                    yearlyDueDay = if (cycleMode == CycleMode.YEARLY) yearlyDay else null,
                    isYearlyLunar = if (cycleMode == CycleMode.YEARLY) yearlyIsLunar else null,
                    reminderLeadDays = reminderLeadDays,
                    memo = memo.takeIf { it.isNotBlank() },
                    createdAt = loadedCreatedAt,
                    sortOrder = sortOrder,
                )
            )
            onDone()
        }
    }
}

class ItemFormViewModelFactory(
    private val consumableItemRepository: ConsumableItemRepository,
    private val assetRepository: AssetRepository,
    private val categoryRepository: CategoryRepository,
    private val assetIdForNew: Long?,
    private val itemId: Long?,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ItemFormViewModel(consumableItemRepository, assetRepository, categoryRepository, assetIdForNew, itemId) as T
}

private val PREVIEW_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN)

@Composable
fun ItemFormScreen(
    viewModel: ItemFormViewModel,
    isNew: Boolean,
    onSaved: () -> Unit,
    onBack: () -> Unit,
) {
    val canSave = viewModel.canSave()
    Scaffold(
        topBar = {
            // 시안(ItemForm.dc.html): ✕ 닫기 + 제목 + 오른쪽 "저장" 알약 버튼.
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "닫기", modifier = Modifier.size(22.dp))
                }
                Text(
                    if (isNew) "주기성작업 추가" else "주기성작업 수정",
                    modifier = Modifier.weight(1f),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (canSave) TealPrimary else ChipBorderUnselected)
                        .then(if (canSave) Modifier.clickable { viewModel.save(onSaved) } else Modifier)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("저장", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (isNew) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel("빠른 선택")
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        QUICK_PICKS.forEach { pick ->
                            KFilterChip(selected = false, label = pick.chipLabel, onClick = { viewModel.applyQuickPick(pick) })
                        }
                    }
                }
            }

            FormCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FieldLabel("이름")
                    KTextField(
                        value = viewModel.name,
                        onValueChange = viewModel::onNameChange,
                        placeholder = "예: 엔진오일 교체, 어머니 생신",
                    )
                }
                viewModel.assetLabel?.let { label ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("대상", fontSize = 13.sp, color = TextSecondary)
                        Text(
                            label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFFF2F5F5))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            FormCard {
                FieldLabel("언제 돌아오나요")
                SegmentedTabs(
                    options = listOf("기간마다", "매달", "매년"),
                    selectedIndex = CycleMode.entries.indexOf(viewModel.cycleMode),
                    onSelect = { viewModel.onCycleModeChange(CycleMode.entries[it]) },
                )
                when (viewModel.cycleMode) {
                    CycleMode.INTERVAL -> Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NumberBox(viewModel.cycleValueText, viewModel::onCycleValueChange, "주기 숫자", width = 64.dp)
                        RadioSegment(
                            options = CycleUnit.entries.map { it.label },
                            selectedIndex = CycleUnit.entries.indexOf(viewModel.cycleUnit),
                            onSelect = { viewModel.onCycleUnitChange(CycleUnit.entries[it]) },
                        )
                        Text("마다", fontSize = 14.sp, color = Color(0xFF3A3F46))
                    }
                    CycleMode.MONTHLY -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("매달", fontSize = 14.sp, color = Color(0xFF3A3F46))
                            NumberBox(viewModel.monthlyDueDayText, viewModel::onMonthlyDueDayChange, "일")
                            Text("일", fontSize = 14.sp, color = Color(0xFF3A3F46))
                        }
                        Hint("말일이 없는 달(예: 2월 30일)은 그 달의 마지막 날로 자동 조정돼요.")
                    }
                    CycleMode.YEARLY -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioSegment(
                                options = listOf("양력", "음력"),
                                selectedIndex = if (viewModel.yearlyIsLunar) 1 else 0,
                                onSelect = { viewModel.onYearlyIsLunarChange(it == 1) },
                            )
                            NumberBox(viewModel.yearlyMonthText, viewModel::onYearlyMonthChange, "월")
                            Text("월", fontSize = 14.sp, color = Color(0xFF3A3F46))
                            NumberBox(viewModel.yearlyDayText, viewModel::onYearlyDayChange, "일")
                            Text("일", fontSize = 14.sp, color = Color(0xFF3A3F46))
                        }
                        Hint(
                            if (viewModel.yearlyIsLunar) {
                                "음력 생일·제사처럼 매년 음력 날짜에 맞춰 그 해의 양력 날짜로 자동 환산돼요."
                            } else {
                                "생일·기념일처럼 매년 돌아오는 날짜예요. 그 해에 없는 날짜(예: 2월 29일)는 그 달의 마지막 날로 자동 조정돼요."
                            },
                        )
                    }
                }
            }

            FormCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FieldLabel("며칠 전부터 알려줄까요", modifier = Modifier.weight(1f))
                    StepperButton("−", "하루 줄이기", enabled = viewModel.reminderLeadDays > 0) { viewModel.stepReminderLeadDays(-1) }
                    Row(modifier = Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
                        Text(viewModel.reminderLeadDays.toString(), fontFamily = NumberFontFamily, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("일", fontSize = 13.sp, modifier = Modifier.padding(start = 1.dp, bottom = 3.dp))
                    }
                    StepperButton("+", "하루 늘리기") { viewModel.stepReminderLeadDays(1) }
                }
                val (alertHour, alertMinute) = ReminderScheduler.getTime(LocalContext.current)
                Hint(reminderExplanation(viewModel.reminderLeadDays, formatReminderTime(alertHour, alertMinute)))
            }

            viewModel.previewNextDueDate?.let { next ->
                val days = DueDateCalculator.daysUntilDue(next, LocalDate.now())
                val dday = dDayStyle(days, viewModel.reminderLeadDays).text
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(TealContainer)
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null, tint = TealPrimaryDark, modifier = Modifier.size(24.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("다음 날짜", fontSize = 12.sp, color = TealPrimaryDark)
                        Text(
                            "${next.format(PREVIEW_DATE_FORMAT)} · $dday",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TealPrimaryDark,
                        )
                        Text(
                            nextDateNote(
                                viewModel.cycleMode, viewModel.yearlyIsLunar,
                                viewModel.yearlyMonth, viewModel.yearlyDay, viewModel.monthlyDueDay,
                            ),
                            fontSize = 12.sp,
                            color = TealPrimaryDark,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel("메모")
                KTextField(
                    value = viewModel.memo,
                    onValueChange = viewModel::onMemoChange,
                    placeholder = "예: 가족 식사 장소 미리 예약",
                    singleLine = false,
                )
            }
        }
    }
}

@Composable
private fun FormCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SurfaceWhite)
            .border(1.dp, CardBorder, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
}

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 12.sp, color = TextSecondary)
}
