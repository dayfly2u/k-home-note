package com.khomenote.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.khomenote.data.local.entity.CategoryEntity
import kotlinx.coroutines.launch

/** 카테고리 추가/수정 다이얼로그에서 고를 수 있는, 생활(집/가전/자동차/카드) 위주의 이모지. 5x5로 배치. */
val CATEGORY_EMOJI_PALETTE = listOf(
    "🏠", "🔌", "🚗", "💳", "🚙",
    "🏍️", "🚲", "📺", "❄️", "🔥",
    "💧", "🧹", "🛋️", "🛁", "💡",
    "🏢", "🏡", "🐶", "📱", "💻",
    "🔧", "⚡", "🍽️", "🏦", "💰",
)

/** [onDelete]가 있으면(= 수정 중일 때) 아래에 "카테고리 삭제" 버튼이 보인다. 삭제 확인은 호출하는 쪽에서 한다. */
@Composable
fun CategoryEditDialog(
    initial: CategoryEntity?,
    checkNameAvailable: suspend (String) -> Boolean,
    isEmojiTaken: (String) -> Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, emoji: String) -> Unit,
    onDelete: (() -> Unit)? = null,
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
                if (isEmojiTaken(emoji)) {
                    Text(
                        "이미 다른 카테고리에서 사용 중인 이모지입니다.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete, modifier = Modifier.padding(top = 8.dp)) {
                        Text("카테고리 삭제", color = MaterialTheme.colorScheme.error)
                    }
                }
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
        CATEGORY_EMOJI_PALETTE.chunked(5).forEach { rowEmojis ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                rowEmojis.forEach { candidate ->
                    val isSelected = candidate == selected
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .padding(2.dp)
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            )
                            .then(
                                if (isSelected) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                } else {
                                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
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
