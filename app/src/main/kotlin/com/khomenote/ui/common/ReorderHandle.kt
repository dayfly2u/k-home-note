package com.khomenote.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private val HANDLE_COLOR = Color(0xFF8D9A9B)

/**
 * 길게 눌러 순서를 바꾸는 손잡이(시안: 세 줄 아이콘 20dp, 터치 영역 44dp).
 * [dragModifier]에는 `Modifier.dragHandle(index, dragState)`를 넘긴다.
 */
@Composable
fun ReorderHandle(dragModifier: Modifier) {
    Box(
        modifier = dragModifier
            .size(44.dp)
            .semantics { contentDescription = "길게 눌러 순서 바꾸기" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.Reorder, contentDescription = null, tint = HANDLE_COLOR, modifier = Modifier.size(20.dp))
    }
}
