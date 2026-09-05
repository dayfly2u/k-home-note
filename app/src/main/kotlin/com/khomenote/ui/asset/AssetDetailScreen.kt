package com.khomenote.ui.asset

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.khomenote.data.local.entity.AssetEntity
import com.khomenote.data.local.entity.ConsumableItemEntity
import com.khomenote.data.local.entity.RepairRecordEntity
import com.khomenote.data.repository.AssetRepository
import com.khomenote.data.repository.ConsumableItemRepository
import com.khomenote.data.repository.RepairRecordRepository
import com.khomenote.ui.common.MoreBelowIndicator
import com.khomenote.ui.common.dragElevation
import com.khomenote.ui.common.dragHandle
import com.khomenote.ui.common.rememberDragDropListState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// 소모품/정비이력 목록은 화면 높이를 서로 갉아먹지 않도록 각자 고정 높이로 두고
// (대략 4줄/3줄이 보이는 정도), 더 있으면 그 영역 안에서만 슬라이드해서 본다.
private val ITEM_ROW_HEIGHT = 72.dp
private val REPAIR_ROW_HEIGHT = 96.dp
private const val VISIBLE_ITEM_ROWS = 5
private const val VISIBLE_REPAIR_ROWS = 3

// 소모품/정비이력 둘 다 같은 연한녹색 타이틀 바로 구간을 표시한다(구매/교체 이력 화면과도 통일).
val SECTION_TITLE_COLOR = Color(0xFFC8E6C9)
val SECTION_TITLE_TEXT_COLOR = Color(0xFF2E7D32)

/**
 * monthlyDueDay가 있으면 "매달 N일"로, yearlyDueMonth/yearlyDueDay가 있으면 "매년 M월 D일"로,
 * 그 외에는 cycleDays를 "1yr 3m" 형태로 보여준다.
 * cycleDays는 12개월 = 1년 기준으로 환산하고(달력 계산과 맞춤), 0인 단위는 생략한다.
 * 예: 15개월(450일) -> "1yr 3m", 6개월(180일) -> "6m".
 */
private fun formatCycle(item: ConsumableItemEntity): String {
    item.monthlyDueDay?.let { return "매달 ${it}일" }
    item.yearlyDueMonth?.let { month ->
        val lunarSuffix = if (item.isYearlyLunar == true) "(음력)" else ""
        return "매년 ${month}월 ${item.yearlyDueDay}일$lunarSuffix"
    }
    val days = item.cycleDays
    val totalMonths = days / 30
    val remDays = days % 30
    val years = totalMonths / 12
    val months = totalMonths % 12
    val parts = buildList {
        if (years > 0) add("${years}yr")
        if (months > 0) add("${months}m")
        if (remDays > 0) add("${remDays}d")
    }
    return if (parts.isEmpty()) "0d" else parts.joinToString(" ")
}

data class AssetDetailUiState(
    val asset: AssetEntity? = null,
    val items: List<ConsumableItemEntity> = emptyList(),
    val repairRecords: List<RepairRecordEntity> = emptyList(),
)

class AssetDetailViewModel(
    assetRepository: AssetRepository,
    private val consumableItemRepository: ConsumableItemRepository,
    private val repairRecordRepository: RepairRecordRepository,
    private val assetId: Long,
) : ViewModel() {
    val uiState: StateFlow<AssetDetailUiState> = combine(
        assetRepository.getById(assetId),
        consumableItemRepository.getByAsset(assetId),
        repairRecordRepository.getByAsset(assetId),
    ) { asset, items, repairRecords -> AssetDetailUiState(asset, items, repairRecords) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AssetDetailUiState())

    fun deleteItem(item: ConsumableItemEntity) {
        viewModelScope.launch { consumableItemRepository.delete(item) }
    }

    fun reorderItems(orderedItems: List<ConsumableItemEntity>) {
        viewModelScope.launch { consumableItemRepository.reorder(orderedItems) }
    }

    fun saveRepairRecord(existing: RepairRecordEntity?, input: RepairRecordInput) {
        viewModelScope.launch {
            repairRecordRepository.save(
                RepairRecordEntity(
                    id = existing?.id ?: 0,
                    assetId = assetId,
                    name = input.name,
                    date = input.date,
                    price = input.price,
                    memo = input.memo,
                )
            )
        }
    }

    fun deleteRepairRecord(record: RepairRecordEntity) {
        viewModelScope.launch { repairRecordRepository.delete(record) }
    }
}

