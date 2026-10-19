package com.khomenote.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khomenote.ui.theme.ChipBorderUnselected
import com.khomenote.ui.theme.SurfaceWhite
import com.khomenote.ui.theme.TealPrimary

/** 시안(Assets.dc.html)의 "대상 추가" 버튼 — 흰 바탕 + 테두리 알약 모양, 앞에 + 아이콘. */
@Composable
fun PillAddButton(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .height(44.dp)
            .clip(shape)
            .background(SurfaceWhite)
            .border(1.dp, ChipBorderUnselected, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Add, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(16.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
    }
}
