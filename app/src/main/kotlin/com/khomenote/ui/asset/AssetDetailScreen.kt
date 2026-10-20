package com.khomenote.ui.asset

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.khomenote.data.local.dto.ConsumableItemWithStatus
import com.khomenote.data.local.entity.AssetEntity
import com.khomenote.data.local.entity.CategoryEntity
import com.khomenote.data.local.entity.ConsumableItemEntity
import com.khomenote.data.local.entity.RepairRecordEntity
import com.khomenote.data.repository.AssetRepository
import com.khomenote.data.repository.CategoryRepository
import com.khomenote.data.repository.ConsumableItemRepository
import com.khomenote.data.repository.RepairRecordRepository
import com.khomenote.domain.DueDateCalculator
import com.khomenote.photo.PhotoStorage
import com.khomenote.ui.common.CircleAddButton
import com.khomenote.ui.common.MoreBelowIndicator
import com.khomenote.ui.common.ReorderHandle
import com.khomenote.ui.common.RowMoreMenu
import com.khomenote.ui.common.dragElevation
import com.khomenote.ui.common.dragHandle
import com.khomenote.ui.common.rememberDragDropListState
import com.khomenote.ui.home.dDayStyle
import com.khomenote.ui.home.dueBandFor
import com.khomenote.ui.home.formatCycleLabel
import com.khomenote.ui.item.LocalPhotoThumbnail
import com.khomenote.ui.theme.CardBorder
import com.khomenote.ui.theme.NumberFontFamily
import com.khomenote.ui.theme.OnceTagBorder
import com.khomenote.ui.theme.RowDivider
import com.khomenote.ui.theme.SurfaceWhite
import com.khomenote.ui.theme.TealPrimary
import com.khomenote.ui.theme.TextFaint
import com.khomenote.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// 주기성작업/일회성작업 목록은 화면 높이를 서로 갉아먹지 않도록 각자 고정 높이 상한을 두고
// (대략 5줄/3줄이 보이는 정도), 더 있으면 그 영역 안에서만 슬라이드해서 본다.
// 행 높이를 고정(72dp)해서 항목이 상한보다 적을 땐 딱 맞게, 많을 땐 상한까지만 보이게 한다.
private val ROW_HEIGHT = 72.dp
private const val VISIBLE_ITEM_ROWS = 5
private const val VISIBLE_REPAIR_ROWS = 3

data class AssetDetailUiState(
    val asset: AssetEntity? = null,
    val items: List<ConsumableItemWithStatus> = emptyList(),
    val repairRecords: List<RepairRecordEntity> = emptyList(),
    val category: CategoryEntity? = null,
)

class AssetDetailViewModel(
    private val assetRepository: AssetRepository,
    private val consumableItemRepository: ConsumableItemRepository,
    private val repairRecordRepository: RepairRecordRepository,
    private val photoStorage: PhotoStorage,
    categoryRepository: CategoryRepository,
    private val assetId: Long,
) : ViewModel() {
    val uiState: StateFlow<AssetDetailUiState> = combine(
        assetRepository.getById(assetId),
        consumableItemRepository.getWithStatusByAsset(assetId),
        repairRecordRepository.getByAsset(assetId),
        categoryRepository.getAll(),
    ) { asset, items, repairRecords, categories ->
        AssetDetailUiState(asset, items, repairRecords, categories.firstOrNull { it.id == asset?.categoryId })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AssetDetailUiState())

    /** 대상을 지운다(이 대상의 일회성작업 기록도 함께 삭제됨). 주기성작업이 남아 있으면 호출하는 쪽에서 막는다. */
    fun deleteAsset(asset: AssetEntity, onDone: () -> Unit) {
        viewModelScope.launch {
            assetRepository.delete(asset)
            onDone()
        }
    }

    fun deleteItem(item: ConsumableItemEntity) {
        viewModelScope.launch { consumableItemRepository.delete(item) }
    }

    fun reorderItems(orderedItems: List<ConsumableItemEntity>) {
        viewModelScope.launch { consumableItemRepository.reorder(orderedItems) }
    }

    fun saveRepairRecord(existing: RepairRecordEntity?, input: RepairRecordInput) {
        viewModelScope.launch { repairRecordRepository.save(input.toEntity(assetId, existing)) }
    }

    /** "완료로 표시" — 실제 한 날·가격·구매처·영수증 사진·메모를 반영하고 알림을 꺼서 홈에서 빼되 기록은 남긴다. */
    fun completeRepairRecord(record: RepairRecordEntity, input: OneTimeDoneInput) {
        viewModelScope.launch { repairRecordRepository.save(record.completedWith(input)) }
    }

    fun deleteRepairRecord(record: RepairRecordEntity) {
        viewModelScope.launch {
            repairRecordRepository.delete(record)
            record.photoUri?.let { path -> withContext(Dispatchers.IO) { photoStorage.deletePhoto(path) } }
        }
    }

    suspend fun pickPhoto(uri: Uri): String? = photoStorage.copyToInternalStorage(uri)

    fun discardPhoto(path: String) {
        viewModelScope.launch(Dispatchers.IO) { photoStorage.deletePhoto(path) }
    }
}

