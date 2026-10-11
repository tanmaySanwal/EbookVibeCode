package com.ebookvibe.tvreader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Size
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Thin wrapper around [PdfRenderer]. The framework renderer is not thread-safe and allows only one
 * open page at a time, so every call is funnelled through a private single-thread dispatcher.
 */
class PdfDocument private constructor(
    private val fd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val dispatcher: ExecutorCoroutineDispatcher,
) {
    val pageCount: Int = renderer.pageCount
    private val sizes = arrayOfNulls<Size>(pageCount)
    @Volatile private var closed = false

    /** Page size in PDF points (1/72 inch). */
    suspend fun pageSize(index: Int): Size = withContext(dispatcher) { sizeLocked(index) }

    private fun sizeLocked(index: Int): Size =
        sizes[index] ?: renderer.openPage(index).use { Size(it.width, it.height) }.also { sizes[index] = it }

    /**
     * Renders a whole page at [scale] device pixels per PDF point. The bitmap is produced at the
     * final on-screen resolution, so zoomed text is re-rasterised sharply rather than upscaled.
     */
    suspend fun render(index: Int, scale: Float): Bitmap? = withContext(dispatcher) {
        if (closed) return@withContext null
        renderer.openPage(index).use { page ->
            val w = (page.width * scale).roundToInt().coerceAtLeast(1)
            val h = (page.height * scale).roundToInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            // PdfRenderer draws onto a transparent canvas; paper should be white.
            bitmap.eraseColor(Color.WHITE)
            val matrix = Matrix().apply { setScale(w / page.width.toFloat(), h / page.height.toFloat()) }
            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }

    /** Small cover image for the home screen. */
    suspend fun renderThumbnail(widthPx: Int): Bitmap? {
        val size = pageSize(0)
        return render(0, widthPx / size.width.toFloat())
    }

    suspend fun close() {
        withContext(dispatcher) {
            if (!closed) {
                closed = true
                runCatching { renderer.close() }
                runCatching { fd.close() }
            }
        }
        dispatcher.close()
    }

    companion object {
        /** Opens a file path or content:// URI. Throws on missing, corrupt or password-protected files. */
        suspend fun open(context: Context, id: String): PdfDocument {
            val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "pdf-render") }
                .asCoroutineDispatcher()
            return try {
                withContext(dispatcher) {
                    val fd = if (id.startsWith("content://")) {
                        context.contentResolver.openFileDescriptor(Uri.parse(id), "r")
                            ?: error("Cannot open document")
                    } else {
                        ParcelFileDescriptor.open(File(id), ParcelFileDescriptor.MODE_READ_ONLY)
                    }
                    try {
                        PdfDocument(fd, PdfRenderer(fd), dispatcher)
                    } catch (t: Throwable) {
                        fd.close()
                        throw t
                    }
                }
            } catch (t: Throwable) {
                dispatcher.close()
                throw t
            }
        }
    }
}
