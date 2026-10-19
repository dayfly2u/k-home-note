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
}
