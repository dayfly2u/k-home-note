package com.khomenote.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * listState 기준으로 아래에 아직 안 보이는 항목이 더 있을 때(canScrollForward)
 * 리스트 하단에 "더 있음" 표시를 겹쳐 보여준다. LazyColumn을 감싸는 Box 안에서
 * Modifier.align(Alignment.BottomCenter)와 함께 사용한다.
 */
@Composable
fun MoreBelowIndicator(listState: LazyListState, modifier: Modifier = Modifier) {
    MoreBelowIndicator(visible = listState.canScrollForward, modifier = modifier)
}

/** LazyColumn이 아니라 `verticalScroll` 페이지 전체를 감쌀 때 — `ScrollState.canScrollForward`를 넘긴다. */
@Composable
fun MoreBelowIndicator(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Text(
            "▼ 더 있음",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .background(Color(0xAA000000), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
