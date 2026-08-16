package com.khomenote.ui.asset

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.khomenote.data.local.entity.AssetEntity
import com.khomenote.data.local.entity.CategoryEntity
import com.khomenote.data.repository.AssetRepository
import com.khomenote.data.repository.CategoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class AssetFormViewModel(
    private val assetRepository: AssetRepository,
    categoryRepository: CategoryRepository,
    private val assetId: Long?,
) : ViewModel() {
    val categories: StateFlow<List<CategoryEntity>> = categoryRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var name by mutableStateOf("")
        private set
    var categoryId by mutableStateOf<Long?>(null)
        private set
    var memo by mutableStateOf("")
        private set

    // 수정 시 원래 등록일/정렬 순서를 보존한다 — 이 값들을 안 챙기면 저장할 때마다
    // AssetEntity 기본값(오늘 날짜, sortOrder 0)으로 초기화돼버린다.
    private var loadedCreatedAt: LocalDate = LocalDate.now()
    private var loadedSortOrder: Int? = null

    init {
        if (assetId != null) {
            viewModelScope.launch {
                val asset = assetRepository.getById(assetId).first() ?: return@launch
                name = asset.name
                categoryId = asset.categoryId
                memo = asset.memo ?: ""
                loadedCreatedAt = asset.createdAt
                loadedSortOrder = asset.sortOrder
            }
        }
    }

    fun onNameChange(value: String) { name = value }
    fun onCategoryChange(value: Long) { categoryId = value }
    fun onMemoChange(value: String) { memo = value }

    /** 새 자산 폼에서 카테고리가 아직 안 골라졌으면(최초 로딩 등) 첫 번째 카테고리를 기본 선택한다. */
    fun ensureDefaultCategory() {
        if (categoryId == null) {
            categoryId = categories.value.firstOrNull()?.id
        }
    }

    fun canSave(): Boolean = name.isNotBlank() && categoryId != null

    fun save(onDone: () -> Unit) {
        val selectedCategoryId = categoryId ?: return
        if (!canSave()) return
        viewModelScope.launch {
            // 새 자산은 항상 목록 맨 끝에 추가되도록 sortOrder를 현재 개수로 준다.
            val sortOrder = loadedSortOrder ?: assetRepository.getAll().first().size
            assetRepository.save(
                AssetEntity(
                    id = assetId ?: 0,
                    name = name,
                    categoryId = selectedCategoryId,
                    memo = memo.takeIf { it.isNotBlank() },
                    createdAt = loadedCreatedAt,
                    sortOrder = sortOrder,
                )
            )
            onDone()
        }
    }
}

class AssetFormViewModelFactory(
    private val assetRepository: AssetRepository,
    private val categoryRepository: CategoryRepository,
    private val assetId: Long?,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AssetFormViewModel(assetRepository, categoryRepository, assetId) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetFormScreen(
    viewModel: AssetFormViewModel,
    isNew: Boolean,
    onSaved: () -> Unit,
    onBack: () -> Unit,
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    viewModel.ensureDefaultCategory()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "자산 추가" else "자산 수정") },
                navigationIcon = { TextButton(onClick = onBack) { Text("< 뒤로") } },
                actions = {
                    TextButton(
                        onClick = { viewModel.save(onSaved) },
                        enabled = viewModel.canSave(),
                    ) { Text("저장") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 12.dp),
            ) {
                categories.forEach { category ->
                    FilterChip(
                        selected = viewModel.categoryId == category.id,
                        onClick = { viewModel.onCategoryChange(category.id) },
                        label = { Text("${category.emoji} ${category.name}") },
                    )
                }
            }
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("이름 (예: 다이슨 V15, 아반떼 하이브리드)") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
            OutlinedTextField(
                value = viewModel.memo,
                onValueChange = viewModel::onMemoChange,
                label = { Text("메모") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
