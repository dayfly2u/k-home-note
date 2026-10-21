package com.khomenote.ui.item

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import kotlinx.coroutines.launch
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
    var photoPath by remember { mutableStateOf(initial?.photoUri) }
    var photoError by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(initial?.date ?: LocalDate.now()) }
    val scope = rememberCoroutineScope()

    // 원래 붙어있던 사진(수정 시)은 취소하거나 다시 고르는 도중엔 절대 지우지 않는다 —
    // 이번 시트 세션에서 새로 골라 아직 저장하지 않은 사진만 정리 대상이다.
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

    val nextDue = remember(item, otherRecordDates, selectedDate) {
        runCatching { nextDueAfterRecord(item, otherRecordDates, selectedDate) }.getOrNull()
    }

    KModalSheet(onDismiss = { discardPickedPhotoAndDismiss() }) {
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
            DashedActionButton(
                icon = AppIcons.Camera,
                label = if (photoPath == null) "영수증 사진" else "사진 첨부됨 · 다시 선택",
                onClick = { photoPicker.launch("image/*") },
                modifier = Modifier.weight(1f),
                leading = photoPath?.let { path ->
                    {
                        LocalPhotoThumbnail(
                            path = path,
                            modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                },
            )
            DashedActionButton(
                icon = AppIcons.Notes,
                label = if (showMemo) "메모 접기" else "메모",
                onClick = { showMemo = !showMemo },
                modifier = Modifier.weight(1f),
            )
        }
        if (photoError) {
            Text("사진을 불러오지 못했습니다", fontSize = 12.sp, color = Color.Red)
        }
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
            onCancel = { discardPickedPhotoAndDismiss() },
            onConfirm = {
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
            },
        )
    }
}
