package com.khomenote.ui.category

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.khomenote.data.local.entity.CategoryEntity
import com.khomenote.data.repository.CategoryRepository
import com.khomenote.ui.common.dragElevation
import com.khomenote.ui.common.dragHandle
import com.khomenote.ui.common.rememberDragDropListState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 카테고리 추가/수정 다이얼로그에서 고를 수 있는, 생활(집/가전/자동차/카드) 위주의 이모지. */
val CATEGORY_EMOJI_PALETTE = listOf(
    "🏠", "🔌", "🚗", "💳", "🚙", "🏍️",
    "🚲", "📺", "❄️", "🔥", "💧", "🧹",
    "🛋️", "🛁", "💡", "🏢", "🏡", "🐶",
    "🌱", "🎓", "📱", "💻", "🏥", "✈️",
    "📦", "🔧", "⚡", "🍽️", "🏦", "💰",
)

class CategoryManagementViewModel(private val categoryRepository: CategoryRepository) : ViewModel() {
    val categories: StateFlow<List<CategoryEntity>> = categoryRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun isInUse(categoryId: Long): Boolean = categoryRepository.isInUse(categoryId)

    suspend fun isNameAvailable(candidate: String, excludingId: Long? = null): Boolean =
        categoryRepository.isNameAvailable(candidate, excludingId)

    fun save(category: CategoryEntity) {
        viewModelScope.launch { categoryRepository.save(category) }
    }

    fun delete(category: CategoryEntity) {
        viewModelScope.launch { categoryRepository.delete(category) }
    }

    fun reorder(orderedCategories: List<CategoryEntity>) {
        viewModelScope.launch { categoryRepository.reorder(orderedCategories) }
    }
}

class CategoryManagementViewModelFactory(
    private val categoryRepository: CategoryRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        CategoryManagementViewModel(categoryRepository) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    viewModel: CategoryManagementViewModel,
    onBack: () -> Unit,
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var editingTarget by remember { mutableStateOf<CategoryEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var blockedDelete by remember { mutableStateOf<CategoryEntity?>(null) }

    var displayCategories by remember { mutableStateOf(categories) }
    val listState = rememberLazyListState()
    val dragState = rememberDragDropListState(
        listState = listState,
        itemCount = { displayCategories.size },
        onMove = { from, to ->
            displayCategories = displayCategories.toMutableList().apply { add(to, removeAt(from)) }
        },
        onReorderFinished = { viewModel.reorder(displayCategories) },
    )
    LaunchedEffect(categories) {
        if (dragState.draggingItemIndex == null) displayCategories = categories
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("카테고리 관리") },
                navigationIcon = { TextButton(onClick = onBack) { Text("< 뒤로") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showAddDialog = true }) { Text("카테고리 추가") }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (displayCategories.isNotEmpty()) {
                Text(
                    "손잡이(≡)를 1초간 꾹 눌렀다가 위아래로 드래그하면 순서를 바꿀 수 있어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                itemsIndexed(displayCategories, key = { _, category -> category.id }) { index, category ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .dragElevation(index, dragState)
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
                            Text("${category.emoji} ${category.name}", fontWeight = FontWeight.Bold)
                        }
                        Row {
                            TextButton(onClick = { editingTarget = category }) { Text("수정") }
                            TextButton(onClick = {
                                scope.launch {
                                    if (viewModel.isInUse(category.id)) {
                                        blockedDelete = category
                                    } else {
                                        viewModel.delete(category)
                                    }
                                }
                            }) { Text("삭제") }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAddDialog) {
        CategoryEditDialog(
            initial = null,
            checkNameAvailable = { candidate -> viewModel.isNameAvailable(candidate) },
            onDismiss = { showAddDialog = false },
            onSave = { name, emoji ->
                viewModel.save(CategoryEntity(name = name, emoji = emoji, sortOrder = displayCategories.size))
                showAddDialog = false
            },
        )
    }

    editingTarget?.let { target ->
        CategoryEditDialog(
            initial = target,
            checkNameAvailable = { candidate -> viewModel.isNameAvailable(candidate, excludingId = target.id) },
            onDismiss = { editingTarget = null },
            onSave = { name, emoji ->
                viewModel.save(target.copy(name = name, emoji = emoji))
                editingTarget = null
            },
        )
    }

    blockedDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { blockedDelete = null },
            title = { Text("삭제할 수 없습니다") },
            text = { Text("\"${target.name}\" 카테고리를 사용 중인 자산이 있습니다. 먼저 해당 자산들의 카테고리를 바꾼 뒤 다시 시도해주세요.") },
            confirmButton = { TextButton(onClick = { blockedDelete = null }) { Text("확인") } },
        )
    }
}

@Composable
private fun CategoryEditDialog(
    initial: CategoryEntity?,
    checkNameAvailable: suspend (String) -> Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, emoji: String) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: CATEGORY_EMOJI_PALETTE.first()) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "카테고리 추가" else "카테고리 수정") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("카테고리 이름") },
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "아이콘",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                CategoryEmojiGrid(selected = emoji, onSelected = { emoji = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = name.trim()
                scope.launch {
                    if (checkNameAvailable(trimmed)) {
                        onSave(trimmed, emoji)
                    } else {
                        error = "이미 사용 중인 이름이거나 비어 있습니다."
                    }
                }
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun CategoryEmojiGrid(selected: String, onSelected: (String) -> Unit) {
    Column {
        CATEGORY_EMOJI_PALETTE.chunked(6).forEach { rowEmojis ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                rowEmojis.forEach { candidate ->
                    val isSelected = candidate == selected
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .then(
                                if (isSelected) {
                                    Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { onSelected(candidate) },
                    ) {
                        Text(candidate, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
    }
}
