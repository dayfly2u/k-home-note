package com.khomenote.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khomenote.ui.theme.ChipBorderUnselected
import com.khomenote.ui.theme.SurfaceWhite
import com.khomenote.ui.theme.TealPrimary
import com.khomenote.ui.theme.TextPrimary

/** 시안의 입력 바텀시트 틀 — 위 모서리 둥근 흰 시트 안에 스크롤 가능한 본문(키보드가 뜨면 같이 올라온다). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KModalSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceWhite,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

private val DASHED_BORDER = Color(0xFFAEBDBD)

/** 점선 테두리의 보조 동작 버튼(영수증 사진, 메모). [leading]이 있으면 아이콘 대신 그걸 보여준다. */
@Composable
fun DashedActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(Color(0xFFF7F9F9))
            .dashedBorder(DASHED_BORDER, 14.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) leading() else Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
        Text(label, fontSize = 14.sp, color = Color(0xFF3A3F46), maxLines = 1)
    }
}

/** 시트 맨 아래 버튼 줄 — 왼쪽 "취소"(테두리), 오른쪽 넓은 청록 확인 버튼(아이콘 선택). */
@Composable
fun SheetButtons(
    confirmLabel: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    confirmIcon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .height(56.dp)
                .clip(shape)
                .background(SurfaceWhite)
                .border(1.dp, ChipBorderUnselected, shape)
                .clickable(onClick = onCancel)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("취소", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3A3F46))
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .clip(shape)
                .background(if (confirmEnabled) TealPrimary else ChipBorderUnselected)
                .then(if (confirmEnabled) Modifier.clickable(onClick = onConfirm) else Modifier),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (confirmIcon != null) {
                Icon(confirmIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Box(modifier = Modifier.size(8.dp))
            }
            Text(confirmLabel, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
