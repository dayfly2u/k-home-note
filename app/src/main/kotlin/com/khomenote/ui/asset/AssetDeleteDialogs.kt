package com.khomenote.ui.asset

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** 대상 삭제 확인 — 모아보기 카드 ⋯ 메뉴와 대상 상세 ⋮ 메뉴가 같이 쓴다. 일회성작업 기록은 대상과 함께 지워진다. */
@Composable
fun AssetDeleteConfirmDialog(
    assetName: String,
    oneTimeCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("대상을 삭제할까요?") },
        text = {
            val oneTimeNote = if (oneTimeCount > 0) " 일회성작업 기록 ${oneTimeCount}개도 함께 삭제됩니다." else ""
            Text("\"$assetName\"이 삭제되며 되돌릴 수 없습니다.$oneTimeNote")
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("삭제") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** 주기성작업이 남아 있는 대상은 지울 수 없다는 안내. */
@Composable
fun AssetDeleteBlockedDialog(assetName: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("삭제할 수 없습니다") },
        text = { Text("\"$assetName\"에 등록된 주기성작업이 있습니다. 먼저 모두 삭제한 뒤 다시 시도해주세요.") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("확인") } },
    )
}
