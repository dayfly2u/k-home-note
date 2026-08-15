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
    onDismiss: () -> Unit,
    onSave: (ReplacementRecordInput) -> Unit,
    onPickPhoto: suspend (Uri) -> String?,
    onDiscardPhoto: (String) -> Unit = {},
) {
    val context = LocalContext.current
    var priceText by remember { mutableStateOf("") }
    var vendor by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    var photoPath by remember { mutableStateOf<String?>(null) }
    var photoError by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val scope = rememberCoroutineScope()

    fun discardPickedPhotoAndDismiss() {
        photoPath?.let { onDiscardPhoto(it) }
        onDismiss()
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                photoPath?.let { onDiscardPhoto(it) }
                photoPath = null
                val result = onPickPhoto(uri)
                if (result != null) {
                    photoPath = result
                    photoError = false
                } else {
                    photoError = true
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { discardPickedPhotoAndDismiss() },
        title = { Text("교체 완료 기록") },
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
