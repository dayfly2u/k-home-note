package com.khomenote.ui.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.khomenote.data.local.entity.ConsumableItemEntity
import com.khomenote.data.repository.ConsumableItemRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

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

    private var loadedAssetId: Long? = assetIdForNew

    // 수정 시 원래 등록일/정렬 순서를 보존한다 — AssetFormViewModel과 같은 이유로,
    // 안 챙기면 저장할 때마다 등록일이 오늘로 리셋되어 다음 예정일 계산이 틀어진다.
    private var loadedCreatedAt: LocalDate = LocalDate.now()
    private var loadedSortOrder: Int? = null

    val cycleDays: Int
        get() = (cycleValueText.toIntOrNull() ?: 0) * cycleUnit.days

    val monthlyDueDay: Int?
        get() = monthlyDueDayText.toIntOrNull()?.coerceIn(1, 31)

    val yearlyMonth: Int?
        get() = yearlyMonthText.toIntOrNull()?.coerceIn(1, 12)

    val yearlyDay: Int?
        get() = yearlyDayText.toIntOrNull()?.coerceIn(1, 31)

    init {
        if (itemId != null) {
            viewModelScope.launch {
                val item = consumableItemRepository.getById(itemId).first() ?: return@launch
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
                loadedAssetId = item.assetId
                loadedCreatedAt = item.createdAt
                loadedSortOrder = item.sortOrder
            }
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
    fun onReminderLeadDaysChange(value: String) { reminderLeadDaysText = value.filter(Char::isDigit) }
    fun onMemoChange(value: String) { memo = value }

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
                    reminderLeadDays = reminderLeadDaysText.toIntOrNull() ?: 7,
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
    private val assetIdForNew: Long?,
    private val itemId: Long?,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ItemFormViewModel(consumableItemRepository, assetIdForNew, itemId) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemFormScreen(
    viewModel: ItemFormViewModel,
    isNew: Boolean,
    onSaved: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "소모품 추가" else "소모품 수정") },
                navigationIcon = { TextButton(onClick = onBack) { Text("< 뒤로") } },
                actions = {
                    TextButton(
                        onClick = { viewModel.save(onSaved) },
                        enabled = viewModel.canSave(),
                    ) { Text("저장") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("이름 (예: 필터, 엔진오일)") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
            Text("교체 주기")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                CycleMode.entries.forEach { mode ->
                    FilterChip(
                        selected = viewModel.cycleMode == mode,
                        onClick = { viewModel.onCycleModeChange(mode) },
                        label = { Text(mode.label) },
                    )
                }
            }
            if (viewModel.cycleMode == CycleMode.INTERVAL) {
                OutlinedTextField(
                    value = viewModel.cycleValueText,
                    onValueChange = viewModel::onCycleValueChange,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                    CycleUnit.entries.forEach { unit ->
                        FilterChip(
                            selected = viewModel.cycleUnit == unit,
                            onClick = { viewModel.onCycleUnitChange(unit) },
                            label = { Text(unit.label) },
                        )
                    }
                }
            } else if (viewModel.cycleMode == CycleMode.MONTHLY) {
                OutlinedTextField(
                    value = viewModel.monthlyDueDayText,
                    onValueChange = viewModel::onMonthlyDueDayChange,
                    label = { Text("매달 며칠 (1~31)") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                )
                Text(
                    "말일이 없는 달(예: 2월 30일)은 그 달의 마지막 날로 자동 조정돼요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                    OutlinedTextField(
                        value = viewModel.yearlyMonthText,
                        onValueChange = viewModel::onYearlyMonthChange,
                        label = { Text("월 (1~12)") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = viewModel.yearlyDayText,
                        onValueChange = viewModel::onYearlyDayChange,
                        label = { Text("일 (1~31)") },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                    FilterChip(
                        selected = !viewModel.yearlyIsLunar,
                        onClick = { viewModel.onYearlyIsLunarChange(false) },
                        label = { Text("양력") },
                    )
                    FilterChip(
                        selected = viewModel.yearlyIsLunar,
                        onClick = { viewModel.onYearlyIsLunarChange(true) },
                        label = { Text("음력") },
                    )
                }
                Text(
                    if (viewModel.yearlyIsLunar) {
                        "음력 생일·제사처럼 매년 음력 날짜에 맞춰 그 해의 양력 날짜로 자동 환산돼요."
                    } else {
                        "생일·기념일처럼 매년 돌아오는 날짜예요. 그 해에 없는 날짜(예: 2월 29일)는 그 달의 마지막 날로 자동 조정돼요."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            OutlinedTextField(
                value = viewModel.reminderLeadDaysText,
                onValueChange = viewModel::onReminderLeadDaysChange,
                label = { Text("며칠 전에 알림을 받을까요?") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
            OutlinedTextField(
                value = viewModel.memo,
                onValueChange = viewModel::onMemoChange,
                label = { Text("메모") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
