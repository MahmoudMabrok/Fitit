package tools.mo3ta.fitit.ui.bluesecret

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlueSecretTest {

    @Test
    fun `column starts with a hash and stacks marks`() {
        val out = BlueSecret.column(20, Random(2))
        assertTrue(out.startsWith("#"))
        assertEquals(21, out.length)
    }

    @Test
    fun `arabic secret roundtrips`() {
        val out = BlueSecret.hide("السلام عليكم", 12, Random(1))
        assertEquals("السلام عليكم", BlueSecret.reveal(out))
    }

    @Test
    fun `plain text reveals nothing`() {
        assertNull(BlueSecret.reveal("hello #tag"))
    }

    @Test
    fun `zalgo keeps the original letters`() {
        val out = BlueSecret.zalgo("hi", 2, Random(4))
        assertTrue(out.contains('h'))
        assertTrue(out.contains('i'))
        assertTrue(out.length > 2)
    }
}
