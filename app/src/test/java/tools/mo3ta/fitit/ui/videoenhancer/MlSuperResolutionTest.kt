package tools.mo3ta.fitit.ui.videoenhancer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MlSuperResolutionTest {

    @Test
    fun `tiles cover an exact multiple grid`() {
        val tiles = planSrTiles(128, 64, 64)
        assertEquals(2, tiles.size)
        assertEquals(SrTile(0, 0, 64, 64), tiles[0])
        assertEquals(SrTile(64, 0, 64, 64), tiles[1])
    }

    @Test
    fun `edge tiles are clamped to the frame bounds`() {
        val tiles = planSrTiles(100, 80, 64)
        // 2 columns (64 + 36) x 2 rows (64 + 16) = 4 tiles.
        assertEquals(4, tiles.size)
        assertEquals(SrTile(64, 64, 36, 16), tiles.last())
    }

    @Test
    fun `tiles never exceed the frame and fully cover it`() {
        val width = 213
        val height = 121
        val tile = 50
        val tiles = planSrTiles(width, height, tile)
        var covered = 0
        for (t in tiles) {
            assertTrue(t.srcX + t.srcW <= width)
            assertTrue(t.srcY + t.srcH <= height)
            assertTrue(t.srcW in 1..tile)
            assertTrue(t.srcH in 1..tile)
            covered += t.srcW * t.srcH
        }
        assertEquals(width * height, covered)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-positive tile size throws`() {
        planSrTiles(64, 64, 0)
    }
}
