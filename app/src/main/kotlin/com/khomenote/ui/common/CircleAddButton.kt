package com.khomenote.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.khomenote.ui.theme.ChipBorderUnselected
import com.khomenote.ui.theme.SurfaceWhite
import com.khomenote.ui.theme.TealPrimary

/**
 * 홈 헤더의 "+"와 같은 모양의 추가 버튼 — 44dp 원형 테두리 + 가운데 + 아이콘.
 * 기본 색은 밝은 바탕(대상 상세의 구간 제목 줄 등)용이고, 청록 헤더 위에서는 [background]/[borderColor]/[iconTint]를 바꿔 쓴다.
 */
@Composable
fun CircleAddButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = SurfaceWhite,
    borderColor: Color = ChipBorderUnselected,
    iconTint: Color = TealPrimary,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, borderColor, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Add, contentDescription = contentDescription, tint = iconTint, modifier = Modifier.size(20.dp))
    }
}
