package com.khomenote.ui.common

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * LazyColumn 안에서 손잡이를 끌어 항목 순서를 바꾸는 상태 홀더.
 * 끌리는 항목은 손가락을 따라 움직이고, 옆 항목의 절반을 넘어갈 때마다 그 항목과 자리를 바꾼다. 실제 DB 저장은 [onReorderFinished]에서
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

        val visibleItems = listState.layoutInfo.visibleItemsInfo
        val itemInfo = visibleItems.firstOrNull { it.index == currentIndex } ?: return
        val itemHeight = itemInfo.size.toFloat()
        if (itemHeight <= 0f) return

        // 옆 항목의 절반을 넘어가면 자리를 바꾼다. 예전에는 한 칸 높이를 통째로 끌어야 바뀌어서, 손가락은
        // 한참 움직였는데 화면은 그대로인 구간이 길었다. 바꾼 뒤에는 그 항목 높이만큼 오프셋을 되돌려
        // 끌리는 항목이 손가락 밑에 그대로 있게 한다.
        val count = itemCount()
        val direction = if (draggingItemOffset > 0f) 1 else -1
        val targetIndex = currentIndex + direction
        if (targetIndex in 0 until count) {
            val step = visibleItems.firstOrNull { it.index == targetIndex }?.size?.toFloat()?.takeIf { it > 0f } ?: itemHeight
            if (draggingItemOffset * direction > step / 2f) {
                onMove(currentIndex, targetIndex)
                draggingItemIndex = targetIndex
                draggingItemOffset -= direction * step
            }
        } else {
            // 리스트 끝에 도달해서 더 못 옮기면 오프셋이 무한정 쌓이지 않게 한계를 둔다.
            draggingItemOffset = draggingItemOffset.coerceIn(-itemHeight / 2f, itemHeight / 2f)
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
    // remember(listState){}는 listState가 안 바뀌는 한 딱 한 번만 실행되기 때문에, 람다를
    // 그대로 넘기면 "첫 조립 시점의" itemCount/onMove/onReorderFinished에 영원히 갇힌다 —
    // 예를 들어 데이터가 아직 안 불러와져 목록이 비어있던 최초 조립 시점의 itemCount=0이
    // 계속 쓰이면서 드래그할 때 크래시가 났다. rememberUpdatedState로 매번 최신 람다를
    // 가리키게 해서 이 문제를 막는다.
    val currentItemCount = rememberUpdatedState(itemCount)
    val currentOnMove = rememberUpdatedState(onMove)
    val currentOnReorderFinished = rememberUpdatedState(onReorderFinished)
    return remember(listState) {
        DragDropListState(
            listState = listState,
            scope = scope,
            itemCount = { currentItemCount.value() },
            onMove = { from, to -> currentOnMove.value(from, to) },
            onReorderFinished = { currentOnReorderFinished.value() },
        )
    }
}

/**
 * 이 Modifier가 붙은 뷰(보통 "≡" 손잡이 아이콘)를 잡고 끌면 [index] 항목이 움직인다.
 *
 * - 길게 누르기를 기다리지 않고 잡자마자 끌린다. 손잡이는 끌기 전용이라 기다릴 이유가 없고, 길게 누르기를
 *   기다리는 동안 손가락이 조금이라도 움직이면 리스트 스크롤로 넘어가 "안 잡히는" 것처럼 느껴졌다.
 * - pointerInput의 키에 [index]를 넣지 않는다. 넣으면 끄는 도중 자리가 바뀌어 index가 달라지는 순간
 *   제스처 블록이 다시 시작되면서 진행 중이던 드래그가 끊긴다(한 칸 움직이고 멈춘다). 대신 최신 index를
 *   rememberUpdatedState로 읽어, 같은 항목을 연달아 끌어도 지난 위치에서 시작하지 않게 한다.
 */
@Composable
fun Modifier.dragHandle(index: Int, dragDropListState: DragDropListState): Modifier {
    val currentIndex by rememberUpdatedState(index)
    return this.pointerInput(dragDropListState) {
        detectDragGestures(
            onDragStart = { dragDropListState.onDragStart(currentIndex) },
            onDrag = { change, dragAmount ->
                change.consume()
                dragDropListState.onDrag(dragAmount.y)
            },
            onDragEnd = { dragDropListState.onDragEnd() },
            onDragCancel = { dragDropListState.onDragEnd() },
        )
    }
}

/** 드래그 중인 항목이 손가락을 따라 움직이게 하고, 다른 항목들 위로 그려서 겹침 없이 자연스럽게 보이게 한다. */
fun Modifier.dragElevation(index: Int, dragDropListState: DragDropListState): Modifier =
    if (dragDropListState.isDragging(index)) {
        this.zIndex(1f).graphicsLayer { translationY = dragDropListState.draggingItemOffset }
    } else {
        this
    }
