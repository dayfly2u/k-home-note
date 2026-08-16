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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.khomenote.data.local.dto.AssetWithCategory
import com.khomenote.data.repository.AssetRepository
import com.khomenote.data.repository.ConsumableItemRepository
import com.khomenote.ui.common.dragElevation
import com.khomenote.ui.common.dragHandle
import com.khomenote.ui.common.rememberDragDropListState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AssetListViewModel(
    private val assetRepository: AssetRepository,
    private val consumableItemRepository: ConsumableItemRepository,
) : ViewModel() {
    val assets: StateFlow<List<AssetWithCategory>> = assetRepository.getAllWithCategory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(asset: AssetWithCategory) {
        viewModelScope.launch { assetRepository.delete(asset.asset) }
    }

    suspend fun hasItems(assetId: Long): Boolean =
        consumableItemRepository.getByAsset(assetId).first().isNotEmpty()

    fun reorder(orderedAssets: List<AssetWithCategory>) {
        viewModelScope.launch { assetRepository.reorder(orderedAssets.map { it.asset }) }
    }
}

class AssetListViewModelFactory(
    private val assetRepository: AssetRepository,
    private val consumableItemRepository: ConsumableItemRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AssetListViewModel(assetRepository, consumableItemRepository) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetListScreen(
    viewModel: AssetListViewModel,
    onAddAsset: () -> Unit,
    onAssetClick: (Long) -> Unit,
    onEditAsset: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val assets by viewModel.assets.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<AssetWithCategory?>(null) }
    var blockedDelete by remember { mutableStateOf<AssetWithCategory?>(null) }
    val scope = rememberCoroutineScope()

    // 드래그 중에는 화면에 보이는 순서(displayAssets)를 직접 바꾸고, 드래그가
    // 끝나야 DB에 저장한다. Flow(assets)가 새로 값을 흘려보내도 드래그 중에는
    // 그걸로 덮어쓰지 않아야 드래그 도중 화면이 튀지 않는다.
    var displayAssets by remember { mutableStateOf(assets) }
    val listState = rememberLazyListState()
    val dragState = rememberDragDropListState(
        listState = listState,
        itemCount = { displayAssets.size },
        onMove = { from, to ->
            displayAssets = displayAssets.toMutableList().apply { add(to, removeAt(from)) }
        },
        onReorderFinished = { viewModel.reorder(displayAssets) },
    )
    LaunchedEffect(assets) {
        if (dragState.draggingItemIndex == null) displayAssets = assets
    }

    fun requestDelete(asset: AssetWithCategory) {
        scope.launch {
            if (viewModel.hasItems(asset.asset.id)) {
                blockedDelete = asset
            } else {
                pendingDelete = asset
            }
        }
    }

    pendingDelete?.let { asset ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("자산을 삭제할까요?") },
            text = { Text("\"${asset.asset.name}\"이 삭제되며 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(asset)
                    pendingDelete = null
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("취소") } },
        )
    }

    blockedDelete?.let { asset ->
        AlertDialog(
            onDismissRequest = { blockedDelete = null },
            title = { Text("삭제할 수 없습니다") },
            text = { Text("\"${asset.asset.name}\"에 등록된 소모품이 있습니다. 먼저 소모품을 모두 삭제한 뒤 다시 시도해주세요.") },
            confirmButton = { TextButton(onClick = { blockedDelete = null }) { Text("확인") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("자산 관리") },
                navigationIcon = { TextButton(onClick = onBack) { Text("< 뒤로") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAddAsset) { Text("자산 추가") }
        },
    ) { padding ->
        if (displayAssets.isEmpty()) {
            Text(
                "등록된 자산이 없습니다. \"자산 추가\" 버튼을 눌러 추가하세요.",
                modifier = Modifier.padding(padding).padding(16.dp),
            )
            return@Scaffold
        }
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                "각 자산의 세부 소모품을 추가하려면 자산이름을 누르세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Text(
                "손잡이(≡)를 1초간 꾹 눌렀다가 위아래로 드래그하면 순서를 바꿀 수 있어요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                itemsIndexed(displayAssets, key = { _, asset -> asset.asset.id }) { index, asset ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .dragElevation(index, dragState)
                            .clickable { onAssetClick(asset.asset.id) }
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
                                Text("${asset.categoryEmoji} ${asset.categoryName}")
                                Text(asset.asset.name, fontWeight = FontWeight.Bold)
                                if (!asset.asset.memo.isNullOrBlank()) {
                                    Text(
                                        asset.asset.memo,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                        }
                        Row {
                            TextButton(onClick = { onEditAsset(asset.asset.id) }) { Text("수정") }
                            TextButton(onClick = { requestDelete(asset) }) { Text("삭제") }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
