package com.khomenote.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khomenote.ui.theme.ChipBorderUnselected
import com.khomenote.ui.theme.SurfaceWhite
import com.khomenote.ui.theme.TealPrimary

/** 시안(Main.dc.html)의 카테고리 필터 칩 — 선택: 청록 채움 + 흰 글자, 비선택: 흰 바탕 + 테두리. */
@Composable
fun KFilterChip(selected: Boolean, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(shape)
            .background(if (selected) TealPrimary else SurfaceWhite)
            .then(if (selected) Modifier else Modifier.border(1.dp, ChipBorderUnselected, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = if (selected) 14.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else Color(0xFF3A3F46),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
