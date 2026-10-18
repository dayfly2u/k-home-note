package com.khomenote.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.khomenote.data.local.dto.ConsumableItemWithStatus
import com.khomenote.data.local.dto.RepairRecordWithAsset
import com.khomenote.data.local.entity.CategoryEntity
import com.khomenote.data.repository.CategoryRepository
import com.khomenote.data.repository.ConsumableItemRepository
import com.khomenote.data.repository.RepairRecordRepository
import com.khomenote.domain.DueDateCalculator
import com.khomenote.ui.common.MoreBelowIndicator
import com.khomenote.ui.theme.OnceTagBorder
import com.khomenote.ui.theme.OrangeUrgent
import com.khomenote.ui.theme.OrangeUrgentContainer
import com.khomenote.ui.theme.OrangeUrgentText
import com.khomenote.ui.theme.NumberFontFamily
import com.khomenote.ui.theme.TealContainer
import com.khomenote.ui.theme.TealPrimary
import com.khomenote.ui.theme.TextFaint
import com.khomenote.ui.theme.TextSecondary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** 홈 대시보드 한 줄 — 주기성작업(소모품)과 일회성작업(알림 받기를 켠 것만)을 한 목록에 합쳐서 보여준다. */
sealed interface HomeRowUiModel {
    val dueDate: LocalDate
    val daysUntilDue: Long
    val categoryId: Long
    val assetName: String
    val title: String
    val reminderLeadDays: Int

    data class Recurring(
        val status: ConsumableItemWithStatus,
        override val dueDate: LocalDate,
        override val daysUntilDue: Long,
    ) : HomeRowUiModel {
        override val categoryId get() = status.categoryId
        override val assetName get() = status.assetName
        override val title get() = status.item.name
        override val reminderLeadDays get() = status.item.reminderLeadDays
    }

    data class OneTime(
        val record: RepairRecordWithAsset,
        override val dueDate: LocalDate,
        override val daysUntilDue: Long,
    ) : HomeRowUiModel {
        override val categoryId get() = record.categoryId
        override val assetName get() = record.assetName
        override val title get() = record.record.name
        override val reminderLeadDays get() = record.record.reminderLeadDays ?: 0
    }
}

