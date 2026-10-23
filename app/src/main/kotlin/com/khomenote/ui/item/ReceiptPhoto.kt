package com.khomenote.ui.item

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khomenote.ui.common.AppIcons
import com.khomenote.ui.common.DashedActionButton
import kotlinx.coroutines.launch

/**
 * 영수증 사진 한 장을 고르고 정리하는 규칙 — 주기성작업 이력 시트, 일회성작업 추가/수정 시트, "완료로 표시" 시트가 같이 쓴다.
 *
 * 원래 붙어 있던 사진([originalPath], 수정할 때만 있다)은 취소하거나 다시 고르는 도중엔 절대 지우지 않는다 —
 * 이번 시트에서 새로 골라 아직 저장하지 않은 사진만 정리 대상이고, 원래 사진은 저장이 확정될 때([commit]) 바뀐 경우에만 지운다.
 */
class ReceiptPhotoState(
    private val originalPath: String?,
    private val discard: (String) -> Unit,
) {
    /** 지금 붙어 있는 사진 경로(없으면 null). */
    var path: String? by mutableStateOf(originalPath)
        private set

    /** 마지막으로 사진을 고르려던 시도가 복사에 실패했는지. */
    var pickFailed: Boolean by mutableStateOf(false)
        private set

    /** 사진 고르기 결과 — null이면 복사에 실패한 것이라 지금 붙어 있는 사진은 그대로 둔다. */
    fun onPicked(newPath: String?) {
        if (newPath == null) {
            pickFailed = true
            return
        }
        // 이번 시트에서 이미 한 번 새로 고른 사진이 있으면 그 사진만 정리한다(원래 사진은 건드리지 않는다).
        discardUnsaved()
        path = newPath
        pickFailed = false
    }

    /** 저장하지 않고 닫을 때 — 이번 시트에서 새로 고른 사진만 지운다. */
    fun discardUnsaved() {
        val current = path
        if (current != null && current != originalPath) discard(current)
    }

    /** 저장이 확정될 때 — 사진이 바뀌었으면 원래 사진을 지우고, 저장할 사진 경로를 돌려준다. */
    fun commit(): String? {
        if (path != originalPath) originalPath?.let(discard)
        return path
    }
}

@Composable
fun rememberReceiptPhoto(originalPath: String?, onDiscardPhoto: (String) -> Unit): ReceiptPhotoState {
    val discard by rememberUpdatedState(onDiscardPhoto)
    return remember { ReceiptPhotoState(originalPath) { discard(it) } }
}

/** 갤러리에서 사진을 골라 [onPickPhoto]로 앱 저장소에 복사하고 [photo]에 반영한다. 돌려주는 함수가 고르기 화면을 연다. */
@Composable
fun rememberReceiptPhotoPicker(photo: ReceiptPhotoState, onPickPhoto: suspend (Uri) -> String?): () -> Unit {
    val scope = rememberCoroutineScope()
    val pick by rememberUpdatedState(onPickPhoto)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch { photo.onPicked(pick(uri)) }
    }
    return { launcher.launch("image/*") }
}

/** 점선 "영수증 사진" 버튼 — 고르고 나면 썸네일과 "사진 첨부됨 · 다시 선택"으로 바뀐다. */
@Composable
fun ReceiptPhotoButton(photo: ReceiptPhotoState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    DashedActionButton(
        icon = AppIcons.Camera,
        label = if (photo.path == null) "영수증 사진" else "사진 첨부됨 · 다시 선택",
        onClick = onClick,
        modifier = modifier,
        leading = photo.path?.let { path ->
            {
                LocalPhotoThumbnail(
                    path = path,
                    modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
        },
    )
}

@Composable
fun ReceiptPhotoError(photo: ReceiptPhotoState) {
    if (photo.pickFailed) Text("사진을 불러오지 못했습니다", fontSize = 12.sp, color = Color.Red)
}
