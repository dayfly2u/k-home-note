package com.khomenote.ui.asset

import android.app.DatePickerDialog
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.khomenote.data.local.entity.RepairRecordEntity
import java.time.LocalDate

data class RepairRecordInput(
    val name: String,
    val date: LocalDate,
    val price: Long?,
    val memo: String?,
)

/**
 * 소모품 교체 주기와 무관하게, 검사 중 발견된 문제를 고친 일회성 정비 이력을 입력/수정하는 다이얼로그.
 * [initial]이 있으면 수정, 없으면 새로 추가.
 */
@Composable
fun RepairRecordDialog(
    initial: RepairRecordEntity?,
    onDismiss: () -> Unit,
    onSave: (RepairRecordInput) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var selectedDate by remember { mutableStateOf(initial?.date ?: LocalDate.now()) }
    var priceText by remember { mutableStateOf(initial?.price?.toString() ?: "") }
    var memo by remember { mutableStateOf(initial?.memo ?: "") }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "정비 이력 추가" else "정비 이력 수정") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("이름 (예: 브레이크 오일 누유 수리)") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                TextButton(onClick = {
                    DatePickerDialog(
                        context,
                        { _, year, month, day -> selectedDate = LocalDate.of(year, month + 1, day) },
                        selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth,
                    ).show()
                }) { Text("정비 날짜: $selectedDate") }
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter(Char::isDigit) },
                    label = { Text("가격 (원)") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("메모") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        RepairRecordInput(
                            name = name.trim(),
                            date = selectedDate,
                            price = priceText.toLongOrNull(),
                            memo = memo.takeIf { it.isNotBlank() },
                        )
                    )
                },
                enabled = name.isNotBlank(),
            ) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
