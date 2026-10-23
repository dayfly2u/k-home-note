package com.khomenote.ui.item

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khomenote.data.local.entity.ConsumableItemEntity
import com.khomenote.data.local.entity.ReplacementRecordEntity
import com.khomenote.ui.common.AppIcons
import com.khomenote.ui.common.DashedActionButton
import com.khomenote.ui.common.DateField
import com.khomenote.ui.common.FormLabel
import com.khomenote.ui.common.KModalSheet
import com.khomenote.ui.common.KTextField
import com.khomenote.ui.common.SheetButtons
import com.khomenote.ui.theme.TealContainer
import com.khomenote.ui.theme.TealPrimaryDark
import com.khomenote.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ReplacementRecordInput(
    val date: LocalDate,
    val price: Long?,
    val vendor: String?,
    val photoUri: String?,
    val memo: String?,
)

private val NEXT_DUE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN)

/**
 * 주기성작업의 이력(교체/구매 기록)을 남기거나 고치는 바텀시트(시안 Done.dc.html).
 * [initial]이 있으면 수정. 시트 아래에는 이 날짜로 기록했을 때 다음 예정일이 언제로 넘어가는지 미리 보여준다 —
 * [otherRecordDates]는 이 기록을 뺀 나머지 기록 날짜들(다음 예정일은 가장 최근 기록 기준이라 필요하다).
 */
@Composable
fun ReplacementRecordSheet(
    item: ConsumableItemEntity,
    assetName: String,
    categoryEmoji: String,
    otherRecordDates: List<LocalDate>,
    initial: ReplacementRecordEntity? = null,
    onDismiss: () -> Unit,
    onSave: (ReplacementRecordInput) -> Unit,
    onPickPhoto: suspend (Uri) -> String?,
    onDiscardPhoto: (String) -> Unit = {},
) {
    var priceText by remember { mutableStateOf(initial?.price?.toString() ?: "") }
    var vendor by remember { mutableStateOf(initial?.vendor ?: "") }
    var memo by remember { mutableStateOf(initial?.memo ?: "") }
    var showMemo by remember { mutableStateOf(!initial?.memo.isNullOrBlank()) }
    val photo = rememberReceiptPhoto(initial?.photoUri, onDiscardPhoto)
    val pickPhoto = rememberReceiptPhotoPicker(photo, onPickPhoto)
    var selectedDate by remember { mutableStateOf(initial?.date ?: LocalDate.now()) }

    fun discardUnsavedAndDismiss() {
        photo.discardUnsaved()
        onDismiss()
    }

    val nextDue = remember(item, otherRecordDates, selectedDate) {
        runCatching { nextDueAfterRecord(item, otherRecordDates, selectedDate) }.getOrNull()
    }

    KModalSheet(onDismiss = { discardUnsavedAndDismiss() }) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFF2F5F5)),
                contentAlignment = Alignment.Center,
            ) { Text(categoryEmoji, fontSize = 20.sp) }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(assetName, fontSize = 12.sp, color = TextSecondary)
                Text(
                    if (initial == null) "${item.name} 이력 기록" else "${item.name} 이력 수정",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DateField("한 날", selectedDate, { selectedDate = it }, Modifier.weight(1f))
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
            FormLabel("구매처/링크")
            // 여러 줄 칸 — 예전 버전에서 줄바꿈이 든 글을 구매처에 적어 둔 기록도 전부 보이고 고칠 수 있다.
            KTextField(value = vendor, onValueChange = { vendor = it }, placeholder = "선택", singleLine = false)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReceiptPhotoButton(photo, onClick = pickPhoto, modifier = Modifier.weight(1f))
            DashedActionButton(
                icon = AppIcons.Notes,
                label = if (showMemo) "메모 접기" else "메모",
                onClick = { showMemo = !showMemo },
                modifier = Modifier.weight(1f),
            )
        }
        ReceiptPhotoError(photo)
        if (showMemo) {
            KTextField(value = memo, onValueChange = { memo = it }, placeholder = "메모", singleLine = false)
        }

        if (nextDue != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(TealContainer)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Refresh, contentDescription = null, tint = TealPrimaryDark, modifier = Modifier.size(20.dp))
                Row {
                    Text("다음 예정일은 ", fontSize = 14.sp, color = TealPrimaryDark)
                    Text(nextDue.format(NEXT_DUE_FORMAT), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TealPrimaryDark)
                    Text("로 넘어가요", fontSize = 14.sp, color = TealPrimaryDark)
                }
            }
        }

        SheetButtons(
            confirmLabel = if (initial == null) "이력 기록" else "저장",
            confirmIcon = Icons.Default.Check,
            onCancel = { discardUnsavedAndDismiss() },
            onConfirm = {
                onSave(
                    ReplacementRecordInput(
                        date = selectedDate,
                        price = priceText.toLongOrNull(),
                        vendor = vendor.takeIf { it.isNotBlank() },
                        photoUri = photo.commit(),
                        memo = memo.takeIf { it.isNotBlank() },
                    )
                )
            },
        )
    }
}
