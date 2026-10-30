package com.khomenote.ui.common

/** 주기성작업 메모는 줄바꿈 기준으로 이 줄 수까지만 받는다. */
const val ITEM_MEMO_MAX_LINES = 3

fun memoLineCount(text: String): Int = text.count { it == '\n' } + 1

/**
 * 메모 입력 칸의 줄 수 제한. 제한을 넘기게 되는 입력은 거절하고 지금 값을 그대로 돌려준다.
 * 제한이 생기기 전에 쓴 긴 메모는 줄을 더 늘리지 않는 편집(고치기·줄이기)만 허용한다.
 */
fun acceptMemoEdit(current: String, proposed: String, maxLines: Int = ITEM_MEMO_MAX_LINES): String =
    if (memoLineCount(proposed) <= maxOf(maxLines, memoLineCount(current))) proposed else current
