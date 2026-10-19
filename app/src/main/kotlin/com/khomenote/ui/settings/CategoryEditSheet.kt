package com.khomenote.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khomenote.data.local.entity.CategoryEntity
import com.khomenote.ui.common.FormLabel
import com.khomenote.ui.common.KModalSheet
import com.khomenote.ui.common.KTextField
import com.khomenote.ui.common.SheetButtons
import com.khomenote.ui.theme.ChipBorderUnselected
import com.khomenote.ui.theme.OrangeUrgent
import com.khomenote.ui.theme.TealContainer
import com.khomenote.ui.theme.TealPrimary
import kotlinx.coroutines.launch

/** 카테고리 추가/수정 시트에서 고를 수 있는, 생활(집/가전/자동차/카드) 위주의 이모지. 5x5로 배치. */
val CATEGORY_EMOJI_PALETTE = listOf(
    "🏠", "🔌", "🚗", "💳", "🚙",
    "🏍️", "🚲", "📺", "❄️", "🔥",
    "💧", "🧹", "🛋️", "🛁", "💡",
    "🏢", "🏡", "🐶", "📱", "💻",
    "🔧", "⚡", "🍽️", "🏦", "💰",
)

/** [onDelete]가 있으면(= 수정 중일 때) 아래에 "카테고리 삭제" 버튼이 보인다. 삭제 확인은 호출하는 쪽에서 한다. */
@Composable
fun CategoryEditSheet(
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

    KModalSheet(onDismiss = onDismiss) {
        Text(
            if (initial == null) "카테고리 추가" else "카테고리 수정",
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FormLabel("카테고리 이름")
            KTextField(
                value = name,
                onValueChange = { name = it; error = null },
                placeholder = "예: 가전, 자동차, 카드",
            )
            error?.let { Text(it, fontSize = 12.sp, color = OrangeUrgent) }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FormLabel("아이콘")
            CategoryEmojiGrid(selected = emoji, onSelected = { emoji = it })
            if (isEmojiTaken(emoji)) {
                Text("이미 다른 카테고리에서 사용 중인 이모지입니다.", fontSize = 12.sp, color = OrangeUrgent)
            }
        }

        if (onDelete != null) {
            TextButton(onClick = onDelete) {
                Text("카테고리 삭제", color = OrangeUrgent, fontWeight = FontWeight.Bold)
            }
        }

        SheetButtons(
            confirmLabel = "저장",
            onCancel = onDismiss,
            onConfirm = {
                val trimmed = name.trim()
                scope.launch {
                    if (checkNameAvailable(trimmed)) {
                        onSave(trimmed, emoji)
                    } else {
                        error = "이미 사용 중인 이름이거나 비어 있습니다."
                    }
                }
            },
        )
    }
}

@Composable
private fun CategoryEmojiGrid(selected: String, onSelected: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CATEGORY_EMOJI_PALETTE.chunked(5).forEach { rowEmojis ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                rowEmojis.forEach { candidate ->
                    val isSelected = candidate == selected
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) TealContainer else Color.Transparent)
                            .border(
                                if (isSelected) 2.dp else 1.dp,
                                if (isSelected) TealPrimary else ChipBorderUnselected,
                                CircleShape,
                            )
                            .clickable { onSelected(candidate) },
                    ) {
                        Text(candidate, fontSize = 22.sp)
                    }
                }
            }
        }
    }
}
