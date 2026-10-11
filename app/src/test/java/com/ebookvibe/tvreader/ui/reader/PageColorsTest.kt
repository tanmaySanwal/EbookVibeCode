package com.ebookvibe.tvreader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageColorsTest {

    private fun dark(r: Float, g: Float, b: Float) = PageColors.apply(PageColors.darkMatrix, r, g, b)
    private fun sepia(r: Float, g: Float, b: Float) = PageColors.apply(PageColors.sepiaMatrix, r, g, b)

    @Test
    fun darkTurnsPaperNearBlackAndInkNearWhite() {
        val paper = dark(255f, 255f, 255f)
        val ink = dark(0f, 0f, 0f)
        paper.forEach { assertTrue("paper channel $it", it < 30f) }
        ink.forEach { assertTrue("ink channel $it", it > 200f) }
    }

    @Test
    fun darkKeepsHuesOfColourImages() {
        // A plain invert would turn red cyan and blue yellow; the hue rotation undoes that.
        val red = dark(255f, 0f, 0f)
        assertTrue("red stays red-dominant", red[0] > red[1] && red[0] > red[2])
        val blue = dark(0f, 0f, 255f)
        assertTrue("blue stays blue-dominant", blue[2] > blue[0] && blue[2] > blue[1])
        val green = dark(0f, 160f, 0f)
        assertTrue("green stays green-dominant", green[1] > green[0] && green[1] > green[2])
    }

    @Test
    fun sepiaPaperIsWarm() {
        val (r, g, b) = sepia(255f, 255f, 255f).toList()
        assertTrue(r > g && g > b)
        val ink = sepia(0f, 0f, 0f)
        ink.forEach { assertTrue(it < 70f) }
    }

    @Test
    fun concatAppliesFirstThenSecond() {
        val identity = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
        val m = PageColors.darkMatrix
        assertEquals(m.toList(), PageColors.concat(m, identity).toList())
        assertEquals(m.toList(), PageColors.concat(identity, m).toList())
    }

    @Test
    fun sleepTimerFormatsRoundingUp() {
        assertEquals("0:00", SleepTimer.format(0))
        assertEquals("1:00", SleepTimer.format(59_001))
        assertEquals("1:01", SleepTimer.format(61_000))
        assertEquals("15:00", SleepTimer.format(15 * 60_000L))
    }
}
