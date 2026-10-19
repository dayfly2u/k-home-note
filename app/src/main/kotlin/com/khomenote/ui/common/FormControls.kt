package com.khomenote.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khomenote.ui.theme.ChipBorderUnselected
import com.khomenote.ui.theme.NumberFontFamily
import com.khomenote.ui.theme.SurfaceWhite
import com.khomenote.ui.theme.TealPrimary
import com.khomenote.ui.theme.TextFaint
import com.khomenote.ui.theme.TextPrimary
import com.khomenote.ui.theme.TextSecondary

// 시안(ItemForm.dc.html) 입력 컨트롤 — 포커스되면 청록 1.5dp 테두리, 아니면 회색 1dp 테두리.

/** 한 줄(또는 [singleLine]=false면 여러 줄) 텍스트 입력. 높이 48dp, 모서리 14dp. */
@Composable
fun KTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SurfaceWhite)
            .border(if (focused) 1.5.dp else 1.dp, if (focused) TealPrimary else ChipBorderUnselected, shape)
            .height(if (singleLine) 48.dp else 80.dp)
            .padding(horizontal = 14.dp),
        contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = TextStyle(fontSize = 16.sp, color = TextPrimary),
            cursorBrush = SolidColor(TealPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = if (singleLine) 0.dp else 12.dp)
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, fontSize = 16.sp, color = TextFaint)
                inner()
            },
        )
    }
}

/** 숫자 입력 칸(월/일/개수 등) — 가운데 정렬 Outfit 17sp. 숫자만 거르는 건 호출하는 쪽(ViewModel)이 한다. */
@Composable
fun NumberBox(value: String, onValueChange: (String) -> Unit, contentDescription: String, width: Dp = 52.dp) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .width(width)
            .height(44.dp)
            .semantics { this.contentDescription = contentDescription }
            .clip(shape)
            .background(SurfaceWhite)
            .border(if (focused) 1.5.dp else 1.dp, if (focused) TealPrimary else ChipBorderUnselected, shape),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(fontFamily = NumberFontFamily, fontSize = 17.sp, color = TextPrimary, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(TealPrimary),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).onFocusChanged { focused = it.isFocused },
        )
    }
}

/** 큰 세그먼트 탭(예: 기간마다 / 매달 / 매년) — 선택된 칸만 흰 바탕 + 청록 글자. */
@Composable
fun SegmentedTabs(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val trackShape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(trackShape)
            .background(Color(0xFFE8EEEE))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val shape = RoundedCornerShape(10.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .then(if (selected) Modifier.shadow(1.dp, shape) else Modifier)
                    .clip(shape)
                    .background(if (selected) SurfaceWhite else Color.Transparent)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) TealPrimary else TextSecondary,
                )
            }
        }
    }
}

/** 작은 두 칸~세 칸 라디오(예: 양력 | 음력, 일 | 개월 | 년) — 선택된 칸은 청록 채움. */
@Composable
fun RadioSegment(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(modifier = Modifier.clip(shape).border(1.dp, ChipBorderUnselected, shape)) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .background(if (selected) TealPrimary else SurfaceWhite)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) Color.White else TextSecondary,
                )
            }
        }
    }
}

/** −/+ 스테퍼 버튼(44dp 원형 테두리). */
@Composable
fun StepperButton(symbol: String, contentDescription: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .semantics { this.contentDescription = contentDescription }
            .clip(CircleShape)
            .border(1.dp, ChipBorderUnselected, CircleShape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (enabled) TextPrimary else TextFaint)
    }
}
