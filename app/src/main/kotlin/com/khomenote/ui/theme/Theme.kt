package com.khomenote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.khomenote.R

// 생활달력 리디자인 색상 (2026-10-18 redesign-brief.md 1장) — 청록 한 가지 + 급한 것만 주황.
val TealPrimary = Color(0xFF155E63)
val TealPrimaryDark = Color(0xFF0D4347)
val TealContainer = Color(0xFFE3EEED)
val TealHeaderMuted = Color(0xFFB9D6D4)

val OrangeUrgent = Color(0xFFB84A16)
val OrangeUrgentContainer = Color(0xFFFCE9DD)
val OrangeUrgentText = Color(0xFFA2410F)
val OrangeHeaderDot = Color(0xFFF08A4B)

val BackgroundGray = Color(0xFFF2F5F5)
val SurfaceWhite = Color(0xFFFFFFFF)
val ChipBorderUnselected = Color(0xFFCCD6D6)

val TextPrimary = Color(0xFF1E2329)
val TextSecondary = Color(0xFF5C6168)
val TextFaint = Color(0xFF8A9091)

val CardBorder = Color(0xFFDFE6E6)
val RowDivider = Color(0xFFEAEFEF)

// 일회성작업 "한 번" 태그 색 (v0.6 연한노랑 배경 대체).
val OnceTagBorder = Color(0xFFA9C7C5)

// 일회성작업 표시 — 홈 목록의 연한노랑 행 바탕(v0.5.8에서 쓰던 색)과 달력에서 날짜를 두르는 노란 원 테두리.
// 주기성작업과 한눈에 구분되도록 2026-10-20에 다시 도입했다(청록 한 가지 + 주황 규칙의 의도된 예외).
val OneTimeYellow = Color(0xFFFFF9C4)
val OneTimeYellowRing = Color(0xFFF2C200)
// 이미 끝낸 일회성작업이 있는 날(지난 이력)의 연한 노란 원 — 예정(진한 노란 원)보다 한 단계 흐리게.
val OneTimeYellowRingLight = Color(0xFFF7E49A)

private val KHomeNoteColorScheme = lightColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    primaryContainer = TealContainer,
    onPrimaryContainer = TealPrimary,
    secondary = TealPrimary,
    onSecondary = Color.White,
    background = BackgroundGray,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = TealContainer,
    onSurfaceVariant = TextSecondary,
    outline = ChipBorderUnselected,
    outlineVariant = RowDivider,
    error = OrangeUrgent,
    onError = Color.White,
    errorContainer = OrangeUrgentContainer,
    onErrorContainer = OrangeUrgentText,
)

// IBM Plex Sans KR: 본문 — 실제로 쓰는 두 굵기(Regular/Bold)만 번들해서 용량을 아낀다.
private val BodyFontFamily = FontFamily(
    Font(R.font.ibm_plex_sans_kr_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_kr_bold, FontWeight.Bold),
)

// Outfit: 숫자(D-day, 날짜, 금액, 개수) — 가변 폭 글꼴 하나를 굵기별로 재사용한다.
@OptIn(ExperimentalTextApi::class)
val NumberFontFamily = FontFamily(
    Font(R.font.outfit, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.outfit, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private val KHomeNoteTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = BodyFontFamily),
        displayMedium = base.displayMedium.copy(fontFamily = BodyFontFamily),
        displaySmall = base.displaySmall.copy(fontFamily = BodyFontFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = BodyFontFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = BodyFontFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = BodyFontFamily),
        titleLarge = base.titleLarge.copy(fontFamily = BodyFontFamily),
        titleMedium = base.titleMedium.copy(fontFamily = BodyFontFamily),
        titleSmall = base.titleSmall.copy(fontFamily = BodyFontFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = BodyFontFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = BodyFontFamily),
        bodySmall = base.bodySmall.copy(fontFamily = BodyFontFamily),
        labelLarge = base.labelLarge.copy(fontFamily = BodyFontFamily),
        labelMedium = base.labelMedium.copy(fontFamily = BodyFontFamily),
        labelSmall = base.labelSmall.copy(fontFamily = BodyFontFamily),
    )
}

@Composable
fun KHomeNoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KHomeNoteColorScheme,
        typography = KHomeNoteTypography,
        content = content,
    )
}
