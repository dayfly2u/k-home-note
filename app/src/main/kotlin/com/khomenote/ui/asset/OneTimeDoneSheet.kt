package com.khomenote.ui.asset

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.khomenote.data.local.entity.RepairRecordEntity
import com.khomenote.ui.common.AppIcons
import com.khomenote.ui.common.DashedActionButton
import com.khomenote.ui.common.DateField
import com.khomenote.ui.common.FormLabel
import com.khomenote.ui.common.KModalSheet
import com.khomenote.ui.common.KTextField
import com.khomenote.ui.common.SheetButtons
import com.khomenote.ui.item.ReceiptPhotoButton
import com.khomenote.ui.item.ReceiptPhotoError
import com.khomenote.ui.item.rememberReceiptPhoto
import com.khomenote.ui.item.rememberReceiptPhotoPicker
import com.khomenote.ui.theme.OnceTagBorder
import com.khomenote.ui.theme.TealContainer
import com.khomenote.ui.theme.TealPrimary
import com.khomenote.ui.theme.TealPrimaryDark
import com.khomenote.ui.theme.TextSecondary
import java.time.LocalDate

/** "완료로 표시"에서 사용자가 확정한 값 — 구매처/영수증 사진은 주기성작업의 완료 기록과 같은 것을 남긴다. */
data class OneTimeDoneInput(
    val date: LocalDate,
    val price: Long?,
    val vendor: String?,
    val photoUri: String?,
    val memo: String?,
)

/** [input]을 반영한 완료 상태의 기록 — 알림을 꺼서(reminderLeadDays = null) 홈/알림 목록에서 빠지고 대상 상세에만 남는다. */
fun RepairRecordEntity.completedWith(input: OneTimeDoneInput): RepairRecordEntity = copy(
    date = input.date,
    price = input.price,
    vendor = input.vendor,
    photoUri = input.photoUri,
    memo = input.memo,
    reminderLeadDays = null,
)

/**
 * 일회성작업 "완료로 표시" 시트(시안 OneTimeDone.dc.html). 실제 한 날·가격·구매처·영수증 사진·메모를 고치면서
 * 알림을 끈다(reminderLeadDays = null) — 그러면 홈에서는 빠지고 대상의 일회성작업 기록에는 그대로 남는다.
 * [onConvertToRecurring]은 "매년 하는 일이면 주기성작업으로 바꾸기" — 같은 대상에 이름을 채운 주기성작업 추가 화면을 연다.
 * 사진 정리 규칙은 주기성작업의 완료 기록 시트(ReplacementRecordSheet)와 같다.
 */
@Composable
fun OneTimeDoneSheet(
    record: RepairRecordEntity,
    assetName: String,
    onDismiss: () -> Unit,
    onComplete: (OneTimeDoneInput) -> Unit,
    onConvertToRecurring: (name: String) -> Unit,
    onPickPhoto: suspend (Uri) -> String?,
    onDiscardPhoto: (String) -> Unit = {},
) {
    var doneDate by remember { mutableStateOf(record.date) }
    var priceText by remember { mutableStateOf(record.price?.toString() ?: "") }
    var vendor by remember { mutableStateOf(record.vendor ?: "") }
    var memo by remember { mutableStateOf(record.memo ?: "") }
    var showMemo by remember { mutableStateOf(!record.memo.isNullOrBlank()) }
    val photo = rememberReceiptPhoto(record.photoUri, onDiscardPhoto)
    val pickPhoto = rememberReceiptPhotoPicker(photo, onPickPhoto)

    fun discardUnsavedAndDismiss() {
        photo.discardUnsaved()
        onDismiss()
    }

    KModalSheet(onDismiss = { discardUnsavedAndDismiss() }) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("$assetName · ${record.date.monthValue}월 ${record.date.dayOfMonth}일 예정", fontSize = 12.sp, color = TextSecondary)
                Text(
                    "한 번",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TealPrimary,
                    modifier = Modifier
                        .border(1.dp, OnceTagBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            Text("${record.name}, 끝났나요?", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DateField("한 날", doneDate, { doneDate = it }, Modifier.weight(1f))
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(TealContainer)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(AppIcons.BellOff, contentDescription = null, tint = TealPrimaryDark, modifier = Modifier.padding(top = 1.dp).size(20.dp))
            Text("알림이 꺼지고 홈에서 빠져요. ${assetName}의 일회성작업 기록에는 그대로 남아요.", fontSize = 13.sp, color = TealPrimaryDark)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clickable { onConvertToRecurring(record.name) },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.Refresh, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(16.dp))
            Text(
                "  매년 하는 일이면 주기성작업으로 바꾸기",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TealPrimary,
            )
        }

        SheetButtons(
            confirmLabel = "완료로 표시",
            confirmIcon = Icons.Default.Check,
            onCancel = { discardUnsavedAndDismiss() },
            onConfirm = {
                onComplete(
                    OneTimeDoneInput(
                        date = doneDate,
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
