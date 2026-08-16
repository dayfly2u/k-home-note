package com.khomenote.ui.item

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.khomenote.data.local.dto.ConsumableItemWithStatus
import com.khomenote.data.local.entity.ReplacementRecordEntity
import com.khomenote.data.repository.ConsumableItemRepository
import com.khomenote.data.repository.ReplacementRecordRepository
import com.khomenote.domain.DueDateCalculator
import com.khomenote.photo.PhotoStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 로컬 파일 경로를 BitmapFactory로 직접 디코드해서 보여주는 작은 썸네일.
 * 원격 URL을 다루지 않으므로 별도 이미지 로딩 라이브러리를 쓰지 않는다.
 */
@Composable
fun LocalPhotoThumbnail(path: String, modifier: Modifier = Modifier) {
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(path)?.asImageBitmap()
        }
    }
    bitmap?.let {
        Image(bitmap = it, contentDescription = null, modifier = modifier)
    }
}

data class ItemDetailUiState(
    val status: ConsumableItemWithStatus? = null,
    val records: List<ReplacementRecordEntity> = emptyList(),
    val loaded: Boolean = false,
)

class ItemDetailViewModel(
    private val consumableItemRepository: ConsumableItemRepository,
    private val replacementRecordRepository: ReplacementRecordRepository,
    private val photoStorage: PhotoStorage,
    private val itemId: Long,
) : ViewModel() {

    val uiState: StateFlow<ItemDetailUiState> = combine(
        consumableItemRepository.getWithStatusById(itemId),
        replacementRecordRepository.getByItem(itemId),
    ) { status, records -> ItemDetailUiState(status, records, loaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ItemDetailUiState())

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
    private val itemId: Long,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ItemDetailViewModel(consumableItemRepository, replacementRecordRepository, photoStorage, itemId) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    viewModel: ItemDetailViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showRecordDialog by remember { mutableStateOf(false) }
    var recordPendingDelete by remember { mutableStateOf<ReplacementRecordEntity?>(null) }
    val status = uiState.status

    if (showRecordDialog) {
        ReplacementRecordDialog(
            onDismiss = { showRecordDialog = false },
            onSave = { input ->
                viewModel.recordReplacement(input.date, input.price, input.vendor, input.photoUri, input.memo)
                showRecordDialog = false
            },
            onPickPhoto = { uri -> viewModel.pickPhoto(uri) },
            onDiscardPhoto = { path -> viewModel.discardPhoto(path) },
        )
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
            TopAppBar(
                title = { Text(status?.item?.name ?: "소모품") },
                navigationIcon = { TextButton(onClick = onBack) { Text("< 뒤로") } },
            )
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
        val nextDueDate = DueDateCalculator.nextDueDate(
            itemCreatedAt = status.item.createdAt,
            cycleDays = status.item.cycleDays,
            latestRecordDate = status.latestRecordDate,
        )
        val daysUntilDue = DueDateCalculator.daysUntilDue(nextDueDate, LocalDate.now())

        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("${status.assetName} · ${status.item.name}", fontWeight = FontWeight.Bold)
            Text("주기: ${status.item.cycleDays}일 / 알림: ${status.item.reminderLeadDays}일 전")
            Text(
                "다음 예정일: $nextDueDate" + if (daysUntilDue <= 0) " (이미 지남)" else " (D-$daysUntilDue)",
                color = if (daysUntilDue <= 0) Color.Red else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            if (!status.item.memo.isNullOrBlank()) {
                Text(status.item.memo, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { showRecordDialog = true }) { Text("구매이력 기록하기") }
            Spacer(Modifier.height(16.dp))
            Text("교체/구매 이력", fontWeight = FontWeight.Bold)
            if (uiState.records.isEmpty()) {
                Text("아직 기록된 이력이 없습니다.", style = MaterialTheme.typography.bodySmall)
            }
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(uiState.records, key = { it.id }) { record ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(modifier = Modifier.weight(1f)) {
                            if (record.photoUri != null) {
                                LocalPhotoThumbnail(
                                    path = record.photoUri,
                                    modifier = Modifier.size(48.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Column {
                                Text(record.date.toString(), fontWeight = FontWeight.Bold)
                                val priceText = record.price?.let { "%,d원".format(it) }
                                val infoLine = listOfNotNull(priceText, record.vendor).joinToString(" · ")
                                if (infoLine.isNotBlank()) Text(infoLine, style = MaterialTheme.typography.bodySmall)
                                if (!record.memo.isNullOrBlank()) Text(record.memo, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        TextButton(onClick = { recordPendingDelete = record }) { Text("삭제") }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
