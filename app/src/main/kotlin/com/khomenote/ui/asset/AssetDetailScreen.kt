package com.khomenote.ui.asset

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.khomenote.data.local.entity.AssetEntity
import com.khomenote.data.local.entity.ConsumableItemEntity
import com.khomenote.data.local.entity.label
import com.khomenote.data.repository.AssetRepository
import com.khomenote.data.repository.ConsumableItemRepository
import com.khomenote.ui.common.dragElevation
import com.khomenote.ui.common.dragHandle
import com.khomenote.ui.common.rememberDragDropListState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AssetDetailUiState(
    val asset: AssetEntity? = null,
    val items: List<ConsumableItemEntity> = emptyList(),
)

class AssetDetailViewModel(
    assetRepository: AssetRepository,
    private val consumableItemRepository: ConsumableItemRepository,
    assetId: Long,
) : ViewModel() {
    val uiState: StateFlow<AssetDetailUiState> = combine(
        assetRepository.getById(assetId),
        consumableItemRepository.getByAsset(assetId),
    ) { asset, items -> AssetDetailUiState(asset, items) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AssetDetailUiState())

    fun deleteItem(item: ConsumableItemEntity) {
        viewModelScope.launch { consumableItemRepository.delete(item) }
    }

    fun reorderItems(orderedItems: List<ConsumableItemEntity>) {
        viewModelScope.launch { consumableItemRepository.reorder(orderedItems) }
    }
}

class AssetDetailViewModelFactory(
    private val assetRepository: AssetRepository,
    private val consumableItemRepository: ConsumableItemRepository,
    private val assetId: Long,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AssetDetailViewModel(assetRepository, consumableItemRepository, assetId) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetDetailScreen(
    viewModel: AssetDetailViewModel,
    onEditAsset: () -> Unit,
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(asset?.name ?: "자산") },
                navigationIcon = { TextButton(onClick = onBack) { Text("< 뒤로") } },
                actions = { TextButton(onClick = onEditAsset) { Text("수정") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAddItem) { Text("소모품 추가") }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (asset != null) {
                Text(
                    asset.category.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (displayItems.isEmpty()) {
                Text(
                    "등록된 소모품이 없습니다. \"소모품 추가\" 버튼을 눌러 추가하세요.",
                    modifier = Modifier.padding(16.dp).weight(1f),
                )
            } else {
                LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                    itemsIndexed(displayItems, key = { _, item -> item.id }) { index, item ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .dragElevation(index, dragState)
                                .clickable { onItemClick(item.id) }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row {
                                Text(
                                    "≡",
                                    modifier = Modifier.dragHandle(index, dragState).padding(end = 12.dp),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                                Column {
                                    Text(item.name, fontWeight = FontWeight.Bold)
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
            }
            val memo = asset?.memo
            if (!memo.isNullOrBlank()) {
                HorizontalDivider()
                Text(
                    memo,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}
