package com.khomenote.ui.common

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * LazyColumn 안에서 손잡이를 끌어 항목 순서를 바꾸는 상태 홀더.
 * 끌리는 항목은 손가락을 따라 움직이고, 옆 항목의 절반을 넘어갈 때마다 그 항목과 자리를 바꾼다.
 * 실제 DB 저장은 [onReorderFinished]에서 최종 순서를 받아 호출자가 처리한다.
 *
 * 손가락이 목록의 보이는 영역 위/아래 가장자리에 머무는 동안에는 계속 스크롤한다 — 그렇지 않으면 화면에
 * 다 안 보이는 긴 목록에서 맨 아래 항목을 맨 위로 옮길 방법이 없다. 목록 자체가 스크롤되면 그것을,
 * 목록이 통째로 펼쳐져 페이지 스크롤 안에 들어 있으면 [outerScroll]로 받은 바깥 스크롤을 움직인다.
 * 가장자리를 알려면 LazyColumn에 [dragContainer]가 붙어 있어야 한다.
 *
 * LazyColumn 항목에는 반드시 안정적인 key를 준다(끌리는 항목을 key로 찾는다).
 *
 * (k-golf, k-cooking, k-home-note, k-ott, k-stock, k-youtube가 같은 파일을 쓴다 — 한 곳을 고치면 함께 고친다.)
 */
class DragDropListState(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val itemCount: () -> Int,
    private val onMove: (from: Int, to: Int) -> Unit,
    private val onReorderFinished: () -> Unit,
    private val outerScroll: () -> ScrollableState?,
    private val edgeZonePx: Float,
    private val minScrollSpeedPx: Float,
    private val maxScrollSpeedPx: Float,
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    private var draggedKey: Any? = null

    /** 끌기 시작할 때 항목의 위치(목록의 보이는 영역 기준)와 그 뒤 손가락이 움직인 거리. */
    private var initialItemOffset by mutableIntStateOf(0)
    private var fingerTravel by mutableFloatStateOf(0f)

    /** 손가락의 화면(window) 세로 위치. 손가락이 멈춰 있으면 이벤트가 안 오므로 마지막 값을 기억해 둔다. */
    private var fingerWindowY: Float? = null
    internal var containerCoordinates: LayoutCoordinates? = null
    private var autoScrollJob: Job? = null

    /**
     * 끌리는 항목을 제자리에서 얼마나 옮겨 그릴지. "손가락이 있어야 할 위치 − 지금 배치된 위치"로 매번 구하므로,
     * 자리가 바뀌거나 목록이 스크롤돼 배치가 달라져도 항목은 손가락 밑에 그대로 있다.
     */
    val draggingItemOffset: Float
        get() {
            val index = draggingItemIndex ?: return 0f
            val info = draggedItemInfo() ?: return 0f
            val half = info.size / 2f
            // 맨 위/맨 아래에서는 더 갈 곳이 없으니 목록 밖으로 멀리 끌려 나가지 않게 한다.
            val min = if (index <= 0) -half else Float.NEGATIVE_INFINITY
            val max = if (index >= itemCount() - 1) half else Float.POSITIVE_INFINITY
            return rawOffset(info).coerceIn(min, max)
        }

    fun isDragging(index: Int): Boolean = draggingItemIndex == index

    private fun draggedItemInfo(): LazyListItemInfo? {
        val key = draggedKey ?: return null
        return listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
    }

    private fun rawOffset(info: LazyListItemInfo): Float = initialItemOffset + fingerTravel - info.offset

    fun onDragStart(index: Int, fingerY: Float? = null) {
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
        draggedKey = info.key
        initialItemOffset = info.offset
        fingerTravel = 0f
        fingerWindowY = fingerY
        draggingItemIndex = index
        autoScrollJob?.cancel()
        autoScrollJob = scope.launch { autoScrollWhileDragging() }
    }

    fun onDrag(delta: Float, fingerY: Float? = null) {
        if (draggingItemIndex == null) return
        fingerTravel += delta
        if (fingerY != null) fingerWindowY = fingerY
        swapIfNeeded()
    }

    private fun swapIfNeeded() {
        val currentIndex = draggingItemIndex ?: return
        val visibleItems = listState.layoutInfo.visibleItemsInfo
        val info = draggedItemInfo() ?: return
        // 방금 자리를 바꿨는데 아직 다시 배치되지 않았으면(배치 정보가 한 박자 늦다) 기다린다 —
        // 낡은 위치로 계산하면 곧바로 되돌아가는 바꿈이 일어난다.
        if (info.index != currentIndex) return

        val offset = rawOffset(info)
        val direction = if (offset > 0f) 1 else -1
        val targetIndex = currentIndex + direction
        if (targetIndex !in 0 until itemCount()) return
        val step = visibleItems.firstOrNull { it.index == targetIndex }?.size?.toFloat()?.takeIf { it > 0f } ?: info.size.toFloat()
        if (offset * direction <= step / 2f) return

        // LazyColumn은 맨 위에 보이는 항목을 key로 붙잡고 있어서, 그 항목이 자리를 바꾸면 목록이 그 항목을 따라
        // 스크롤돼 버린다. 지금 스크롤 위치를 그대로 유지하라고 먼저 알려 둔다.
        val firstVisible = listState.firstVisibleItemIndex
        if (currentIndex == firstVisible || targetIndex == firstVisible) {
            listState.requestScrollToItem(firstVisible, listState.firstVisibleItemScrollOffset)
        }
        onMove(currentIndex, targetIndex)
        draggingItemIndex = targetIndex
    }

    private suspend fun autoScrollWhileDragging() {
        var lastFrame = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val seconds = ((frame - lastFrame) / 1_000_000_000f).coerceAtMost(0.05f)
            lastFrame = frame
            val delta = autoScrollVelocity() * seconds
            if (delta == 0f || draggedItemInfo() == null) continue

            val listCanScroll = if (delta > 0f) listState.canScrollForward else listState.canScrollBackward
            if (listCanScroll) {
                // 목록이 스크롤되면 항목의 배치 위치가 바뀌고, draggingItemOffset이 그만큼을 알아서 메운다.
                listState.scrollBy(delta)
            } else {
                // 바깥 페이지가 스크롤되면 목록 전체가 화면에서 움직인다. 손가락은 그대로이므로 그만큼 더 끌린 셈이다.
                val outer = outerScroll() ?: continue
                fingerTravel += outer.scrollBy(delta)
            }
            swapIfNeeded()
        }
    }

    /** 손가락이 보이는 영역의 위/아래 가장자리 띠 안에 있으면 그 방향 스크롤 속도(px/초), 아니면 0. 깊이 들어갈수록 빠르다. */
    private fun autoScrollVelocity(): Float {
        val fingerY = fingerWindowY ?: return 0f
        val container = containerCoordinates?.takeIf { it.isAttached } ?: return 0f
        // boundsInWindow는 바깥 스크롤/화면에 잘린 뒤의 영역이라, 목록이 페이지 안에 통째로 펼쳐져 있어도
        // "실제로 보이는 부분"의 가장자리가 나온다.
        val bounds = container.boundsInWindow()
        if (bounds.height <= 0f) return 0f
        val zone = minOf(edgeZonePx, bounds.height / 3f)
        val intoTop = (bounds.top + zone - fingerY) / zone
        val intoBottom = (fingerY - (bounds.bottom - zone)) / zone
        fun speed(depth: Float) = minScrollSpeedPx + (maxScrollSpeedPx - minScrollSpeedPx) * depth.coerceIn(0f, 1f)
        return when {
            intoTop > 0f -> -speed(intoTop)
            intoBottom > 0f -> speed(intoBottom)
            else -> 0f
        }
    }

    fun onDragEnd() {
        autoScrollJob?.cancel()
        autoScrollJob = null
        val wasDragging = draggingItemIndex != null
        draggingItemIndex = null
        draggedKey = null
        fingerTravel = 0f
        fingerWindowY = null
        if (wasDragging) onReorderFinished()
    }
}

