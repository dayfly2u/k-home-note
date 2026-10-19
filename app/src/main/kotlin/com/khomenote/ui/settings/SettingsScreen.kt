package com.khomenote.ui.settings

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.khomenote.backup.BackupManager
import com.khomenote.data.local.entity.CategoryEntity
import com.khomenote.data.repository.AssetRepository
import com.khomenote.data.repository.CategoryRepository
import com.khomenote.notification.ReminderScheduler
import com.khomenote.ui.common.AppIcons
import com.khomenote.ui.common.MoreBelowIndicator
import com.khomenote.ui.common.dragElevation
import com.khomenote.ui.common.dragHandle
import com.khomenote.ui.common.formatReminderTime
import com.khomenote.ui.common.rememberDragDropListState
import com.khomenote.ui.theme.CardBorder
import com.khomenote.ui.theme.NumberFontFamily
import com.khomenote.ui.theme.RowDivider
import com.khomenote.ui.theme.SurfaceWhite
import com.khomenote.ui.theme.TealPrimary
import com.khomenote.ui.theme.TextFaint
import com.khomenote.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 설정 화면 카테고리 한 줄 — 카테고리와 그걸 쓰는 대상 개수. */
data class CategoryRow(val category: CategoryEntity, val assetCount: Int)

class SettingsViewModel(
    private val categoryRepository: CategoryRepository,
    assetRepository: AssetRepository,
) : ViewModel() {
    val rows: StateFlow<List<CategoryRow>> = combine(
        categoryRepository.getAll(),
        assetRepository.getAll(),
    ) { categories, assets ->
        val countByCategory = assets.groupingBy { it.categoryId }.eachCount()
        categories.map { CategoryRow(it, countByCategory[it.id] ?: 0) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

class SettingsViewModelFactory(
    private val categoryRepository: CategoryRepository,
    private val assetRepository: AssetRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SettingsViewModel(categoryRepository, assetRepository) as T
}

private val CATEGORY_ROW_HEIGHT = 52.dp
private const val VISIBLE_CATEGORY_ROWS = 6

/**
 * 설정 화면(시안 Settings.dc.html) — 카테고리 관리(목록·순서 바꾸기·추가/수정/삭제), 매일 알림 확인 시각,
 * 백업/복원을 한 화면에 모았다. 프로 결제 카드는 결제 연동 전이라 아직 넣지 않았다.
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rows by viewModel.rows.collectAsStateWithLifecycle()

    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var lastBackup by remember { mutableStateOf(BackupManager.lastBackupDate(context)) }
    var alertTime by remember { mutableStateOf(ReminderScheduler.getTime(context)) }

    var showAddCategory by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var pendingDeleteCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var blockedDeleteCategory by remember { mutableStateOf<CategoryEntity?>(null) }

    var displayRows by remember { mutableStateOf(rows) }
    val categoryListState = rememberLazyListState()
    val dragState = rememberDragDropListState(
        listState = categoryListState,
        itemCount = { displayRows.size },
        onMove = { from, to ->
            displayRows = displayRows.toMutableList().apply { add(to, removeAt(from)) }
        },
        onReorderFinished = { viewModel.reorder(displayRows.map { it.category }) },
    )
    LaunchedEffect(rows) {
        if (dragState.draggingItemIndex == null) displayRows = rows
    }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { BackupManager.backup(context, uri) } }
                    .onSuccess {
                        lastBackup = BackupManager.lastBackupDate(context)
                        Toast.makeText(context, "백업이 완료되었습니다", Toast.LENGTH_SHORT).show()
                    }
                    .onFailure { Toast.makeText(context, "백업 실패: ${it.message}", Toast.LENGTH_LONG).show() }
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
                        runCatching { withContext(Dispatchers.IO) { BackupManager.restore(context, uri) } }
                            .onSuccess {
                                // 기존 리포지토리/DAO가 닫힌 연결을 들고 있으므로, 새로
                                // 뜨는 프로세스에서 깨끗하게 다시 열리도록 앱을 재시작한다.
                                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                    ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) }
                                if (intent != null) context.startActivity(intent)
                                Process.killProcess(Process.myPid())
                            }
                            .onFailure { Toast.makeText(context, "복원 실패: ${it.message}", Toast.LENGTH_LONG).show() }
                    }
                }) { Text("복원") }
            },
            dismissButton = { TextButton(onClick = { pendingRestoreUri = null }) { Text("취소") } },
        )
    }

    if (showAddCategory) {
        CategoryEditSheet(
            initial = null,
            checkNameAvailable = { candidate -> viewModel.isNameAvailable(candidate) },
            isEmojiTaken = { candidate -> displayRows.any { it.category.emoji == candidate } },
            onDismiss = { showAddCategory = false },
            onSave = { name, emoji ->
                viewModel.save(CategoryEntity(name = name, emoji = emoji, sortOrder = displayRows.size))
                showAddCategory = false
            },
        )
    }

    editingCategory?.let { target ->
        CategoryEditSheet(
            initial = target,
            checkNameAvailable = { candidate -> viewModel.isNameAvailable(candidate, excludingId = target.id) },
            isEmojiTaken = { candidate -> displayRows.any { it.category.emoji == candidate && it.category.id != target.id } },
            onDismiss = { editingCategory = null },
            onSave = { name, emoji ->
                viewModel.save(target.copy(name = name, emoji = emoji))
                editingCategory = null
            },
            onDelete = {
                editingCategory = null
                scope.launch {
                    if (viewModel.isInUse(target.id)) blockedDeleteCategory = target else pendingDeleteCategory = target
                }
            },
        )
    }

    pendingDeleteCategory?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteCategory = null },
            title = { Text("카테고리를 삭제할까요?") },
            text = { Text("\"${target.name}\" 카테고리가 삭제되며 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(target)
                    pendingDeleteCategory = null
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteCategory = null }) { Text("취소") } },
        )
    }

    blockedDeleteCategory?.let { target ->
        AlertDialog(
            onDismissRequest = { blockedDeleteCategory = null },
            title = { Text("삭제할 수 없습니다") },
            text = { Text("\"${target.name}\" 카테고리를 사용 중인 자산이 있습니다. 먼저 해당 자산들의 카테고리를 바꾼 뒤 다시 시도해주세요.") },
            confirmButton = { TextButton(onClick = { blockedDeleteCategory = null }) { Text("확인") } },
        )
    }

    val cardShape = RoundedCornerShape(18.dp)

    Scaffold(
        topBar = {
            Text(
                "설정",
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("카테고리")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(cardShape)
                        .background(SurfaceWhite)
                        .border(1.dp, CardBorder, cardShape),
                ) {
                    if (displayRows.isNotEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(CATEGORY_ROW_HEIGHT * minOf(displayRows.size, VISIBLE_CATEGORY_ROWS))) {
                            LazyColumn(state = categoryListState, modifier = Modifier.fillMaxSize()) {
                                itemsIndexed(displayRows, key = { _, row -> row.category.id }) { index, row ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(CATEGORY_ROW_HEIGHT)
                                            .dragElevation(index, dragState)
                                            .clickable { editingCategory = row.category }
                                            .topDivider(index > 0)
                                            .padding(start = 14.dp, end = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(row.category.emoji, fontSize = 18.sp)
                                        Text(row.category.name, modifier = Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                        Text("대상 ${row.assetCount}", fontSize = 13.sp, color = TextSecondary)
                                        Text(
                                            "≡",
                                            modifier = Modifier.dragHandle(index, dragState).padding(horizontal = 10.dp, vertical = 8.dp),
                                            style = MaterialTheme.typography.titleLarge,
                                            color = TextFaint,
                                        )
                                    }
                                }
                            }
                            MoreBelowIndicator(
                                listState = categoryListState,
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable { showAddCategory = true }
                            .topDivider(displayRows.isNotEmpty()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+ 카테고리 추가", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("알림과 데이터")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(cardShape)
                        .background(SurfaceWhite)
                        .border(1.dp, CardBorder, cardShape),
                ) {
                    SettingsRow(
                        icon = Icons.Default.Notifications,
                        label = "매일 알림 확인 시각",
                        valueText = formatReminderTime(alertTime.first, alertTime.second),
                        valueIsNumber = true,
                        showDivider = false,
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    ReminderScheduler.setTime(context, hour, minute)
                                    alertTime = hour to minute
                                },
                                alertTime.first,
                                alertTime.second,
                                false,
                            ).show()
                        },
                    )
                    SettingsRow(
                        icon = AppIcons.Download,
                        label = "백업 파일 만들기",
                        valueText = lastBackup?.let { "마지막 ${it.monthValue}월 ${it.dayOfMonth}일" } ?: "아직 없음",
                        valueIsNumber = false,
                        showDivider = true,
                        onClick = { backupLauncher.launch(BackupManager.backupFileName()) },
                    )
                    SettingsRow(
                        icon = AppIcons.Upload,
                        label = "백업에서 복원",
                        valueText = null,
                        valueIsNumber = false,
                        showDivider = true,
                        onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                    )
                }
            }

            val versionName = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
            }
            Text(
                "생활달력 v$versionName · 모든 데이터는 이 폰에만 저장돼요",
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, modifier = Modifier.padding(horizontal = 4.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    valueText: String?,
    valueIsNumber: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .topDivider(showDivider)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(20.dp))
        Text(label, modifier = Modifier.weight(1f), fontSize = 15.sp)
        if (valueText != null) {
            if (valueIsNumber) {
                Text(valueText, fontFamily = NumberFontFamily, fontWeight = FontWeight.Medium, color = TealPrimary)
            } else {
                Text(valueText, fontSize = 12.sp, color = TextSecondary)
            }
        }
    }
}

private fun Modifier.topDivider(show: Boolean): Modifier =
    if (!show) this else this.drawBehind {
        drawLine(RowDivider, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
    }
