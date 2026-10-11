package com.ebookvibe.tvreader.ui.reader

import android.app.ActivityManager
import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ebookvibe.tvreader.ReaderApp
import com.ebookvibe.tvreader.pdf.PageCache
import com.ebookvibe.tvreader.pdf.PageKey
import com.ebookvibe.tvreader.pdf.PdfDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.sqrt

enum class Direction { UP, DOWN, LEFT, RIGHT }

/** Where the viewport lands on a newly shown bitmap. */
private sealed interface Anchor {
    /** Top of the page, same horizontal position (next page, or "Go to page"). */
    data object Top : Anchor
    /** Bottom of the page (moving backwards by panning up past the top edge). */
    data object Bottom : Anchor
    /** Same relative centre as before (zoom or viewport change). */
    data class Keep(val cx: Float, val cy: Float) : Anchor
}

class ReaderViewModel(app: Application, private val bookId: String) : AndroidViewModel(app) {

    private val repo = (app as ReaderApp).books
    private val appScope = app.appScope

    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    private var doc: PdfDocument? = null
    private var viewportW = 0
    private var viewportH = 0

    /** Bitmap currently on screen; never recycled while it is. */
    private var shownBitmap: Bitmap? = null

    private val cache = PageCache(maxEntries = 3, maxBytes = cacheBudget(app), onEvict = ::recycleLater)

    private var renderJob: Job? = null
    private var saveJob: Job? = null
    private var hideJob: Job? = null

    init {
        viewModelScope.launch { open() }
        scheduleToolbarHide()
    }

    // ---------------------------------------------------------------- loading & rendering

