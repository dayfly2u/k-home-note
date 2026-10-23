package com.khomenote.ui.item

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptPhotoStateTest {
    private val discarded = mutableListOf<String>()
    private fun state(original: String?) = ReceiptPhotoState(original) { discarded += it }

    @Test
    fun `처음 상태는 원래 사진이고 아무것도 지우지 않는다`() {
        val s = state("old.jpg")
        assertEquals("old.jpg", s.path)
        assertFalse(s.pickFailed)
        assertTrue(discarded.isEmpty())
    }

    @Test
    fun `새 사진을 골라도 원래 사진은 저장 전까지 지우지 않는다`() {
        val s = state("old.jpg")
        s.onPicked("new1.jpg")
        assertEquals("new1.jpg", s.path)
        assertTrue(discarded.isEmpty())
    }

    @Test
    fun `사진을 다시 고르면 직전에 고른 저장 안 된 사진만 정리한다`() {
        val s = state("old.jpg")
        s.onPicked("new1.jpg")
        s.onPicked("new2.jpg")
        assertEquals("new2.jpg", s.path)
        assertEquals(listOf("new1.jpg"), discarded)
    }

    @Test
    fun `원래 사진이 없을 때 처음 고른 사진은 정리할 것이 없다`() {
        val s = state(null)
        s.onPicked("new1.jpg")
        assertEquals("new1.jpg", s.path)
        assertTrue(discarded.isEmpty())
    }

    @Test
    fun `복사에 실패하면 지금 사진을 그대로 두고 실패만 표시한다`() {
        val s = state("old.jpg")
        s.onPicked(null)
        assertEquals("old.jpg", s.path)
        assertTrue(s.pickFailed)
        assertTrue(discarded.isEmpty())
    }

    @Test
    fun `실패 뒤에 성공하면 실패 표시가 사라진다`() {
        val s = state(null)
        s.onPicked(null)
        s.onPicked("new1.jpg")
        assertFalse(s.pickFailed)
    }

    @Test
    fun `저장 없이 닫으면 새로 고른 사진만 지우고 원래 사진은 남긴다`() {
        val s = state("old.jpg")
        s.onPicked("new1.jpg")
        s.discardUnsaved()
        assertEquals(listOf("new1.jpg"), discarded)
    }

    @Test
    fun `사진을 바꾸지 않고 닫으면 아무것도 지우지 않는다`() {
        val s = state("old.jpg")
        s.discardUnsaved()
        assertTrue(discarded.isEmpty())
    }

    @Test
    fun `저장하면 바뀐 경우에만 원래 사진을 지우고 새 경로를 돌려준다`() {
        val s = state("old.jpg")
        s.onPicked("new1.jpg")
        assertEquals("new1.jpg", s.commit())
        assertEquals(listOf("old.jpg"), discarded)
    }

    @Test
    fun `사진을 바꾸지 않고 저장하면 아무것도 지우지 않고 원래 경로를 돌려준다`() {
        val s = state("old.jpg")
        assertEquals("old.jpg", s.commit())
        assertTrue(discarded.isEmpty())
    }

    @Test
    fun `사진이 없는 채로 저장하면 null을 돌려준다`() {
        assertNull(state(null).commit())
    }
}
