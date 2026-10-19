package com.khomenote.ui.asset

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.khomenote.data.local.dto.AssetWithCategory
import com.khomenote.data.local.entity.CategoryEntity
import com.khomenote.data.repository.AssetRepository
import com.khomenote.data.repository.CategoryRepository
import com.khomenote.data.repository.ConsumableItemRepository
import com.khomenote.data.repository.RepairRecordRepository
import com.khomenote.ui.common.KFilterChip
import com.khomenote.ui.common.MoreBelowIndicator
import com.khomenote.ui.common.PillAddButton
import com.khomenote.ui.common.dragElevation
import com.khomenote.ui.common.dragHandle
import com.khomenote.ui.common.rememberDragDropListState
import com.khomenote.ui.home.dDayStyle
import com.khomenote.ui.theme.BackgroundGray
import com.khomenote.ui.theme.CardBorder
import com.khomenote.ui.theme.NumberFontFamily
import com.khomenote.ui.theme.SurfaceWhite
import com.khomenote.ui.theme.TextFaint
import com.khomenote.ui.theme.TextSecondary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class AssetListViewModel(
    private val assetRepository: AssetRepository,
    consumableItemRepository: ConsumableItemRepository,
    repairRecordRepository: RepairRecordRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {
    val assets: StateFlow<List<AssetWithCategory>> = assetRepository.getAllWithCategory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = categoryRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 카드마다 보여줄 "주기성 N · 일회성 M"과 가장 가까운 작업 (대상 id -> 요약). */
    internal val summaries: StateFlow<Map<Long, AssetCardSummary>> = combine(
        consumableItemRepository.getAllWithStatus(),
        repairRecordRepository.getAll(),
        assetRepository.getAll(),
    ) { statuses, records, assetList ->
        val today = LocalDate.now()
        assetList.associate { it.id to summarizeAsset(it.id, statuses, records, today) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // remember{}로 화면(Composable)에만 두면 다른 화면으로 넘어갔다가 뒤로 왔을 때
    // 화면이 다시 조립되며 필터가 초기화된다 — ViewModel에 둬서 화면을 나갔다 와도 유지되게 한다.
    private val _categoryFilter = MutableStateFlow<Long?>(null)
    val categoryFilter: StateFlow<Long?> = _categoryFilter

    fun setCategoryFilter(categoryId: Long?) {
        _categoryFilter.value = categoryId
    }

    fun reorder(orderedAssets: List<AssetWithCategory>) {
        viewModelScope.launch { assetRepository.reorder(orderedAssets.map { it.asset }) }
    }
}

class AssetListViewModelFactory(
    private val assetRepository: AssetRepository,
    private val consumableItemRepository: ConsumableItemRepository,
    private val repairRecordRepository: RepairRecordRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AssetListViewModel(assetRepository, consumableItemRepository, repairRecordRepository, categoryRepository) as T
}

@Composable
fun AssetListScreen(
    viewModel: AssetListViewModel,
    onAddAsset: () -> Unit,
    onAssetClick: (Long) -> Unit,
) {
    val assets by viewModel.assets.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.categoryFilter.collectAsStateWithLifecycle()
    val summaries by viewModel.summaries.collectAsStateWithLifecycle()

    // 드래그 중에는 화면에 보이는 순서(displayAssets)를 직접 바꾸고, 드래그가
    // 끝나야 DB에 저장한다. Flow(assets)가 새로 값을 흘려보내도 드래그 중에는
    // 그걸로 덮어쓰지 않아야 드래그 도중 화면이 튀지 않는다.
    // displayAssets는 카테고리 필터와 무관하게 항상 전체 자산의 순서를 담는다 —
    // 필터링된 화면에서 드래그해도 전체 정렬 순서(sortOrder)가 깨지지 않게 하기 위함.
    var displayAssets by remember { mutableStateOf(assets) }
    val visibleAssets = remember(displayAssets, categoryFilter) {
        displayAssets.filter { categoryFilter == null || it.asset.categoryId == categoryFilter }
    }
    val listState = rememberLazyListState()
    val dragState = rememberDragDropListState(
        listState = listState,
        itemCount = { visibleAssets.size },
        onMove = { from, to ->
            // from/to는 필터링된 화면 안에서의 위치 — 전체 리스트 안에서 실제 위치를 찾아 옮긴다.
            val actualFrom = displayAssets.indexOfFirst { it.asset.id == visibleAssets[from].asset.id }
            val actualTo = displayAssets.indexOfFirst { it.asset.id == visibleAssets[to].asset.id }
            if (actualFrom != -1 && actualTo != -1) {
                displayAssets = displayAssets.toMutableList().apply { add(actualTo, removeAt(actualFrom)) }
            }
        },
        onReorderFinished = { viewModel.reorder(displayAssets) },
    )
    LaunchedEffect(assets) {
        if (dragState.draggingItemIndex == null) displayAssets = assets
    }

    Scaffold(
        topBar = {
            // 시안(Assets.dc.html)은 TopAppBar 대신 큰 제목 + 오른쪽 "대상 추가" 알약 버튼을 쓴다.
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "모아보기",
                    modifier = Modifier.weight(1f),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                PillAddButton(label = "대상 추가", onClick = onAddAsset)
            }
        },
    ) { padding ->
        if (displayAssets.isEmpty()) {
            Text(
                "등록된 자산이 없습니다. \"대상 추가\" 버튼을 눌러 추가하세요.",
                modifier = Modifier.padding(padding).padding(16.dp),
            )
            return@Scaffold
        }
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KFilterChip(
                    selected = categoryFilter == null,
                    label = "전체 ${displayAssets.size}",
                    onClick = { viewModel.setCategoryFilter(null) },
                )
                categories.forEach { category ->
                    val count = displayAssets.count { it.asset.categoryId == category.id }
                    KFilterChip(
                        selected = categoryFilter == category.id,
                        label = "${category.emoji} ${category.name} $count",
                        onClick = { viewModel.setCategoryFilter(category.id) },
                    )
                }
            }
            if (visibleAssets.isEmpty()) {
                Text(
                    "해당 카테고리에 등록된 자산이 없습니다.",
                    modifier = Modifier.padding(16.dp),
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    itemsIndexed(visibleAssets, key = { _, asset -> asset.asset.id }) { index, asset ->
                        val summary = summaries[asset.asset.id] ?: AssetCardSummary.EMPTY
                        // 카드 사이 간격은 Arrangement.spacedBy 대신 항목 안쪽 패딩으로 둔다 — 드래그 재정렬이
                        // 항목 높이로 위치를 계산하므로 간격까지 항목 크기에 포함돼야 어긋나지 않는다.
                        Box(modifier = Modifier.dragElevation(index, dragState).padding(bottom = 10.dp)) {
                            val cardShape = RoundedCornerShape(18.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(cardShape)
                                    .background(SurfaceWhite)
                                    .border(1.dp, CardBorder, cardShape)
                                    .clickable { onAssetClick(asset.asset.id) }
                                    .padding(start = 14.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(BackgroundGray),
                                    contentAlignment = Alignment.Center,
                                ) { Text(asset.categoryEmoji, fontSize = 20.sp) }
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        asset.asset.name,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(cardCountsLabel(summary), fontSize = 12.sp, color = TextSecondary)
                                    summary.nearest?.let { nearest ->
                                        val dStyle = dDayStyle(nearest.daysUntilDue, nearest.reminderLeadDays)
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                dStyle.text,
                                                fontFamily = NumberFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = dStyle.textColor,
                                                modifier = Modifier
                                                    .background(dStyle.background, RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                                            )
                                            Text(
                                                nearest.name,
                                                fontSize = 12.sp,
                                                color = Color(0xFF3A3F46),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                                Text(
                                    "≡",
                                    modifier = Modifier.dragHandle(index, dragState).padding(horizontal = 10.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TextFaint,
                                )
                            }
                        }
                    }
                }
                MoreBelowIndicator(
                    listState = listState,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                )
            }
        }
    }
}
