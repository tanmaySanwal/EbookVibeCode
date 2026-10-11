package com.ebookvibe.tvreader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomLevelTest {
    private val w = 1920
    private val h = 1080

    @Test
    fun portraitPageOnProjectorOrdersPresetsByMagnification() {
        // A4 portrait in PDF points: Fit width is ~2.5x Fit page on a 16:9 screen.
        val order = ZoomLevel.ordered(595f, 842f, w, h)
        assertEquals(
            listOf(ZoomLevel.FIT_PAGE, ZoomLevel.P125, ZoomLevel.P150, ZoomLevel.P200, ZoomLevel.FIT_WIDTH, ZoomLevel.P300),
            order,
        )
    }

    @Test
    fun fitPageFillsHeightForPortraitPage() {
        val scale = ZoomLevel.FIT_PAGE.scale(595f, 842f, w, h)
        assertEquals(1080f, 842f * scale, 0.5f)
    }

    @Test
    fun fitWidthFillsWidth() {
        val scale = ZoomLevel.FIT_WIDTH.scale(595f, 842f, w, h)
        assertEquals(1920f, 595f * scale, 0.5f)
    }

    @Test
    fun percentagesAreRelativeToFitPage() {
        val fit = ZoomLevel.FIT_PAGE.scale(595f, 842f, w, h)
        assertEquals(fit * 2f, ZoomLevel.P200.scale(595f, 842f, w, h), 1e-4f)
        assertEquals(fit * 3f, ZoomLevel.P300.scale(595f, 842f, w, h), 1e-4f)
    }

    @Test
    fun parseFallsBackToFitPage() {
        assertEquals(ZoomLevel.FIT_PAGE, ZoomLevel.parse(null))
        assertEquals(ZoomLevel.FIT_PAGE, ZoomLevel.parse("bogus"))
        assertEquals(ZoomLevel.P150, ZoomLevel.parse("P150"))
    }

    @Test
    fun themeCyclesLightDarkSepia() {
        assertEquals(PageTheme.DARK, PageTheme.LIGHT.next())
        assertEquals(PageTheme.SEPIA, PageTheme.DARK.next())
        assertEquals(PageTheme.LIGHT, PageTheme.SEPIA.next())
    }
}
