package com.khomenote.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khomenote.data.local.entity.CategoryEntity
import com.khomenote.ui.common.FormLabel
import com.khomenote.ui.common.KModalSheet
import com.khomenote.ui.common.KTextField
import com.khomenote.ui.common.SheetButtons
import com.khomenote.ui.theme.CardBorder
import com.khomenote.ui.theme.OrangeUrgent
import com.khomenote.ui.theme.SurfaceWhite
import com.khomenote.ui.theme.TealContainer
import com.khomenote.ui.theme.TealPrimary
import com.khomenote.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/** 카테고리 추가/수정 시트에서 고를 수 있는, 생활(집/가전/자동차/카드/기념일) 위주의 이모지. 5열로 배치. */
val CATEGORY_EMOJI_PALETTE = listOf(
    "🏠", "🔌", "🚗", "💳", "🚙",
    "🏍️", "🚲", "📺", "❄️", "🔥",
    "💧", "🧹", "🛋️", "🛁", "💡",
    "🏢", "🏡", "🐶", "📱", "💻",
    "🔧", "⚡", "🍽️", "🏦", "💰",
    "🎂", "🎁", "💍", "🎉", "🙏",
)

/**
 * 카테고리 추가/수정 시트(시안 CategorySheet.dc.html). 다른 카테고리가 이미 쓰는 이모지는 처음부터 흐리게 보이고 누를 수 없다
 * ([isEmojiTaken]은 수정 중인 카테고리 자신의 이모지는 제외하고 판단해 준다).
 * [onDelete]가 있으면(= 수정 중일 때) 아래에 "카테고리 삭제" 버튼이 보인다. 삭제 확인은 호출하는 쪽에서 한다.
 */
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
    // 새로 만들 때는 아직 아무도 안 쓰는 이모지부터 골라 둔다.
    var emoji by remember {
        mutableStateOf(
            initial?.emoji
                ?: CATEGORY_EMOJI_PALETTE.firstOrNull { !isEmojiTaken(it) }
                ?: CATEGORY_EMOJI_PALETTE.first(),
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    KModalSheet(onDismiss = onDismiss) {
        Text(
            if (initial == null) "카테고리 추가" else "카테고리 수정",
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            Box(
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(TealContainer),
                contentAlignment = Alignment.Center,
            ) { Text(emoji, fontSize = 26.sp) }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FormLabel("카테고리 이름")
                KTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    placeholder = "예: 가전, 자동차, 카드",
                )
            }
        }
        error?.let { Text(it, fontSize = 12.sp, color = OrangeUrgent) }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FormLabel("아이콘")
            CategoryEmojiGrid(selected = emoji, isTaken = isEmojiTaken, onSelected = { emoji = it })
            Text("흐린 아이콘은 다른 카테고리에서 쓰는 중이에요", fontSize = 12.sp, color = TextSecondary)
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
private fun CategoryEmojiGrid(selected: String, isTaken: (String) -> Boolean, onSelected: (String) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CATEGORY_EMOJI_PALETTE.chunked(5).forEach { rowEmojis ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowEmojis.forEach { candidate ->
                    val isSelected = candidate == selected
                    val taken = isTaken(candidate) && !isSelected
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .alpha(if (taken) 0.3f else 1f)
                            .clip(shape)
                            .background(if (isSelected) TealContainer else SurfaceWhite)
                            .border(if (isSelected) 2.dp else 1.dp, if (isSelected) TealPrimary else CardBorder, shape)
                            .then(if (taken) Modifier else Modifier.clickable { onSelected(candidate) }),
                    ) {
                        Text(candidate, fontSize = 22.sp)
                    }
                }
            }
        }
    }
}
