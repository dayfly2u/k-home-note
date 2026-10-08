package com.khomenote.ui.asset

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
    val reminderLeadDays: Int?,
)

/**
 * 소모품 교체 주기와 무관한 일회성작업(예정된 일정이거나 이미 끝난 일 기록)을 입력/수정하는 다이얼로그.
 * [initial]이 있으면 수정, 없으면 새로 추가. "알림 받기"를 켜면 대시보드에 표시되고 알림을 받는다 —
 * 처리를 끝냈으면 다시 꺼서 대시보드/알림 목록에서 빼면 된다(소모품과 달리 다음 주기로 안 넘어간다).
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
    var reminderEnabled by remember { mutableStateOf(initial?.reminderLeadDays != null) }
    var reminderLeadDaysText by remember { mutableStateOf((initial?.reminderLeadDays ?: 3).toString()) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "일회성작업 추가" else "일회성작업 수정") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("이름 (예: 보일러 점검)") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
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
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("메모") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = reminderEnabled, onCheckedChange = { reminderEnabled = it })
                    Text("알림 받기 (대시보드에도 표시돼요)", style = MaterialTheme.typography.bodySmall)
                }
                if (reminderEnabled) {
                    OutlinedTextField(
                        value = reminderLeadDaysText,
                        onValueChange = { reminderLeadDaysText = it.filter(Char::isDigit) },
                        label = { Text("며칠 전에 알림을 받을까요?") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
                            reminderLeadDays = if (reminderEnabled) (reminderLeadDaysText.toIntOrNull() ?: 3) else null,
                        )
                    )
                },
                enabled = name.isNotBlank(),
            ) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
