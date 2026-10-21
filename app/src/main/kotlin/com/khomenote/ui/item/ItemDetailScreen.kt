package com.khomenote.ui.item

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.khomenote.data.local.dto.ConsumableItemWithStatus
import com.khomenote.data.local.entity.ReplacementRecordEntity
import com.khomenote.data.repository.CategoryRepository
import com.khomenote.data.repository.ConsumableItemRepository
import com.khomenote.data.repository.ReplacementRecordRepository
import com.khomenote.domain.DueDateCalculator
import com.khomenote.domain.ReminderTargetSelector
import com.khomenote.photo.PhotoStorage
import com.khomenote.ui.asset.cycleLengthDays
import com.khomenote.ui.asset.progressBarColor
import com.khomenote.ui.asset.progressFraction
import com.khomenote.ui.common.MoreBelowIndicator
import com.khomenote.ui.common.RowMoreMenu
import com.khomenote.ui.home.DueBand
import com.khomenote.ui.home.dDayStyle
import com.khomenote.ui.home.dueBandFor
import com.khomenote.ui.home.formatCycleLabel
import com.khomenote.ui.theme.CardBorder
import com.khomenote.ui.theme.NumberFontFamily
import com.khomenote.ui.theme.OrangeUrgent
import com.khomenote.ui.theme.RowDivider
import com.khomenote.ui.theme.SurfaceWhite
import com.khomenote.ui.theme.TealPrimary
import com.khomenote.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 로컬 파일 경로를 BitmapFactory로 직접 디코드해서 보여주는 작은 썸네일.
 * 원격 URL을 다루지 않으므로 별도 이미지 로딩 라이브러리를 쓰지 않는다.
 */
@Composable
fun LocalPhotoThumbnail(path: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit) {
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(path)?.asImageBitmap()
        }
    }
    bitmap?.let {
        Image(bitmap = it, contentDescription = null, modifier = modifier, contentScale = contentScale)
    }
}

data class ItemDetailUiState(
    val status: ConsumableItemWithStatus? = null,
    val records: List<ReplacementRecordEntity> = emptyList(),
    val loaded: Boolean = false,
    /** 완료 기록 시트 머리에 보여줄 대상의 카테고리 이모지. */
    val categoryEmoji: String = "",
)

