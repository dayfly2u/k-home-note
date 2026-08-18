package com.khomenote.ui.common

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * LazyColumn 안에서 롱프레스 후 드래그로 항목 순서를 바꾸는 상태 홀더.
 * 항목 높이가 대략 균일하다고 가정하고, 드래그 오프셋이 한 항목 높이를 넘어갈 때마다
 * 리스트 안에서 그만큼 자리를 바꾼다. 실제 DB 저장은 [onReorderFinished]에서
 * 최종 순서를 받아 호출자가 처리한다.
 *
 * 드래그 중인 항목이 화면(뷰포트) 가장자리에 닿으면 리스트를 자동으로 스크롤한다 —
 * 그렇지 않으면 화면에 다 안 보이는 긴 리스트에서 맨 아래 항목을 맨 위로 옮기려 해도
 * 손가락이 화면 밖으로 나갈 수 없어 끝까지 이동시킬 방법이 없다.
 */
class DragDropListState(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val itemCount: () -> Int,
    private val onMove: (from: Int, to: Int) -> Unit,
    private val onReorderFinished: () -> Unit,
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set
    var draggingItemOffset by mutableStateOf(0f)
        private set

    fun isDragging(index: Int): Boolean = draggingItemIndex == index

    fun onDragStart(index: Int) {
        draggingItemIndex = index
        draggingItemOffset = 0f
    }

    fun onDrag(delta: Float) {
        val currentIndex = draggingItemIndex ?: return
        draggingItemOffset += delta

        val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentIndex } ?: return
        val itemHeight = itemInfo.size.toFloat()
        if (itemHeight <= 0f) return

        val moveBy = (draggingItemOffset / itemHeight).toInt()
        if (moveBy != 0) {
            val targetIndex = (currentIndex + moveBy).coerceIn(0, itemCount() - 1)
            if (targetIndex != currentIndex) {
                onMove(currentIndex, targetIndex)
                draggingItemIndex = targetIndex
                draggingItemOffset -= moveBy * itemHeight
            } else {
                // 리스트 끝에 도달해서 더 못 옮기면 오프셋이 무한정 쌓이지 않게 한계를 둔다.
                draggingItemOffset = draggingItemOffset.coerceIn(-itemHeight, itemHeight)
            }
        }

        autoScrollIfNeeded(itemInfo)
    }

    private fun autoScrollIfNeeded(itemInfo: LazyListItemInfo) {
        val layoutInfo = listState.layoutInfo
        val draggedTop = itemInfo.offset + draggingItemOffset
        val draggedBottom = draggedTop + itemInfo.size

        val overflowTop = draggedTop - layoutInfo.viewportStartOffset
        val overflowBottom = draggedBottom - layoutInfo.viewportEndOffset

        val scrollAmount = when {
            overflowTop < 0 && listState.canScrollBackward -> overflowTop
            overflowBottom > 0 && listState.canScrollForward -> overflowBottom
            else -> 0f
        }
        if (scrollAmount != 0f) {
            scope.launch { listState.scrollBy(scrollAmount) }
        }
    }

    fun onDragEnd() {
        draggingItemIndex = null
        draggingItemOffset = 0f
        onReorderFinished()
    }
}

@Composable
fun rememberDragDropListState(
    listState: LazyListState,
    itemCount: () -> Int,
    onMove: (from: Int, to: Int) -> Unit,
    onReorderFinished: () -> Unit,
): DragDropListState {
    val scope = rememberCoroutineScope()
    return remember(listState) { DragDropListState(listState, scope, itemCount, onMove, onReorderFinished) }
}

/** 이 Modifier가 붙은 뷰(보통 "≡" 손잡이 아이콘)를 롱프레스+드래그하면 [index] 항목이 움직인다. */
fun Modifier.dragHandle(index: Int, dragDropListState: DragDropListState): Modifier =
    this.pointerInput(dragDropListState) {
        detectDragGesturesAfterLongPress(
            onDragStart = { dragDropListState.onDragStart(index) },
            onDrag = { change, dragAmount ->
                change.consume()
                dragDropListState.onDrag(dragAmount.y)
            },
            onDragEnd = { dragDropListState.onDragEnd() },
            onDragCancel = { dragDropListState.onDragEnd() },
        )
    }

/** 드래그 중인 항목을 다른 항목들 위로 그려서 겹침 없이 자연스럽게 보이게 한다. */
fun Modifier.dragElevation(index: Int, dragDropListState: DragDropListState): Modifier =
    if (dragDropListState.isDragging(index)) this.zIndex(1f) else this