class AssetDetailViewModelFactory(
    private val assetRepository: AssetRepository,
    private val consumableItemRepository: ConsumableItemRepository,
    private val repairRecordRepository: RepairRecordRepository,
    private val assetId: Long,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AssetDetailViewModel(assetRepository, consumableItemRepository, repairRecordRepository, assetId) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetDetailScreen(
    viewModel: AssetDetailViewModel,
    onAddItem: () -> Unit,
    onItemClick: (Long) -> Unit,
    onEditItem: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<ConsumableItemEntity?>(null) }
    val asset = uiState.asset

    var displayItems by remember { mutableStateOf(uiState.items) }
    val listState = rememberLazyListState()
    val dragState = rememberDragDropListState(
        listState = listState,
        itemCount = { displayItems.size },
        onMove = { from, to ->
            displayItems = displayItems.toMutableList().apply { add(to, removeAt(from)) }
        },
        onReorderFinished = { viewModel.reorderItems(displayItems) },
    )
    LaunchedEffect(uiState.items) {
        if (dragState.draggingItemIndex == null) displayItems = uiState.items
    }

    val repairListState = rememberLazyListState()
    var showRepairDialog by remember { mutableStateOf(false) }
    var editingRepairRecord by remember { mutableStateOf<RepairRecordEntity?>(null) }
    var pendingDeleteRepair by remember { mutableStateOf<RepairRecordEntity?>(null) }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("소모품을 삭제할까요?") },
            text = { Text("\"${item.name}\"과 교체 이력이 모두 삭제되며 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteItem(item)
                    pendingDelete = null
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("취소") } },
        )
    }

    if (showRepairDialog) {
        RepairRecordDialog(
            initial = null,
            onDismiss = { showRepairDialog = false },
            onSave = { input ->
                viewModel.saveRepairRecord(null, input)
                showRepairDialog = false
            },
        )
    }

    editingRepairRecord?.let { target ->
        RepairRecordDialog(
            initial = target,
            onDismiss = { editingRepairRecord = null },
            onSave = { input ->
                viewModel.saveRepairRecord(target, input)
                editingRepairRecord = null
            },
        )
    }

    pendingDeleteRepair?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteRepair = null },
            title = { Text("이 정비 이력을 삭제할까요?") },
            text = { Text("삭제된 기록은 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRepairRecord(target)
                    pendingDeleteRepair = null
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteRepair = null }) { Text("취소") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(asset?.name ?: "자산") },
                navigationIcon = { TextButton(onClick = onBack) { Text("< 뒤로") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "소모품",
                    fontWeight = FontWeight.Bold,
                    color = SECTION_TITLE_TEXT_COLOR,
                    modifier = Modifier
                        .background(SECTION_TITLE_COLOR, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
                TextButton(onClick = onAddItem) { Text("소모품 추가") }
            }
            if (displayItems.isEmpty()) {
                Text(
                    "등록된 소모품이 없습니다. \"소모품 추가\" 버튼을 눌러 추가하세요.",
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                Box(modifier = Modifier.height(ITEM_ROW_HEIGHT * VISIBLE_ITEM_ROWS)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(displayItems, key = { _, item -> item.id }) { index, item ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .dragElevation(index, dragState)
                                    .clickable { onItemClick(item.id) }
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "≡",
                                        modifier = Modifier.dragHandle(index, dragState).padding(end = 12.dp),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row {
                                            Text(item.name, fontWeight = FontWeight.Bold)
                                            Text(
                                                " (${formatCycle(item)})",
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                        if (!item.memo.isNullOrBlank()) {
                                            Text(
                                                item.memo,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline,
                                            )
                                        }
                                    }
                                }
                                Row {
                                    TextButton(onClick = { onEditItem(item.id) }) { Text("수정") }
                                    TextButton(onClick = { pendingDelete = item }) { Text("삭제") }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                    MoreBelowIndicator(
                        listState = listState,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "정비 이력",
                    fontWeight = FontWeight.Bold,
                    color = SECTION_TITLE_TEXT_COLOR,
                    modifier = Modifier
                        .background(SECTION_TITLE_COLOR, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
                TextButton(onClick = { showRepairDialog = true }) { Text("정비 이력 추가") }
            }
            if (uiState.repairRecords.isEmpty()) {
                Text(
                    "교체 주기와 무관하게 고친 내역은 \"정비 이력 추가\"로 남길 수 있어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            } else {
                Box(modifier = Modifier.height(REPAIR_ROW_HEIGHT * VISIBLE_REPAIR_ROWS)) {
                    LazyColumn(
                        state = repairListState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(uiState.repairRecords, key = { it.id }) { record ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("🔧 ${record.name}", fontWeight = FontWeight.Bold)
                                    val priceText = record.price?.let { "%,d원".format(it) }
                                    val infoLine = listOfNotNull(record.date.toString(), priceText).joinToString(" · ")
                                    Text(infoLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    if (!record.memo.isNullOrBlank()) {
                                        Text(record.memo, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Row {
                                    TextButton(onClick = { editingRepairRecord = record }) { Text("수정") }
                                    TextButton(onClick = { pendingDeleteRepair = record }) { Text("삭제") }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                    MoreBelowIndicator(
                        listState = repairListState,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                    )
                }
            }
        }
    }
}
