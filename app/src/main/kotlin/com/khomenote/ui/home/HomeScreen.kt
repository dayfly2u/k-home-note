package com.khomenote.ui.home

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.khomenote.data.local.dto.ConsumableItemWithStatus
import com.khomenote.data.local.entity.AssetCategory
import com.khomenote.data.local.entity.ReplacementRecordEntity
import com.khomenote.data.local.entity.label
import com.khomenote.data.repository.ConsumableItemRepository
import com.khomenote.data.repository.ReplacementRecordRepository
import com.khomenote.domain.DueDateCalculator
import com.khomenote.photo.PhotoStorage
import com.khomenote.ui.item.ReplacementRecordDialog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HomeItemUiModel(
    val status: ConsumableItemWithStatus,
    val nextDueDate: LocalDate,
    val daysUntilDue: Long,
)

class HomeViewModel(
    private val consumableItemRepository: ConsumableItemRepository,
    private val replacementRecordRepository: ReplacementRecordRepository,
    private val photoStorage: PhotoStorage,
) : ViewModel() {

    // 정렬 기준이 nextDueDate 오름차순이라, 연체(과거 날짜) 항목이 자연히
    // 맨 앞에 온다 — 화면에서 별도로 "연체는 위로" 정렬 로직을 두지 않아도 된다.
    val items: StateFlow<List<HomeItemUiModel>> = consumableItemRepository.getAllWithStatus()
        .map { list ->
            list.map { status ->
                val nextDueDate = DueDateCalculator.nextDueDate(
                    itemCreatedAt = status.item.createdAt,
                    cycleDays = status.item.cycleDays,
                    latestRecordDate = status.latestRecordDate,
                )
                HomeItemUiModel(
                    status = status,
                    nextDueDate = nextDueDate,
                    daysUntilDue = DueDateCalculator.daysUntilDue(nextDueDate, LocalDate.now()),
                )
            }.sortedBy { it.nextDueDate }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
        photoStorage.deletePhoto(path)
    }
}

class HomeViewModelFactory(
    private val consumableItemRepository: ConsumableItemRepository,
    private val replacementRecordRepository: ReplacementRecordRepository,
    private val photoStorage: PhotoStorage,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        HomeViewModel(consumableItemRepository, replacementRecordRepository, photoStorage) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onManageAssets: () -> Unit,
    onItemClick: (Long) -> Unit,
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    var categoryFilter by remember { mutableStateOf<AssetCategory?>(null) }
    var recordDialogItemId by remember { mutableStateOf<Long?>(null) }

    val filteredItems = remember(items, categoryFilter) {
        items.filter { categoryFilter == null || it.status.assetCategory == categoryFilter }
    }

    recordDialogItemId?.let { itemId ->
        ReplacementRecordDialog(
            onDismiss = { recordDialogItemId = null },
            onSave = { input ->
                viewModel.recordReplacement(itemId, input.date, input.price, input.vendor, input.photoUri, input.memo)
                recordDialogItemId = null
            },
            onPickPhoto = { uri -> viewModel.pickPhoto(uri) },
            onDiscardPhoto = { path -> viewModel.discardPhoto(path) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("정비 노트") },
                actions = { TextButton(onClick = onManageAssets) { Text("자산 관리") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = categoryFilter == null, onClick = { categoryFilter = null }, label = { Text("전체") })
                FilterChip(
                    selected = categoryFilter == AssetCategory.HOME,
                    onClick = { categoryFilter = AssetCategory.HOME },
                    label = { Text(AssetCategory.HOME.label) },
                )
                FilterChip(
                    selected = categoryFilter == AssetCategory.CAR,
                    onClick = { categoryFilter = AssetCategory.CAR },
                    label = { Text(AssetCategory.CAR.label) },
                )
            }
            if (filteredItems.isEmpty()) {
                Text(
                    "등록된 소모품이 없습니다. \"자산 관리\"에서 자산과 소모품을 먼저 추가하세요.",
                    modifier = Modifier.padding(16.dp),
                )
                return@Scaffold
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredItems, key = { it.status.item.id }) { uiModel ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onItemClick(uiModel.status.item.id) }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${uiModel.status.assetName} · ${uiModel.status.item.name}",
                                fontWeight = FontWeight.Bold,
                            )
                            val dDayText = when {
                                uiModel.daysUntilDue < 0 -> "${-uiModel.daysUntilDue}일 지남"
                                uiModel.daysUntilDue == 0L -> "오늘"
                                else -> "D-${uiModel.daysUntilDue}"
                            }
                            Text(
                                dDayText,
                                color = if (uiModel.daysUntilDue <= 0) Color.Red else MaterialTheme.colorScheme.outline,
                                fontWeight = if (uiModel.daysUntilDue <= 0) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                        TextButton(onClick = { recordDialogItemId = uiModel.status.item.id }) { Text("교체 완료") }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
