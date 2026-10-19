package com.khomenote.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val MORE_ICON_COLOR = Color(0xFF8D9A9B)

/** 시안의 카드 행 오른쪽 ⋮ 버튼 — 누르면 "수정"/"삭제"(+ [onComplete]가 있으면 맨 위 "완료로 표시") 메뉴가 뜬다(삭제 확인은 호출하는 쪽에서 한다). */
@Composable
fun RowMoreMenu(onEdit: () -> Unit, onDelete: () -> Unit, onComplete: (() -> Unit)? = null) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(width = 36.dp, height = 44.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = "더보기", tint = MORE_ICON_COLOR, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (onComplete != null) {
                DropdownMenuItem(text = { Text("완료로 표시") }, onClick = { expanded = false; onComplete() })
            }
            DropdownMenuItem(text = { Text("수정") }, onClick = { expanded = false; onEdit() })
            DropdownMenuItem(text = { Text("삭제") }, onClick = { expanded = false; onDelete() })
        }
    }
}