class HomeViewModel(
    private val consumableItemRepository: ConsumableItemRepository,
    private val repairRecordRepository: RepairRecordRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {

    val categories: StateFlow<List<CategoryEntity>> = categoryRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // remember{}로 화면(Composable)에만 두면 항목 클릭 후 뒤로 왔을 때 화면이
    // 다시 조립되며 필터가 초기화된다 — ViewModel에 둬서 화면을 나갔다 와도 유지되게 한다.
    private val _categoryFilter = MutableStateFlow<Long?>(null)
    val categoryFilter: StateFlow<Long?> = _categoryFilter

    fun setCategoryFilter(categoryId: Long?) {
        _categoryFilter.value = categoryId
    }

    // 정렬 기준이 dueDate 오름차순이라, 연체(과거 날짜) 항목이 자연히
    // 맨 앞에 온다 — 화면에서 별도로 "연체는 위로" 정렬 로직을 두지 않아도 된다.
    val items: StateFlow<List<HomeRowUiModel>> = combine(
        consumableItemRepository.getAllWithStatus(),
        repairRecordRepository.getPendingReminders(),
    ) { statuses, oneTimeTasks ->
        val today = LocalDate.now()
        val recurring = statuses.map { status ->
            val nextDueDate = DueDateCalculator.nextDueDate(
                itemCreatedAt = status.item.createdAt,
                cycleDays = status.item.cycleDays,
                latestRecordDate = status.latestRecordDate,
                monthlyDueDay = status.item.monthlyDueDay,
                yearlyDueMonth = status.item.yearlyDueMonth,
                yearlyDueDay = status.item.yearlyDueDay,
                yearlyDueIsLunar = status.item.isYearlyLunar == true,
            )
            HomeRowUiModel.Recurring(
                status = status,
                dueDate = nextDueDate,
                daysUntilDue = DueDateCalculator.daysUntilDue(nextDueDate, today),
            )
        }
        val oneTime = oneTimeTasks.map { withAsset ->
            HomeRowUiModel.OneTime(
                record = withAsset,
                dueDate = withAsset.record.date,
                daysUntilDue = DueDateCalculator.daysUntilDue(withAsset.record.date, today),
            )
        }
        (recurring + oneTime).sortedBy { it.dueDate }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

class HomeViewModelFactory(
    private val consumableItemRepository: ConsumableItemRepository,
    private val repairRecordRepository: RepairRecordRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        HomeViewModel(consumableItemRepository, repairRecordRepository, categoryRepository) as T
}

private data class DDayStyle(val text: String, val background: Color, val textColor: Color, val large: Boolean)

// 생활달력 리디자인(2026-10-18 redesign-brief.md 1장): 색을 청록 한 가지 + 급한 것만 주황으로 줄였다.
// 지난 날짜(연체)는 주황 바탕에 흰 글자로 "D+N". 그 외는 남은 날짜에 따라 소모품별 알림 설정일
// (reminderLeadDays) 이내는 연한주황, 31일 이하는 연한청록, 100일 이하/그 뒤는 바탕 없이 보조 글자색만
// 두 단계로 흐리게 — 급하지 않은 구간은 눈에 크게 띌 이유가 없어 폰트를 작게 둔다.
private fun dDayStyle(daysUntilDue: Long, reminderLeadDays: Int): DDayStyle = when {
    daysUntilDue < 0 -> DDayStyle("D+${-daysUntilDue}", OrangeUrgent, Color.White, large = true)
    daysUntilDue == 0L -> DDayStyle("오늘", OrangeUrgentContainer, OrangeUrgentText, large = true)
    daysUntilDue <= reminderLeadDays -> DDayStyle("D-$daysUntilDue", OrangeUrgentContainer, OrangeUrgentText, large = true)
    daysUntilDue <= 31 -> DDayStyle("D-$daysUntilDue", TealContainer, TealPrimary, large = true)
    daysUntilDue <= 100 -> DDayStyle("D-$daysUntilDue", Color.Transparent, TextSecondary, large = false)
    else -> DDayStyle("D-$daysUntilDue", Color.Transparent, TextFaint, large = false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onItemClick: (Long) -> Unit,
    onOneTimeTaskClick: (Long) -> Unit,
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.categoryFilter.collectAsStateWithLifecycle()

    val filteredItems = remember(items, categoryFilter) {
        items.filter { categoryFilter == null || it.categoryId == categoryFilter }
    }
    val categoryById = remember(categories) { categories.associateBy { it.id } }
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("K-Home-Note") })
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = categoryFilter == null,
                    onClick = { viewModel.setCategoryFilter(null) },
                    label = { Text("전체") },
                )
                categories.forEach { category ->
                    FilterChip(
                        selected = categoryFilter == category.id,
                        onClick = { viewModel.setCategoryFilter(category.id) },
                        label = { Text("${category.emoji} ${category.name}") },
                    )
                }
            }
            if (filteredItems.isEmpty()) {
                Text(
                    "등록된 작업이 없습니다. \"모아보기\"에서 자산과 주기성작업/일회성작업을 먼저 추가하세요.",
                    modifier = Modifier.padding(16.dp),
                )
                return@Scaffold
            }
            Text(
                "예정일순으로 정렬되어있습니다. \"한 번\" 표시는 일회성작업이에요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(
                        filteredItems,
                        key = {
                            when (it) {
                                is HomeRowUiModel.Recurring -> "recurring-${it.status.item.id}"
                                is HomeRowUiModel.OneTime -> "onetime-${it.record.record.id}"
                            }
                        },
                    ) { uiModel ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    when (uiModel) {
                                        is HomeRowUiModel.Recurring -> onItemClick(uiModel.status.item.id)
                                        is HomeRowUiModel.OneTime -> onOneTimeTaskClick(uiModel.record.record.assetId)
                                    }
                                }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val category = categoryById[uiModel.categoryId]
                                if (category != null) {
                                    Text(
                                        "${category.emoji} ${category.name}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${uiModel.assetName} · ${uiModel.title}",
                                        fontWeight = FontWeight.Bold,
                                    )
                                    if (uiModel is HomeRowUiModel.OneTime) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "한 번",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TealPrimary,
                                            modifier = Modifier
                                                .border(1.dp, OnceTagBorder, RoundedCornerShape(999.dp))
                                                .padding(horizontal = 6.dp, vertical = 1.dp),
                                        )
                                    }
                                }
                            }
                            val dStyle = dDayStyle(uiModel.daysUntilDue, uiModel.reminderLeadDays)
                            Box(
                                modifier = Modifier
                                    .background(dStyle.background, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    dStyle.text,
                                    color = dStyle.textColor,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = NumberFontFamily,
                                    style = if (dStyle.large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        HorizontalDivider()
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
