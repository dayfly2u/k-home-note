package com.khomenote.ui.asset

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.khomenote.ui.common.FormCard
import com.khomenote.ui.common.FormLabel
import com.khomenote.ui.common.FormTopBar
import com.khomenote.ui.common.KFilterChip
import com.khomenote.ui.common.KTextField
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

/** 대상(자산) 추가·수정 폼 — 주기성작업 폼과 같은 ✕ 닫기 + 저장 알약 + 흰 카드 스타일. */
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
            FormTopBar(
                title = if (isNew) "대상 추가" else "대상 수정",
                canSave = viewModel.canSave(),
                onClose = onBack,
                onSave = { viewModel.save(onSaved) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FormCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormLabel("카테고리")
                    if (categories.isEmpty()) {
                        Text("카테고리가 없어요. 설정에서 먼저 카테고리를 추가해 주세요.")
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            categories.forEach { category ->
                                KFilterChip(
                                    selected = viewModel.categoryId == category.id,
                                    label = "${category.emoji} ${category.name}",
                                    onClick = { viewModel.onCategoryChange(category.id) },
                                )
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FormLabel("이름")
                    KTextField(
                        value = viewModel.name,
                        onValueChange = viewModel::onNameChange,
                        placeholder = "예: 다이슨 V15, 아반떼 하이브리드",
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FormLabel("메모")
                KTextField(
                    value = viewModel.memo,
                    onValueChange = viewModel::onMemoChange,
                    placeholder = "예: 12가 3456 · 2023년 3월 출고",
                    singleLine = false,
                )
            }
        }
    }
}