    private suspend fun open() {
        val book = repo.get(bookId)
        val theme = PageTheme.parse(repo.pageTheme.first())
        val title = book?.title ?: repo.displayName(bookId)
        _state.update { it.copy(title = title, theme = theme, zoom = ZoomLevel.parse(book?.zoom)) }

        val d = try {
            PdfDocument.open(getApplication(), bookId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _state.update { it.copy(loading = false, error = openErrorMessage(e)) }
            return
        }
        if (d.pageCount <= 0) {
            d.close()
            _state.update { it.copy(loading = false, error = "This PDF has no pages.") }
            return
        }
        doc = d
        val page = (book?.lastPage ?: 0).coerceIn(0, d.pageCount - 1)
        _state.update { it.copy(pageCount = d.pageCount, page = page) }
        repo.saveProgress(bookId, page, d.pageCount, _state.value.zoom.name)
        render(Anchor.Top)
        makeThumbnailIfMissing(d)
    }

    private fun openErrorMessage(e: Throwable): String = when (e) {
        is SecurityException -> "This PDF is password-protected and can't be opened."
        is java.io.FileNotFoundException -> "File not found. Was the USB drive removed?"
        else -> "Couldn't open this PDF (${e.javaClass.simpleName})."
    }

    /** Called by the UI whenever the page area is measured. */
    fun setViewport(width: Int, height: Int) {
        if (width <= 0 || height <= 0 || (width == viewportW && height == viewportH)) return
        val anchor = currentCentre()
        viewportW = width
        viewportH = height
        render(anchor)
    }

    private fun variant(zoom: ZoomLevel) = "${zoom.name}@${viewportW}x$viewportH"

    private fun render(anchor: Anchor) {
        val d = doc ?: return
        if (viewportW <= 0) return
        val s = _state.value
        val index = s.page
        val zoom = s.zoom
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val bitmap = try {
                obtain(d, index, zoom)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(loading = false, error = "Couldn't render page ${index + 1}.") }
                return@launch
            } ?: return@launch
            show(index, bitmap, anchor)

            // Keep only this page and its neighbours, then pre-render the neighbours so turning
            // is instant. Skip whichever neighbour would not fit in the memory budget.
            val keep = setOf(index - 1, index, index + 1).map { PageKey(it, variant(zoom)) }.toSet()
            cache.retain(keep)
            val bytes = bitmap.allocationByteCount.toLong()
            runCatching {
                if (index + 1 < d.pageCount && bytes * 2 <= cache.maxBytes) obtain(d, index + 1, zoom)
                if (index - 1 >= 0 && bytes * 3 <= cache.maxBytes) obtain(d, index - 1, zoom)
            }.onFailure { if (it is CancellationException) throw it }
        }
    }

    /** Returns the cached bitmap or renders it at the zoomed resolution (so text stays sharp). */
    private suspend fun obtain(d: PdfDocument, index: Int, zoom: ZoomLevel): Bitmap? {
        val key = PageKey(index, variant(zoom))
        cache.get(key)?.let { return it }
        val size = d.pageSize(index)
        var scale = zoom.scale(size, viewportW, viewportH)
        val pixels = size.width * scale * size.height * scale
        if (pixels > MAX_PIXELS) scale *= sqrt(MAX_PIXELS / pixels)
        val bitmap = try {
            d.render(index, scale)
        } catch (oom: OutOfMemoryError) {
            // Low-RAM device: drop everything we can and try once more at a lower resolution.
            cache.retain(emptySet())
            System.gc()
            d.render(index, scale * 0.6f)
        } ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    private fun show(index: Int, bitmap: Bitmap, anchor: Anchor) {
        val old = shownBitmap
        shownBitmap = bitmap
        val maxX = (bitmap.width - viewportW).coerceAtLeast(0).toFloat()
        val maxY = (bitmap.height - viewportH).coerceAtLeast(0).toFloat()
        val s = _state.value
        val (x, y) = when (anchor) {
            Anchor.Top -> s.panX to 0f
            Anchor.Bottom -> s.panX to maxY
            is Anchor.Keep -> (anchor.cx * bitmap.width - viewportW / 2f) to (anchor.cy * bitmap.height - viewportH / 2f)
        }
        _state.update {
            it.copy(
                shownPage = index,
                image = bitmap.asImageBitmap(),
                loading = false,
                error = null,
                panX = x.coerceIn(0f, maxX),
                panY = y.coerceIn(0f, maxY),
            )
        }
        if (old != null && old !== bitmap && !cache.holds(old)) recycleLater(old)
    }

    /**
     * Frees bitmap memory early (important with ~2 GB of RAM) but only after the UI has had time
     * to switch to the new image, so a frame never draws a recycled bitmap.
     */
    private fun recycleLater(bitmap: Bitmap) {
        if (bitmap === shownBitmap) return
        viewModelScope.launch {
            delay(600)
            if (bitmap !== shownBitmap && !cache.holds(bitmap) && !bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun currentCentre(): Anchor {
        val b = shownBitmap ?: return Anchor.Top
        val s = _state.value
        return Anchor.Keep(
            cx = ((s.panX + viewportW / 2f) / b.width).coerceIn(0f, 1f),
            cy = ((s.panY + viewportH / 2f) / b.height).coerceIn(0f, 1f),
        )
    }

    private fun makeThumbnailIfMissing(d: PdfDocument) {
        if (repo.thumbFile(bookId).exists()) return
        viewModelScope.launch {
            runCatching {
                d.renderThumbnail(THUMB_WIDTH)?.let { thumb ->
                    repo.saveThumb(bookId, thumb)
                    thumb.recycle()
                }
            }
        }
    }

    // ---------------------------------------------------------------- navigation

    fun nextPage() = goToPage(_state.value.page + 1, Anchor.Top)

    fun previousPage() = goToPage(_state.value.page - 1, Anchor.Top)

    fun goToPage(page: Int) = goToPage(page, Anchor.Top)

    private fun goToPage(page: Int, anchor: Anchor) {
        val s = _state.value
        if (s.pageCount == 0) return
        val target = page.coerceIn(0, s.pageCount - 1)
        if (target == s.page) return
        _state.update { it.copy(page = target) }
        saveProgressSoon()
        render(anchor)
    }

    /** D-pad on the page. Pans when the page is bigger than the screen, otherwise turns pages. */
    fun onDirection(direction: Direction) {
        val s = _state.value
        when (direction) {
            Direction.UP -> if (!panBy(0f, -PAN_STEP)) goToPage(s.page - 1, Anchor.Bottom)
            Direction.DOWN -> if (!panBy(0f, PAN_STEP)) goToPage(s.page + 1, Anchor.Top)
            Direction.LEFT -> if (s.panMode && isWiderThanScreen()) panBy(-PAN_STEP, 0f) else previousPage()
            Direction.RIGHT -> if (s.panMode && isWiderThanScreen()) panBy(PAN_STEP, 0f) else nextPage()
        }
    }

    private fun isWiderThanScreen(): Boolean = (shownBitmap?.width ?: 0) > viewportW

    /** Moves the viewport by a fraction of the screen. Returns false if already at that edge. */
    private fun panBy(fx: Float, fy: Float): Boolean {
        val b = shownBitmap ?: return false
        val s = _state.value
        val maxX = (b.width - viewportW).coerceAtLeast(0).toFloat()
        val maxY = (b.height - viewportH).coerceAtLeast(0).toFloat()
        val x = (s.panX + fx * viewportW).coerceIn(0f, maxX)
        val y = (s.panY + fy * viewportH).coerceIn(0f, maxY)
        if (x == s.panX && y == s.panY) return false
        _state.update { it.copy(panX = x, panY = y) }
        return true
    }

    fun togglePanMode() = _state.update { it.copy(panMode = !it.panMode) }

    // ---------------------------------------------------------------- zoom & theme

    fun setZoom(zoom: ZoomLevel) {
        val d = doc ?: return
        if (zoom == _state.value.zoom) return
        val anchor = currentCentre()
        viewModelScope.launch {
            val size = d.pageSize(_state.value.page)
            // Pan mode is the natural default once the page no longer fits across the screen.
            val wider = size.width * zoom.scale(size, viewportW, viewportH) > viewportW + 1
            _state.update { it.copy(zoom = zoom, panMode = wider) }
            saveProgressSoon()
            render(anchor)
        }
    }

    fun zoomIn() = stepZoom(+1)

    fun zoomOut() = stepZoom(-1)

    private fun stepZoom(delta: Int) {
        val d = doc ?: return
        if (viewportW <= 0) return
        viewModelScope.launch {
            val ordered = ZoomLevel.ordered(d.pageSize(_state.value.page), viewportW, viewportH)
            val i = ordered.indexOf(_state.value.zoom)
            ordered.getOrNull(i + delta)?.let(::setZoom)
        }
    }

    fun cycleTheme() {
        val theme = _state.value.theme.next()
        _state.update { it.copy(theme = theme) }
        repo.setPageTheme(theme.name)
    }

    // ---------------------------------------------------------------- toolbar & dialogs

    /** Any remote key: show the toolbar and restart the 4 s auto-hide countdown. */
    fun onUserActivity() {
        if (!_state.value.toolbarVisible) _state.update { it.copy(toolbarVisible = true) }
        scheduleToolbarHide()
    }

    fun hideToolbar() {
        hideJob?.cancel()
        _state.update { it.copy(toolbarVisible = false) }
    }

    private fun scheduleToolbarHide() {
        hideJob?.cancel()
        hideJob = viewModelScope.launch {
            delay(TOOLBAR_TIMEOUT_MS)
            val s = _state.value
            if (s.dialog == ReaderDialog.NONE && !s.sleep.showStillReading) {
                _state.update { it.copy(toolbarVisible = false) }
            }
        }
    }

    fun openDialog(dialog: ReaderDialog) {
        hideJob?.cancel()
        _state.update { it.copy(dialog = dialog, toolbarVisible = true) }
    }

    fun closeDialog() {
        _state.update { it.copy(dialog = ReaderDialog.NONE) }
        scheduleToolbarHide()
    }

    // ---------------------------------------------------------------- persistence

    private fun saveProgressSoon() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(700)
            saveProgressNow()
        }
    }

    fun saveProgressNow() {
        val s = _state.value
        if (s.pageCount > 0) repo.saveProgress(bookId, s.page, s.pageCount, s.zoom.name)
    }

    override fun onCleared() {
        saveProgressNow()
        val d = doc
        doc = null
        // Close on the app scope: viewModelScope is already cancelled here.
        if (d != null) appScope.launch { d.close() }
    }

    private companion object {
        const val TOOLBAR_TIMEOUT_MS = 4_000L
        const val PAN_STEP = 0.3f
        const val THUMB_WIDTH = 240
        /** Largest page bitmap we will allocate (~48 MB). */
        const val MAX_PIXELS = 12_000_000f

        fun cacheBudget(app: Application): Long {
            val am = app.getSystemService(ActivityManager::class.java)
            val mb = am?.memoryClass ?: 128
            val budget = mb / 3L * 1024 * 1024
            val capped = budget.coerceIn(24L shl 20, 96L shl 20)
            return if (am?.isLowRamDevice == true) capped / 2 else capped
        }
    }
}
