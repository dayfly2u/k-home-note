package com.khomenote.ui.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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

class ItemFormViewModel(
    private val consumableItemRepository: ConsumableItemRepository,
    assetIdForNew: Long?,
    private val itemId: Long?,
) : ViewModel() {
    var name by mutableStateOf("")
        private set
    var cycleUnit by mutableStateOf(CycleUnit.MONTHS)
        private set
    var cycleValueText by mutableStateOf("6")
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

    init {
        if (itemId != null) {
            viewModelScope.launch {
                val item = consumableItemRepository.getById(itemId).first() ?: return@launch
                name = item.name
                cycleUnit = CycleUnit.DAYS
                cycleValueText = item.cycleDays.toString()
                reminderLeadDaysText = item.reminderLeadDays.toString()
                memo = item.memo ?: ""
                loadedAssetId = item.assetId
                loadedCreatedAt = item.createdAt
                loadedSortOrder = item.sortOrder
            }
        }
    }

    fun onNameChange(value: String) { name = value }
    fun onCycleUnitChange(value: CycleUnit) { cycleUnit = value }
    fun onCycleValueChange(value: String) { cycleValueText = value.filter(Char::isDigit) }
    fun onReminderLeadDaysChange(value: String) { reminderLeadDaysText = value.filter(Char::isDigit) }
    fun onMemoChange(value: String) { memo = value }

    fun canSave(): Boolean = name.isNotBlank() && cycleDays > 0 && loadedAssetId != null

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
                    cycleDays = cycleDays,
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
