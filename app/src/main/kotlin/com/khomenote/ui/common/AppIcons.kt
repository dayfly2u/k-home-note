package com.khomenote.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// material-icons-core에 없는 선(stroke) 아이콘 — 시안의 SVG 경로를 그대로 옮겼다. 색은 Icon(tint = ...)로 입힌다.
private fun strokeIcon(name: String, path: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(path),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()

object AppIcons {
    /** 아래로 내려받는 트레이 — 백업 파일 만들기 */
    val Download: ImageVector by lazy { strokeIcon("Download", "M12 3v12M7 10l5 5 5-5M4 17v3h16v-3") }

    /** 위로 올리는 트레이 — 백업에서 복원 */
    val Upload: ImageVector by lazy { strokeIcon("Upload", "M12 15V3M7 8l5-5 5 5M4 17v3h16v-3") }

    /** 줄 그어진 종 — 알림이 꺼짐 */
    val BellOff: ImageVector by lazy { strokeIcon("BellOff", "M6 16V11a6 6 0 0 1 12 0v5l2 2H4zM10 21h4M3 3l18 18") }

    /** 카메라 — 영수증 사진 */
    val Camera: ImageVector by lazy { strokeIcon("Camera", "M4 8h3l2-3h6l2 3h3v11H4zM8.5 13a3.5 3.5 0 1 0 7 0 3.5 3.5 0 1 0-7 0") }

    /** 줄 세 개 — 메모 */
    val Notes: ImageVector by lazy { strokeIcon("Notes", "M4 6h16M4 12h16M4 18h10") }

    /** 원형 화살표 — 주기성작업으로 바꾸기 */
    val Refresh: ImageVector by lazy { strokeIcon("Refresh", "M20 11a8 8 0 1 0-2.3 5.7M20 4v7h-7") }
}
