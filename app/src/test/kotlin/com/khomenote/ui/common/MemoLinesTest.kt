package com.khomenote.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoLinesTest {
    @Test
    fun `빈 메모와 한 줄 메모는 1줄이다`() {
        assertEquals(1, memoLineCount(""))
        assertEquals(1, memoLineCount("가족 식사 장소 예약"))
    }

    @Test
    fun `줄바꿈 하나마다 한 줄이 늘고 끝의 빈 줄도 센다`() {
        assertEquals(2, memoLineCount("a\nb"))
        assertEquals(3, memoLineCount("a\nb\nc"))
        assertEquals(3, memoLineCount("a\nb\n"))
    }

    @Test
    fun `세 줄까지는 그대로 받는다`() {
        assertEquals("a\nb\nc", acceptMemoEdit("a\nb", "a\nb\nc"))
    }

    @Test
    fun `네 번째 줄을 만드는 입력은 거절하고 지금 값을 둔다`() {
        assertEquals("a\nb\nc", acceptMemoEdit("a\nb\nc", "a\nb\nc\n"))
    }

    @Test
    fun `가운데 줄에서 줄바꿈을 넣어 네 줄이 되는 입력도 거절한다`() {
        assertEquals("a\nb\nc", acceptMemoEdit("a\nb\nc", "a\n\nb\nc"))
    }

    @Test
    fun `세 줄을 넘기는 붙여넣기는 통째로 거절한다`() {
        assertEquals("a", acceptMemoEdit("a", "a\nb\nc\nd\ne"))
    }

    @Test
    fun `긴 글이 한 줄이면 줄바꿈이 없으니 길이와 상관없이 받는다`() {
        val long = "가".repeat(500)
        assertEquals(long, acceptMemoEdit("", long))
    }

    @Test
    fun `제한 전에 쓴 다섯 줄 메모는 줄 수를 늘리지 않는 고치기를 허용한다`() {
        val old = "1\n2\n3\n4\n5"
        assertEquals("1\n2\n3\n4\n5!", acceptMemoEdit(old, "1\n2\n3\n4\n5!"))
    }

    @Test
    fun `제한 전에 쓴 다섯 줄 메모는 줄을 줄이는 것은 허용하고 늘리는 것은 거절한다`() {
        val old = "1\n2\n3\n4\n5"
        assertEquals("1\n2\n3\n4", acceptMemoEdit(old, "1\n2\n3\n4"))
        assertEquals(old, acceptMemoEdit(old, "1\n2\n3\n4\n5\n6"))
    }
}
