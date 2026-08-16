package com.khomenote.ui.item

import android.app.DatePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.khomenote.data.local.entity.ReplacementRecordEntity
import java.time.LocalDate

data class ReplacementRecordInput(
    val date: LocalDate,
    val price: Long?,
    val vendor: String?,
    val photoUri: String?,
    val memo: String?,
)

@Composable
fun ReplacementRecordDialog(
    initial: ReplacementRecordEntity? = null,
    onDismiss: () -> Unit,
    onSave: (ReplacementRecordInput) -> Unit,
    onPickPhoto: suspend (Uri) -> String?,
    onDiscardPhoto: (String) -> Unit = {},
) {
    val context = LocalContext.current
    var priceText by remember { mutableStateOf(initial?.price?.toString() ?: "") }
    var vendor by remember { mutableStateOf(initial?.vendor ?: "") }
    var memo by remember { mutableStateOf(initial?.memo ?: "") }
    var photoPath by remember { mutableStateOf(initial?.photoUri) }
    var photoError by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(initial?.date ?: LocalDate.now()) }
    val scope = rememberCoroutineScope()

    // 원래 붙어있던 사진(수정 시)은 취소하거나 다시 고르는 도중엔 절대 지우지 않는다 —
    // 이번 다이얼로그 세션에서 새로 골라 아직 저장하지 않은 사진만 정리 대상이다.
    val originalPhotoPath = initial?.photoUri

    fun discardPickedPhotoAndDismiss() {
        if (photoPath != null && photoPath != originalPhotoPath) onDiscardPhoto(photoPath!!)
        onDismiss()
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = onPickPhoto(uri)
                if (result != null) {
                    // 이번 세션에서 이미 한 번 새로 고른 사진이 있으면 그 orphan만 정리한다.
                    if (photoPath != null && photoPath != originalPhotoPath) onDiscardPhoto(photoPath!!)
                    photoPath = result
                    photoError = false
                } else {
                    // 새로 고른 사진 복사가 실패한 경우 — 기존에 붙어있던 사진은 그대로 둔다.
                    photoError = true
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { discardPickedPhotoAndDismiss() },
        title = { Text(if (initial == null) "구매 이력 추가" else "구매 이력 수정") },
        text = {
            Column {
                TextButton(onClick = {
                    DatePickerDialog(
                        context,
                        { _, year, month, day -> selectedDate = LocalDate.of(year, month + 1, day) },
                        selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth,
                    ).show()
                }) { Text("날짜: $selectedDate") }
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter(Char::isDigit) },
                    label = { Text("가격 (원)") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = vendor,
                    onValueChange = { vendor = it },
                    label = { Text("구매처/링크") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("메모") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                TextButton(onClick = { photoPicker.launch("image/*") }) {
                    Text(if (photoPath == null) "영수증/제품 사진 첨부" else "사진 첨부됨 (다시 선택하려면 클릭)")
                }
                if (photoError) {
                    Text("사진을 불러오지 못했습니다", color = Color.Red)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // 사진을 새로 교체했다면(원래 사진과 다르면) 저장이 확정된 이 시점에만 원래 사진을 지운다.
                if (photoPath != originalPhotoPath) originalPhotoPath?.let { onDiscardPhoto(it) }
                onSave(
                    ReplacementRecordInput(
                        date = selectedDate,
                        price = priceText.toLongOrNull(),
                        vendor = vendor.takeIf { it.isNotBlank() },
                        photoUri = photoPath,
                        memo = memo.takeIf { it.isNotBlank() },
                    )
                )
            }) { Text("저장") }
        },
        dismissButton = {
            TextButton(onClick = { discardPickedPhotoAndDismiss() }) { Text("취소") }
        },
    )
}
