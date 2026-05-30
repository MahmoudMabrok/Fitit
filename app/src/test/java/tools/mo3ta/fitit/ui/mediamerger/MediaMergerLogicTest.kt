package tools.mo3ta.fitit.ui.mediamerger

import org.junit.Assert.*
import org.junit.Test

class MediaMergerLogicTest {

    @Test
    fun `canMerge false for fewer than two items`() {
        assertFalse(canMerge(0, false))
        assertFalse(canMerge(1, false))
    }

    @Test
    fun `canMerge true for two or more idle items`() {
        assertTrue(canMerge(2, false))
        assertTrue(canMerge(5, false))
    }

    @Test
    fun `canMerge false while processing`() {
        assertFalse(canMerge(3, true))
    }

    @Test
    fun `movedUp swaps with previous element`() {
        assertEquals(listOf("b", "a", "c"), listOf("a", "b", "c").movedUp(1))
    }

    @Test
    fun `movedUp on first element is no-op`() {
        assertEquals(listOf("a", "b", "c"), listOf("a", "b", "c").movedUp(0))
    }

    @Test
    fun `movedUp out of bounds is no-op`() {
        assertEquals(listOf("a", "b"), listOf("a", "b").movedUp(9))
    }

    @Test
    fun `movedDown swaps with next element`() {
        assertEquals(listOf("a", "c", "b"), listOf("a", "b", "c").movedDown(1))
    }

    @Test
    fun `movedDown on last element is no-op`() {
        assertEquals(listOf("a", "b", "c"), listOf("a", "b", "c").movedDown(2))
    }

    @Test
    fun `removedAt removes the target element`() {
        assertEquals(listOf("a", "c"), listOf("a", "b", "c").removedAt(1))
    }

    @Test
    fun `removedAt out of bounds is no-op`() {
        assertEquals(listOf("a", "b"), listOf("a", "b").removedAt(5))
    }

    @Test
    fun `moves do not mutate the original list`() {
        val original = listOf("a", "b", "c")
        original.movedUp(1)
        original.movedDown(1)
        original.removedAt(0)
        assertEquals(listOf("a", "b", "c"), original)
    }
}
