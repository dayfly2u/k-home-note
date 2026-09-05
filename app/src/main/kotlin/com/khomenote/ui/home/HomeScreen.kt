package com.khomenote.ui.home

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.khomenote.backup.BackupManager
import com.khomenote.data.local.dto.ConsumableItemWithStatus
import com.khomenote.data.local.entity.CategoryEntity
import com.khomenote.data.repository.CategoryRepository
import com.khomenote.data.repository.ConsumableItemRepository
import com.khomenote.domain.DueDateCalculator
import com.khomenote.ui.common.MoreBelowIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class HomeItemUiModel(
    val status: ConsumableItemWithStatus,
    val nextDueDate: LocalDate,
    val daysUntilDue: Long,
)

class HomeViewModel(
    private val consumableItemRepository: ConsumableItemRepository,
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

    // 정렬 기준이 nextDueDate 오름차순이라, 연체(과거 날짜) 항목이 자연히
    // 맨 앞에 온다 — 화면에서 별도로 "연체는 위로" 정렬 로직을 두지 않아도 된다.
    val items: StateFlow<List<HomeItemUiModel>> = consumableItemRepository.getAllWithStatus()
        .map { list ->
            list.map { status ->
                val nextDueDate = DueDateCalculator.nextDueDate(
                    itemCreatedAt = status.item.createdAt,
                    cycleDays = status.item.cycleDays,
                    latestRecordDate = status.latestRecordDate,
                    monthlyDueDay = status.item.monthlyDueDay,
                    yearlyDueMonth = status.item.yearlyDueMonth,
                    yearlyDueDay = status.item.yearlyDueDay,
                    yearlyDueIsLunar = status.item.isYearlyLunar == true,
                )
                HomeItemUiModel(
                    status = status,
                    nextDueDate = nextDueDate,
                    daysUntilDue = DueDateCalculator.daysUntilDue(nextDueDate, LocalDate.now()),
                )
            }.sortedBy { it.nextDueDate }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

class HomeViewModelFactory(
    private val consumableItemRepository: ConsumableItemRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        HomeViewModel(consumableItemRepository, categoryRepository) as T
}

private data class DDayStyle(val text: String, val background: Color, val textColor: Color, val large: Boolean)

// 지난 날짜(연체)는 진한빨강 바탕에 흰 글자로 "D+N". 그 외는 남은 날짜에 따라
// 소모품별 알림 설정일(reminderLeadDays) 이내는 연한빨강, 30일 이하는 연한녹색,
// 100일 이하는 연한파랑, 그보다 먼 날짜는 관심 밖이라 연한회색 배지로 보여준다.
// 급하지 않은 연한파랑/회색 구간은 눈에 크게 띌 이유가 없어 폰트를 작게 둔다.
private fun dDayStyle(daysUntilDue: Long, reminderLeadDays: Int): DDayStyle = when {
    daysUntilDue < 0 -> DDayStyle("D+${-daysUntilDue}", Color(0xFFB71C1C), Color.White, large = true)
    daysUntilDue == 0L -> DDayStyle("오늘", Color(0xFFFFCDD2), Color(0xFFC62828), large = true)
    daysUntilDue <= reminderLeadDays -> DDayStyle("D-$daysUntilDue", Color(0xFFFFCDD2), Color(0xFFC62828), large = true)
    daysUntilDue <= 31 -> DDayStyle("D-$daysUntilDue", Color(0xFFC8E6C9), Color(0xFF2E7D32), large = true)
    daysUntilDue <= 100 -> DDayStyle("D-$daysUntilDue", Color(0xFFBBDEFB), Color(0xFF1565C0), large = false)
    else -> DDayStyle("D-$daysUntilDue", Color(0xFFE0E0E0), Color(0xFF616161), large = false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onManageAssets: () -> Unit,
    onManageCategories: () -> Unit,
    onItemClick: (Long) -> Unit,
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.categoryFilter.collectAsStateWithLifecycle()

    val filteredItems = remember(items, categoryFilter) {
        items.filter { categoryFilter == null || it.status.categoryId == categoryFilter }
    }
    val categoryById = remember(categories) { categories.associateBy { it.id } }
    val listState = rememberLazyListState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                withContext(Dispatchers.IO) { BackupManager.backup(context, uri) }
                Toast.makeText(context, "백업이 완료되었습니다", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) pendingRestoreUri = uri }

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("백업 파일로 복원할까요?") },
            text = { Text("현재 저장된 모든 데이터가 백업 시점 상태로 교체됩니다. 되돌릴 수 없고, 사진 파일은 복원되지 않습니다. 복원 후 앱이 다시 시작됩니다.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestoreUri = null
                    scope.launch {
                        withContext(Dispatchers.IO) { BackupManager.restore(context, uri) }
                        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        Runtime.getRuntime().exit(0)
                    }
                }) { Text("복원") }
            },
            dismissButton = { TextButton(onClick = { pendingRestoreUri = null }) { Text("취소") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("K-Home-Note") },
                actions = {
                    TextButton(onClick = onManageAssets) { Text("자산 관리") }
                    TextButton(onClick = onManageCategories) { Text("카테고리 관리") }
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(
                    onClick = { backupLauncher.launch(BackupManager.backupFileName()) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                ) { Text("백업", style = MaterialTheme.typography.bodySmall) }
                TextButton(
                    onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                ) { Text("복원", style = MaterialTheme.typography.bodySmall) }
            }
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
                    "등록된 소모품이 없습니다. \"자산 관리\"에서 자산과 소모품을 먼저 추가하세요.",
                    modifier = Modifier.padding(16.dp),
                )
                return@Scaffold
            }
            Text(
                "교체주기순으로 정렬되어있습니다. 구매이력을 기록하려면 소모품이름을 누르세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(filteredItems, key = { it.status.item.id }) { uiModel ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onItemClick(uiModel.status.item.id) }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val category = categoryById[uiModel.status.categoryId]
                                if (category != null) {
                                    Text(
                                        "${category.emoji} ${category.name}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                                Text(
                                    "${uiModel.status.assetName} · ${uiModel.status.item.name}",
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            val dStyle = dDayStyle(uiModel.daysUntilDue, uiModel.status.item.reminderLeadDays)
                            Box(
                                modifier = Modifier
                                    .background(dStyle.background, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    dStyle.text,
                                    color = dStyle.textColor,
                                    fontWeight = FontWeight.Bold,
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
