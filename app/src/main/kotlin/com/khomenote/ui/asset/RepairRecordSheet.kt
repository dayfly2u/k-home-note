package com.khomenote.ui.asset

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khomenote.data.local.entity.RepairRecordEntity
import com.khomenote.notification.ReminderScheduler
import com.khomenote.ui.common.DateField
import com.khomenote.ui.common.FormLabel
import com.khomenote.ui.common.KModalSheet
import com.khomenote.ui.common.KTextField
import com.khomenote.ui.common.SheetButtons
import com.khomenote.ui.common.StepperButton
import com.khomenote.ui.common.formatReminderTime
import com.khomenote.ui.item.reminderExplanation
import com.khomenote.ui.theme.CardBorder
import com.khomenote.ui.theme.ChipBorderUnselected
import com.khomenote.ui.theme.NumberFontFamily
import com.khomenote.ui.theme.RowDivider
import com.khomenote.ui.theme.TealPrimary
import com.khomenote.ui.theme.TextSecondary
import java.time.LocalDate

data class RepairRecordInput(
    val name: String,
    val date: LocalDate,
    val price: Long?,
    val memo: String?,
    val reminderLeadDays: Int?,
)

/**
 * 소모품 교체 주기와 무관한 일회성작업(예정된 일정이거나 이미 끝난 일 기록)을 입력/수정하는 바텀시트.
 * [initial]이 있으면 수정, 없으면 새로 추가. "알림 받기"를 켜면 대시보드에 표시되고 알림을 받는다 —
 * 처리를 끝냈으면 "완료로 표시"([OneTimeDoneSheet])로 꺼서 대시보드/알림 목록에서 빼면 된다(소모품과 달리 다음 주기로 안 넘어간다).
 * [assetLabel]은 제목 옆에 보여주는 대상 칩("🔌 보일러").
 */
@Composable
fun RepairRecordSheet(
    initial: RepairRecordEntity?,
    assetLabel: String?,
    onDismiss: () -> Unit,
    onSave: (RepairRecordInput) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var selectedDate by remember { mutableStateOf(initial?.date ?: LocalDate.now()) }
    var priceText by remember { mutableStateOf(initial?.price?.toString() ?: "") }
    var memo by remember { mutableStateOf(initial?.memo ?: "") }
    var reminderEnabled by remember { mutableStateOf(initial?.reminderLeadDays != null) }
    var reminderLeadDays by remember { mutableStateOf(initial?.reminderLeadDays ?: 3) }
    val (alertHour, alertMinute) = ReminderScheduler.getTime(LocalContext.current)

    KModalSheet(onDismiss = onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (initial == null) "일회성작업 추가" else "일회성작업 수정",
                modifier = Modifier.weight(1f),
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
            )
            if (assetLabel != null) {
                Text(
                    assetLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFF2F5F5))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FormLabel("이름")
            KTextField(value = name, onValueChange = { name = it }, placeholder = "예: 보일러 점검")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DateField("날짜", selectedDate, { selectedDate = it }, Modifier.weight(1f))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FormLabel("가격 (원)")
                KTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter(Char::isDigit) },
                    placeholder = "선택",
                    keyboardType = KeyboardType.Number,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FormLabel("메모")
            KTextField(value = memo, onValueChange = { memo = it }, placeholder = "선택")
        }

        val cardShape = RoundedCornerShape(16.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CardBorder, cardShape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("알림 받기", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("켜면 홈에도 예정으로 보여요", fontSize = 12.sp, color = TextSecondary)
                }
                Switch(
                    checked = reminderEnabled,
                    onCheckedChange = { reminderEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = TealPrimary,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = ChipBorderUnselected,
                        uncheckedBorderColor = ChipBorderUnselected,
                    ),
                )
            }
            if (reminderEnabled) {
                HorizontalDivider(color = RowDivider)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("며칠 전부터", modifier = Modifier.weight(1f), fontSize = 13.sp, color = Color(0xFF3A3F46))
                    StepperButton("−", "하루 줄이기", enabled = reminderLeadDays > 0) { reminderLeadDays = (reminderLeadDays - 1).coerceAtLeast(0) }
                    Row(modifier = Modifier.width(48.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                        Text(reminderLeadDays.toString(), fontFamily = NumberFontFamily, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("일", fontSize = 13.sp, modifier = Modifier.padding(start = 1.dp, bottom = 3.dp))
                    }
                    StepperButton("+", "하루 늘리기") { reminderLeadDays = (reminderLeadDays + 1).coerceAtMost(365) }
                }
                Text(
                    "${reminderExplanation(reminderLeadDays, formatReminderTime(alertHour, alertMinute))}. 끄면 기록으로만 남아요.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
            } else {
                Text("끄면 기록으로만 남고 홈에는 보이지 않아요.", fontSize = 12.sp, color = TextSecondary)
            }
        }

        Spacer(Modifier.height(2.dp))
        SheetButtons(
            confirmLabel = "저장",
            onCancel = onDismiss,
            confirmEnabled = name.isNotBlank(),
            onConfirm = {
                onSave(
                    RepairRecordInput(
                        name = name.trim(),
                        date = selectedDate,
                        price = priceText.toLongOrNull(),
                        memo = memo.takeIf { it.isNotBlank() },
                        reminderLeadDays = if (reminderEnabled) reminderLeadDays else null,
                    )
                )
            },
        )
    }
}