/**
 * [outerScroll]: 목록이 세로 스크롤되는 페이지 안에 들어 있을 때 그 페이지의 스크롤 상태(예: `rememberScrollState()`).
 * 목록 자체가 더 스크롤되지 않을 때 가장자리에서 이것을 대신 움직인다. 목록이 화면을 혼자 채우면 생략한다.
 */
@Composable
fun rememberDragDropListState(
    listState: LazyListState,
    itemCount: () -> Int,
    onMove: (from: Int, to: Int) -> Unit,
    onReorderFinished: () -> Unit,
    outerScroll: ScrollableState? = null,
): DragDropListState {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // remember(listState){}는 listState가 안 바뀌는 한 딱 한 번만 실행되기 때문에, 람다를
    // 그대로 넘기면 "첫 조립 시점의" itemCount/onMove/onReorderFinished에 영원히 갇힌다 —
    // 예를 들어 데이터가 아직 안 불러와져 목록이 비어있던 최초 조립 시점의 itemCount=0이
    // 계속 쓰이면서 드래그할 때 크래시가 났다. rememberUpdatedState로 매번 최신 람다를
    // 가리키게 해서 이 문제를 막는다.
    val currentItemCount = rememberUpdatedState(itemCount)
    val currentOnMove = rememberUpdatedState(onMove)
    val currentOnReorderFinished = rememberUpdatedState(onReorderFinished)
    val currentOuterScroll = rememberUpdatedState(outerScroll)
    return remember(listState) {
        DragDropListState(
            listState = listState,
            scope = scope,
            itemCount = { currentItemCount.value() },
            onMove = { from, to -> currentOnMove.value(from, to) },
            onReorderFinished = { currentOnReorderFinished.value() },
            outerScroll = { currentOuterScroll.value },
            edgeZonePx = with(density) { 72.dp.toPx() },
            minScrollSpeedPx = with(density) { 120.dp.toPx() },
            maxScrollSpeedPx = with(density) { 900.dp.toPx() },
        )
    }
}

/** 순서를 바꾸는 LazyColumn에 붙인다. 목록의 보이는 영역을 알아야 가장자리에서 자동으로 스크롤할 수 있다. */
fun Modifier.dragContainer(dragDropListState: DragDropListState): Modifier =
    this.onGloballyPositioned { dragDropListState.containerCoordinates = it }

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
    // 손가락의 화면 위치(자동 스크롤 판단용)를 구하려고 손잡이의 좌표를 들고 있는다. 화면을 다시 그릴 필요가 없는 값이라 상태로 두지 않는다.
    val handleCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    return this
        .onGloballyPositioned { handleCoordinates[0] = it }
        .pointerInput(dragDropListState) {
            fun windowY(local: Offset): Float? = handleCoordinates[0]?.takeIf { it.isAttached }?.localToWindow(local)?.y
            detectDragGestures(
                onDragStart = { start -> dragDropListState.onDragStart(currentIndex, windowY(start)) },
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragDropListState.onDrag(dragAmount.y, windowY(change.position))
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
