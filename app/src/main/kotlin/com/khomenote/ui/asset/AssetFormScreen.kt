package com.khomenote.ui.asset

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.khomenote.data.local.entity.AssetEntity
import com.khomenote.data.local.entity.CategoryEntity
import com.khomenote.data.repository.AssetRepository
import com.khomenote.data.repository.CategoryRepository
import com.khomenote.ui.common.AppIcons
import com.khomenote.ui.common.FormCard
import com.khomenote.ui.common.FormLabel
import com.khomenote.ui.common.FormTopBar
import com.khomenote.ui.common.KTextField
import com.khomenote.ui.common.dashedBorder
import com.khomenote.ui.settings.CategoryEditSheet
import com.khomenote.ui.theme.ChipBorderUnselected
import com.khomenote.ui.theme.SurfaceWhite
import com.khomenote.ui.theme.TealContainer
import com.khomenote.ui.theme.TealPrimary
import com.khomenote.ui.theme.TealPrimaryDark
import com.khomenote.ui.theme.TextPrimary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class AssetFormViewModel(
    private val assetRepository: AssetRepository,
    private val categoryRepository: CategoryRepository,
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

    /** 새 대상 폼에서 카테고리가 아직 안 골라졌으면(최초 로딩 등) 첫 번째 카테고리를 기본 선택한다. */
    fun ensureDefaultCategory() {
        if (categoryId == null) {
            categoryId = categories.value.firstOrNull()?.id
        }
    }

    suspend fun isCategoryNameAvailable(candidate: String): Boolean = categoryRepository.isNameAvailable(candidate)

    /** 폼 안에서 "+ 새 카테고리"로 만든 카테고리를 목록 맨 끝에 추가하고 바로 선택한다. */
    fun addCategory(name: String, emoji: String) {
        viewModelScope.launch {
            val sortOrder = categoryRepository.getAll().first().size
            categoryId = categoryRepository.save(CategoryEntity(name = name, emoji = emoji, sortOrder = sortOrder))
        }
    }

    fun canSave(): Boolean = name.isNotBlank() && categoryId != null

    /** 저장이 끝나면 저장된 대상의 id로 [onDone]을 부른다(새 대상이면 그 상세 화면으로 이어가기 위해). */
    fun save(onDone: (assetId: Long) -> Unit) {
        val selectedCategoryId = categoryId ?: return
        if (!canSave()) return
        viewModelScope.launch {
            // 새 자산은 항상 목록 맨 끝에 추가되도록 sortOrder를 현재 개수로 준다.
            val sortOrder = loadedSortOrder ?: assetRepository.getAll().first().size
            val savedId = assetRepository.save(
                AssetEntity(
                    id = assetId ?: 0,
                    name = name,
                    categoryId = selectedCategoryId,
                    memo = memo.takeIf { it.isNotBlank() },
                    createdAt = loadedCreatedAt,
                    sortOrder = sortOrder,
                )
            )
            onDone(savedId)
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

/**
 * 대상 추가·수정 폼(시안 AssetForm.dc.html) — 카테고리는 2열 큰 버튼, 마지막 칸 "+ 새 카테고리"로 폼을 떠나지 않고 만들 수 있다.
 * [onSaved]는 저장된 대상의 id를 받는다 — 새 대상이면 호출하는 쪽에서 그 대상의 상세 화면으로 이어간다.
 */
@Composable
fun AssetFormScreen(
    viewModel: AssetFormViewModel,
    isNew: Boolean,
    onSaved: (assetId: Long) -> Unit,
    onBack: () -> Unit,
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    viewModel.ensureDefaultCategory()
    var showNewCategory by remember { mutableStateOf(false) }

    if (showNewCategory) {
        CategoryEditSheet(
            initial = null,
            checkNameAvailable = { candidate -> viewModel.isCategoryNameAvailable(candidate) },
            isEmojiTaken = { candidate -> categories.any { it.emoji == candidate } },
            onDismiss = { showNewCategory = false },
            onSave = { name, emoji ->
                viewModel.addCategory(name, emoji)
                showNewCategory = false
            },
        )
    }

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
                .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FormCard {
                FormLabel("카테고리")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { category ->
                                CategoryChoiceButton(
                                    category = category,
                                    selected = viewModel.categoryId == category.id,
                                    onClick = { viewModel.onCategoryChange(category.id) },
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    NewCategoryButton(onClick = { showNewCategory = true })
                }
            }

            FormCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FormLabel("이름")
                    KTextField(
                        value = viewModel.name,
                        onValueChange = viewModel::onNameChange,
                        placeholder = "예: 다이슨 V15, 신한카드, 우리 가족",
                    )
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

            if (isNew) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(TealContainer)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.ChevronRight, contentDescription = null, tint = TealPrimaryDark, modifier = Modifier.size(20.dp))
                    Text(
                        "저장하면 이 대상의 상세 화면으로 가서 주기성작업을 바로 추가할 수 있어요",
                        fontSize = 13.sp,
                        color = TealPrimaryDark,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.CategoryChoiceButton(category: CategoryEntity, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .weight(1f)
            .height(52.dp)
            .clip(shape)
            .background(if (selected) TealContainer else SurfaceWhite)
            .border(if (selected) 2.dp else 1.dp, if (selected) TealPrimary else ChipBorderUnselected, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = if (selected) 13.dp else 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(category.emoji, fontSize = 20.sp)
        Text(
            category.name,
            modifier = Modifier.weight(1f),
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) TealPrimaryDark else TextPrimary,
            maxLines = 1,
        )
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = "선택됨", tint = TealPrimaryDark, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun NewCategoryButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(Color(0xFFF7F9F9))
            .dashedBorder(Color(0xFFAEBDBD), 14.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Add, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(16.dp))
            Text("새 카테고리", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
        }
    }
}
