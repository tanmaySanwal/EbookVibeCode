package com.ebookvibe.tvreader.ui.reader

import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Zoom presets. PDFs cannot reflow, so "bigger text" means zooming. Percentages are relative to
 * "Fit page"; on a 16:9 screen a portrait page at Fit width is roughly 270%, so the presets give
 * steps between whole-page and full-width, plus one beyond.
 */
enum class ZoomLevel(val label: String) {
    FIT_PAGE("Fit page"),
    FIT_WIDTH("Fit width"),
    P125("125%"),
    P150("150%"),
    P200("200%"),
    P300("300%");

    /** Device pixels per PDF point for a page of [page] size shown in a [vw]x[vh] viewport. */
    fun scale(page: Size, vw: Int, vh: Int): Float = scale(page.width.toFloat(), page.height.toFloat(), vw, vh)

    fun scale(pageW: Float, pageH: Float, vw: Int, vh: Int): Float {
        val fitPage = minOf(vw / pageW, vh / pageH)
        return when (this) {
            FIT_PAGE -> fitPage
            FIT_WIDTH -> vw / pageW
            P125 -> fitPage * 1.25f
            P150 -> fitPage * 1.5f
            P200 -> fitPage * 2f
            P300 -> fitPage * 3f
        }
    }

    companion object {
        fun parse(name: String?): ZoomLevel = entries.firstOrNull { it.name == name } ?: FIT_PAGE

        /** Presets sorted by actual magnification for this page, for the zoom +/- buttons. */
        fun ordered(page: Size, vw: Int, vh: Int): List<ZoomLevel> =
            ordered(page.width.toFloat(), page.height.toFloat(), vw, vh)

        fun ordered(pageW: Float, pageH: Float, vw: Int, vh: Int): List<ZoomLevel> =
            entries.sortedBy { it.scale(pageW, pageH, vw, vh) }
    }
}

enum class PageTheme(val label: String) {
    LIGHT("Light"), DARK("Dark"), SEPIA("Sepia");

    fun next(): PageTheme = entries[(ordinal + 1) % entries.size]

    companion object {
        fun parse(name: String?): PageTheme = entries.firstOrNull { it.name == name } ?: DARK
    }
}

enum class ReaderDialog { NONE, GO_TO_PAGE, ZOOM, SLEEP, SLEEP_CUSTOM }

data class ReaderState(
    val title: String = "",
    val pageCount: Int = 0,
    /** Page the user asked for (0-based). */
    val page: Int = 0,
    /** Page currently on screen; lags [page] while a render is in flight. */
    val shownPage: Int = -1,
    val image: ImageBitmap? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val zoom: ZoomLevel = ZoomLevel.FIT_PAGE,
    val theme: PageTheme = PageTheme.DARK,
    /** true: D-pad left/right pans a zoomed page; false: left/right turns pages. */
    val panMode: Boolean = false,
    /** Top-left of the viewport within the page bitmap, in pixels. */
    val panX: Float = 0f,
    val panY: Float = 0f,
    val toolbarVisible: Boolean = true,
    val dialog: ReaderDialog = ReaderDialog.NONE,
    val sleep: SleepState = SleepState(),
)

data class SleepState(
    /** Milliseconds left, or null when the timer is off. */
    val remainingMs: Long? = null,
    val showStillReading: Boolean = false,
    /** Timer expired: the screen is fading to black and the reader is about to close. */
    val fadingOut: Boolean = false,
) {
    val active: Boolean get() = remainingMs != null
}
