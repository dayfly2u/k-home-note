package com.khomenote.ui.item

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
    onPickPhoto: suspend (Uri) -> String,
) {
    var priceText by remember { mutableStateOf("") }
    var vendor by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    var photoPath by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch { photoPath = onPickPhoto(uri) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("교체 완료 기록") },
        text = {
            Column {
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
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    ReplacementRecordInput(
                        date = LocalDate.now(),
                        price = priceText.toLongOrNull(),
                        vendor = vendor.takeIf { it.isNotBlank() },
                        photoUri = photoPath,
                        memo = memo.takeIf { it.isNotBlank() },
                    )
                )
            }) { Text("저장") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}