class ItemDetailViewModel(
    private val consumableItemRepository: ConsumableItemRepository,
    private val replacementRecordRepository: ReplacementRecordRepository,
    private val photoStorage: PhotoStorage,
    categoryRepository: CategoryRepository,
    private val itemId: Long,
) : ViewModel() {

    val uiState: StateFlow<ItemDetailUiState> = combine(
        consumableItemRepository.getWithStatusById(itemId),
        replacementRecordRepository.getByItem(itemId),
        categoryRepository.getAll(),
    ) { status, records, categories ->
        ItemDetailUiState(
            status = status,
            records = records,
            loaded = true,
            categoryEmoji = categories.firstOrNull { it.id == status?.categoryId }?.emoji.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ItemDetailUiState())

    fun recordReplacement(date: LocalDate, price: Long?, vendor: String?, photoUri: String?, memo: String?) {
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

    fun updateRecord(existing: ReplacementRecordEntity, date: LocalDate, price: Long?, vendor: String?, photoUri: String?, memo: String?) {
        viewModelScope.launch {
            replacementRecordRepository.update(
                existing.copy(date = date, price = price, vendor = vendor, photoUri = photoUri, memo = memo)
            )
        }
    }

    fun deleteRecord(record: ReplacementRecordEntity) {
        viewModelScope.launch {
            replacementRecordRepository.delete(record)
            record.photoUri?.let { path -> withContext(Dispatchers.IO) { photoStorage.deletePhoto(path) } }
        }
    }

    suspend fun pickPhoto(uri: Uri): String? = photoStorage.copyToInternalStorage(uri)

    fun discardPhoto(path: String) {
        viewModelScope.launch(Dispatchers.IO) { photoStorage.deletePhoto(path) }
    }
}

class ItemDetailViewModelFactory(
    private val consumableItemRepository: ConsumableItemRepository,
    private val replacementRecordRepository: ReplacementRecordRepository,
    private val photoStorage: PhotoStorage,
    private val categoryRepository: CategoryRepository,
    private val itemId: Long,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ItemDetailViewModel(consumableItemRepository, replacementRecordRepository, photoStorage, categoryRepository, itemId) as T
}

private val RING_TRACK_COLOR = Color(0xFFE6EDED)
private val DUE_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
private val RECORD_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")

@Composable
fun ItemDetailScreen(
    viewModel: ItemDetailViewModel,
    onEditItem: () -> Unit,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showRecordDialog by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<ReplacementRecordEntity?>(null) }
    var recordPendingDelete by remember { mutableStateOf<ReplacementRecordEntity?>(null) }
    var expandedPhotoPath by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    val status = uiState.status

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

    if (showRecordDialog && status != null) {
        ReplacementRecordSheet(
            item = status.item,
            assetName = status.assetName,
            categoryEmoji = uiState.categoryEmoji,
            otherRecordDates = uiState.records.map { it.date },
            onDismiss = { showRecordDialog = false },
            onSave = { input ->
                viewModel.recordReplacement(input.date, input.price, input.vendor, input.photoUri, input.memo)
                showRecordDialog = false
            },
            onPickPhoto = { uri -> viewModel.pickPhoto(uri) },
            onDiscardPhoto = { path -> viewModel.discardPhoto(path) },
        )
    }

    editingRecord?.let { target ->
        if (status != null) {
            ReplacementRecordSheet(
                item = status.item,
                assetName = status.assetName,
                categoryEmoji = uiState.categoryEmoji,
                otherRecordDates = uiState.records.filter { it.id != target.id }.map { it.date },
                initial = target,
                onDismiss = { editingRecord = null },
                onSave = { input ->
                    viewModel.updateRecord(target, input.date, input.price, input.vendor, input.photoUri, input.memo)
                    editingRecord = null
                },
                onPickPhoto = { uri -> viewModel.pickPhoto(uri) },
                onDiscardPhoto = { path -> viewModel.discardPhoto(path) },
            )
        }
    }

    recordPendingDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { recordPendingDelete = null },
            title = { Text("이 기록을 삭제할까요?") },
            text = { Text("삭제된 기록은 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRecord(record)
                    recordPendingDelete = null
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { recordPendingDelete = null }) { Text("취소") } },
        )
    }

    Scaffold(
        topBar = {
            // 시안(ItemDetail.dc.html): 아이콘 뒤로가기 + 대상 이름(작게) + 오른쪽 "수정".
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", modifier = Modifier.size(22.dp))
                }
                Text(status?.assetName.orEmpty(), modifier = Modifier.weight(1f), fontSize = 13.sp, color = TextSecondary)
                if (status != null) {
                    TextButton(onClick = onEditItem) {
                        Text("수정", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                    }
                }
            }
        },
    ) { padding ->
        if (!uiState.loaded) {
            Text("불러오는 중...", modifier = Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        if (status == null) {
            Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
                Text("삭제되었거나 존재하지 않는 항목입니다")
                Spacer(Modifier.height(12.dp))
                Button(onClick = onBack) { Text("뒤로 가기") }
            }
            return@Scaffold
        }
        val item = status.item
        val nextDueDate = DueDateCalculator.nextDueDate(
            itemCreatedAt = item.createdAt,
            cycleDays = item.cycleDays,
            latestRecordDate = status.latestRecordDate,
            monthlyDueDay = item.monthlyDueDay,
            yearlyDueMonth = item.yearlyDueMonth,
            yearlyDueDay = item.yearlyDueDay,
            yearlyDueIsLunar = item.isYearlyLunar == true,
        )
        val daysUntilDue = DueDateCalculator.daysUntilDue(nextDueDate, LocalDate.now())
        val records = uiState.records

        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HeroCard(status = status, nextDueDate = nextDueDate, daysUntilDue = daysUntilDue)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(TealPrimary)
                        .clickable { showRecordDialog = true },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("이력 기록하기", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                val count = StatValue(records.size.toString(), "회")
                val totalCost = formatTotalCost(records.mapNotNull { it.price }.takeIf { it.isNotEmpty() }?.sum())
                val averageInterval = formatAverageInterval(averageIntervalDays(records.map { it.date }))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("기록", count, Modifier.weight(1f))
                    StatCard("누적 비용", totalCost, Modifier.weight(1f))
                    StatCard("실제 평균 간격", averageInterval, Modifier.weight(1f))
                }

                Text("교체/구매 이력", fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                if (records.isEmpty()) {
                    Text("아직 기록된 이력이 없습니다.", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 4.dp))
                } else {
                    val cardShape = RoundedCornerShape(18.dp)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(cardShape)
                            .background(SurfaceWhite)
                            .border(1.dp, CardBorder, cardShape),
                    ) {
                        records.forEachIndexed { index, record ->
                            if (index > 0) HorizontalDivider(color = RowDivider)
                            RecordRow(
                                record = record,
                                onPhotoClick = { expandedPhotoPath = it },
                                onEdit = { editingRecord = record },
                                onDelete = { recordPendingDelete = record },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            MoreBelowIndicator(
                visible = scrollState.canScrollForward,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun HeroCard(status: ConsumableItemWithStatus, nextDueDate: LocalDate, daysUntilDue: Long) {
    val item = status.item
    val band = dueBandFor(daysUntilDue, item.reminderLeadDays)
    val dStyle = dDayStyle(daysUntilDue, item.reminderLeadDays)
    val accent = progressBarColor(band)
    val ddayColor = if (band == DueBand.OVERDUE) OrangeUrgent else dStyle.textColor
    val totalDays = cycleLengthDays(item)
    val fraction = progressFraction(daysUntilDue, totalDays)
    // 매달/매년 고정 모드는 한 주기 길이를 평균값으로 근사하므로 "N일 / M일" 글자는 간격 주기에서만 보여준다.
    val showElapsed = item.monthlyDueDay == null && item.yearlyDueMonth == null
    val elapsedDays = (totalDays - daysUntilDue).coerceIn(0, totalDays.toLong())
    val cardShape = RoundedCornerShape(22.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(SurfaceWhite)
            .border(1.dp, CardBorder, cardShape)
            .padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 10.dp.toPx()
                val arcTopLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                drawArc(
                    color = RING_TRACK_COLOR, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    topLeft = arcTopLeft, size = arcSize, style = Stroke(width = strokeWidth),
                )
                if (fraction > 0f) {
                    drawArc(
                        color = accent, startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false,
                        topLeft = arcTopLeft, size = arcSize, style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    dStyle.text,
                    fontFamily = NumberFontFamily,
                    fontSize = 32.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = ddayColor,
                )
                if (showElapsed) {
                    Text("${elapsedDays}일 / ${totalDays}일", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row {
                Text("다음 예정일 ", fontSize = 13.sp, color = Color(0xFF3A3F46))
                Text(nextDueDate.format(DUE_DATE_FORMAT), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3A3F46))
            }
            Text(formatCycleLabel(item), fontSize = 13.sp, color = TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Notifications, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(13.dp))
                val milestones = ReminderTargetSelector.reminderMilestones(item.reminderLeadDays)
                val reminderText = if (item.reminderLeadDays <= 0) "당일 알림" else milestones.joinToString("·") + "일 전 알림"
                Text(reminderText, fontSize = 12.sp, color = TextSecondary)
            }
            if (!item.memo.isNullOrBlank()) {
                Text(item.memo, fontSize = 12.sp, color = TextSecondary, maxLines = 2)
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: StatValue, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(SurfaceWhite)
            .border(1.dp, CardBorder, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value.number, fontFamily = NumberFontFamily, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (value.unit.isNotEmpty()) Text(value.unit, fontSize = 12.sp, modifier = Modifier.padding(start = 1.dp, bottom = 2.dp))
        }
    }
}

@Composable
private fun RecordRow(
    record: ReplacementRecordEntity,
    onPhotoClick: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 4.dp, end = 2.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(6.dp).background(TealPrimary, CircleShape))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                Text(
                    record.date.format(RECORD_DATE_FORMAT),
                    fontFamily = NumberFontFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                record.price?.let { Text("%,d원".format(it), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3A3F46)) }
            }
            if (!record.vendor.isNullOrBlank()) Text(record.vendor, fontSize = 12.sp, color = TextSecondary)
            if (!record.memo.isNullOrBlank()) Text(record.memo, fontSize = 12.sp, color = TextSecondary, maxLines = 2)
        }
        record.photoUri?.let { path ->
            LocalPhotoThumbnail(
                path = path,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).clickable { onPhotoClick(path) },
                contentScale = ContentScale.Crop,
            )
        }
        RowMoreMenu(onEdit = onEdit, onDelete = onDelete)
    }
}