class AssetDetailViewModelFactory(
    private val assetRepository: AssetRepository,
    private val consumableItemRepository: ConsumableItemRepository,
    private val repairRecordRepository: RepairRecordRepository,
    private val photoStorage: PhotoStorage,
    private val categoryRepository: CategoryRepository,
    private val assetId: Long,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AssetDetailViewModel(assetRepository, consumableItemRepository, repairRecordRepository, photoStorage, categoryRepository, assetId) as T
}

private fun repairSubtitle(record: RepairRecordEntity): String {
    val priceText = record.price?.let { "%,d원".format(it) }
    val dateText = if (record.reminderLeadDays != null) {
        val weekday = record.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
        "${record.date} ($weekday) · 예정"
    } else {
        record.date.toString()
    }
    return listOfNotNull(dateText, priceText, record.vendor?.takeIf { it.isNotBlank() }).joinToString(" · ")
}

@Composable
fun AssetDetailScreen(
    viewModel: AssetDetailViewModel,
    onAddItem: () -> Unit,
    onItemClick: (Long) -> Unit,
    onEditItem: (Long) -> Unit,
    onEditAsset: () -> Unit,
    onConvertToRecurring: (name: String) -> Unit,
    onBack: () -> Unit,
    startWithOneTimeForm: Boolean = false,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<ConsumableItemEntity?>(null) }
    val asset = uiState.asset
    val today = remember { LocalDate.now() }

    var displayItems by remember { mutableStateOf(uiState.items) }
    val listState = rememberLazyListState()
    val dragState = rememberDragDropListState(
        listState = listState,
        itemCount = { displayItems.size },
        onMove = { from, to ->
            displayItems = displayItems.toMutableList().apply { add(to, removeAt(from)) }
        },
        onReorderFinished = { viewModel.reorderItems(displayItems.map { it.item }) },
    )
    LaunchedEffect(uiState.items) {
        if (dragState.draggingItemIndex == null) displayItems = uiState.items
    }

    val repairListState = rememberLazyListState()
    var showRepairDialog by remember { mutableStateOf(startWithOneTimeForm) }
    var editingRepairRecord by remember { mutableStateOf<RepairRecordEntity?>(null) }
    var completingRepairRecord by remember { mutableStateOf<RepairRecordEntity?>(null) }
    var pendingDeleteRepair by remember { mutableStateOf<RepairRecordEntity?>(null) }
    var expandedPhotoPath by remember { mutableStateOf<String?>(null) }

    expandedPhotoPath?.let { path ->
        Dialog(
            onDismissRequest = { expandedPhotoPath = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            LocalPhotoThumbnail(
                path = path,
                modifier = Modifier.fillMaxWidth().padding(24.dp).clickable { expandedPhotoPath = null },
            )
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("주기성작업을 삭제할까요?") },
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

    val assetLabel = asset?.let { "${uiState.category?.emoji.orEmpty()} ${it.name}".trim() }

    var showDeleteAssetConfirm by remember { mutableStateOf(false) }
    var showDeleteAssetBlocked by remember { mutableStateOf(false) }

    if (showDeleteAssetConfirm && asset != null) {
        AssetDeleteConfirmDialog(
            assetName = asset.name,
            oneTimeCount = uiState.repairRecords.size,
            onConfirm = {
                showDeleteAssetConfirm = false
                viewModel.deleteAsset(asset, onDone = onBack)
            },
            onDismiss = { showDeleteAssetConfirm = false },
        )
    }

    if (showDeleteAssetBlocked && asset != null) {
        AssetDeleteBlockedDialog(assetName = asset.name, onDismiss = { showDeleteAssetBlocked = false })
    }

    if (showRepairDialog) {
        RepairRecordSheet(
            initial = null,
            assetLabel = assetLabel,
            onDismiss = { showRepairDialog = false },
            onSave = { input ->
                viewModel.saveRepairRecord(null, input)
                showRepairDialog = false
            },
        )
    }

    editingRepairRecord?.let { target ->
        RepairRecordSheet(
            initial = target,
            assetLabel = assetLabel,
            onDismiss = { editingRepairRecord = null },
            onSave = { input ->
                viewModel.saveRepairRecord(target, input)
                editingRepairRecord = null
            },
        )
    }

    completingRepairRecord?.let { target ->
        OneTimeDoneSheet(
            record = target,
            assetName = asset?.name.orEmpty(),
            onDismiss = { completingRepairRecord = null },
            onComplete = { input ->
                viewModel.completeRepairRecord(target, input)
                completingRepairRecord = null
            },
            onConvertToRecurring = { name ->
                completingRepairRecord = null
                onConvertToRecurring(name)
            },
            onPickPhoto = { uri -> viewModel.pickPhoto(uri) },
            onDiscardPhoto = { path -> viewModel.discardPhoto(path) },
        )
    }

    pendingDeleteRepair?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteRepair = null },
            title = { Text("이 일회성작업을 삭제할까요?") },
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

    val cardShape = RoundedCornerShape(18.dp)

    Scaffold(
        topBar = {
            // 시안(AssetDetail.dc.html): TopAppBar 대신 아이콘만 있는 뒤로가기 + 오른쪽 ⋯ 메뉴("대상 수정 / 대상 삭제").
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.weight(1f))
                if (asset != null) {
                    RowMoreMenu(
                        onEdit = onEditAsset,
                        onDelete = {
                            // 주기성작업이 남아 있으면 지울 수 없다(모아보기 카드의 삭제와 같은 규칙).
                            if (uiState.items.isNotEmpty()) showDeleteAssetBlocked = true else showDeleteAssetConfirm = true
                        },
                        editLabel = "대상 수정",
                        deleteLabel = "대상 삭제",
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            if (asset != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(SurfaceWhite)
                            .border(1.dp, CardBorder, RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text(uiState.category?.emoji.orEmpty(), fontSize = 26.sp) }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        uiState.category?.let { Text(it.name, fontSize = 12.sp, color = TextSecondary) }
                        Text(asset.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        if (!asset.memo.isNullOrBlank()) {
                            Text(asset.memo, fontSize = 12.sp, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionHeader(title = "주기성작업", onAdd = onAddItem)
                if (displayItems.isEmpty()) {
                    Text(
                        "등록된 주기성작업이 없습니다. \"+ 추가\" 버튼을 눌러 추가하세요.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ROW_HEIGHT * minOf(displayItems.size, VISIBLE_ITEM_ROWS))
                            .clip(cardShape)
                            .background(SurfaceWhite)
                            .border(1.dp, CardBorder, cardShape),
                    ) {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(displayItems, key = { _, status -> status.item.id }) { index, status ->
                                RecurringRow(
                                    status = status,
                                    today = today,
                                    showTopDivider = index > 0,
                                    modifier = Modifier.dragElevation(index, dragState),
                                    dragHandleModifier = Modifier.dragHandle(index, dragState),
                                    onClick = { onItemClick(status.item.id) },
                                    onEdit = { onEditItem(status.item.id) },
                                    onDelete = { pendingDelete = status.item },
                                )
                            }
                        }
                        MoreBelowIndicator(
                            listState = listState,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                SectionHeader(title = "일회성작업", onAdd = { showRepairDialog = true })
                if (uiState.repairRecords.isEmpty()) {
                    Text(
                        "교체 주기 없이 한 번만 하는 일은 \"+ 추가\"로 남길 수 있어요. 알림 받기를 켜면 예정일로 대시보드에도 표시돼요.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ROW_HEIGHT * minOf(uiState.repairRecords.size, VISIBLE_REPAIR_ROWS))
                            .clip(cardShape)
                            .background(SurfaceWhite)
                            .border(1.dp, CardBorder, cardShape),
                    ) {
                        LazyColumn(state = repairListState, modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(uiState.repairRecords, key = { _, record -> record.id }) { index, record ->
                                RepairRow(
                                    record = record,
                                    today = today,
                                    showTopDivider = index > 0,
                                    onComplete = { completingRepairRecord = record },
                                    onPhotoClick = { path -> expandedPhotoPath = path },
                                    onEdit = { editingRepairRecord = record },
                                    onDelete = { pendingDeleteRepair = record },
                                )
                            }
                        }
                        MoreBelowIndicator(
                            listState = repairListState,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        // 홈 헤더의 "+"와 같은 원형 버튼 — 오른쪽 끝이 아래 카드의 오른쪽 끝과 맞는다.
        CircleAddButton(contentDescription = "$title 추가", onClick = onAdd)
    }
}

@Composable
private fun RecurringRow(
    status: ConsumableItemWithStatus,
    today: LocalDate,
    showTopDivider: Boolean,
    modifier: Modifier,
    dragHandleModifier: Modifier,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val item = status.item
    val daysUntilDue = remember(status, today) {
        val nextDue = DueDateCalculator.nextDueDate(
            itemCreatedAt = item.createdAt,
            cycleDays = item.cycleDays,
            latestRecordDate = status.latestRecordDate,
            monthlyDueDay = item.monthlyDueDay,
            yearlyDueMonth = item.yearlyDueMonth,
            yearlyDueDay = item.yearlyDueDay,
            yearlyDueIsLunar = item.isYearlyLunar == true,
        )
        DueDateCalculator.daysUntilDue(nextDue, today)
    }
    val dStyle = dDayStyle(daysUntilDue, item.reminderLeadDays)
    val fraction = progressFraction(daysUntilDue, cycleLengthDays(item))
    val barColor = progressBarColor(dueBandFor(daysUntilDue, item.reminderLeadDays))

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .clickable(onClick = onClick)
            .topDivider(showTopDivider)
            .padding(start = 2.dp, end = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReorderHandle(dragHandleModifier)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    item.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatCycleLabel(item),
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(RowDivider)) {
                Box(modifier = Modifier.fillMaxWidth(fraction).height(6.dp).background(barColor))
            }
        }
        DDayBadge(dStyle.text, dStyle.background, dStyle.textColor)
        RowMoreMenu(onEdit = onEdit, onDelete = onDelete)
    }
}

@Composable
private fun RepairRow(
    record: RepairRecordEntity,
    today: LocalDate,
    showTopDivider: Boolean,
    onComplete: () -> Unit,
    onPhotoClick: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val lead = record.reminderLeadDays
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            // 아직 안 끝난(알림 켜진) 일회성작업은 가볍게 눌러도 "완료로 표시" 시트가 열린다.
            .then(if (lead != null) Modifier.clickable(onClick = onComplete) else Modifier)
            .topDivider(showTopDivider)
            .padding(start = 14.dp, end = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    record.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (lead != null) {
                    Spacer(Modifier.width(6.dp))
                    Row(
                        modifier = Modifier
                            .border(1.dp, OnceTagBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(11.dp))
                        Text("${lead}일 전", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                    }
                }
            }
            Text(repairSubtitle(record), fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!record.memo.isNullOrBlank()) {
                Text(record.memo, fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        record.photoUri?.let { path ->
            LocalPhotoThumbnail(
                path = path,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).clickable { onPhotoClick(path) },
                contentScale = ContentScale.Crop,
            )
        }
        if (lead != null) {
            val dStyle = dDayStyle(DueDateCalculator.daysUntilDue(record.date, today), lead)
            DDayBadge(dStyle.text, dStyle.background, dStyle.textColor)
        }
        // 알림이 켜진(= 아직 끝나지 않은) 일회성작업만 "완료로 표시" 메뉴가 있다.
        RowMoreMenu(onEdit = onEdit, onDelete = onDelete, onComplete = if (lead != null) onComplete else null)
    }
}

@Composable
private fun DDayBadge(text: String, background: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .widthIn(min = 52.dp)
            .height(32.dp)
            .background(background, RoundedCornerShape(9.dp))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontFamily = NumberFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = textColor)
    }
}

private fun Modifier.topDivider(show: Boolean): Modifier =
    if (!show) this else this.drawBehind {
        drawLine(RowDivider, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
    }
