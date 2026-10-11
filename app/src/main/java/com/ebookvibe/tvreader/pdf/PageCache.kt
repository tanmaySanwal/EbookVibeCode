package com.ebookvibe.tvreader.pdf

import android.graphics.Bitmap

/** One rendered bitmap: a page under a particular zoom + viewport combination ([variant]). */
data class PageKey(val index: Int, val variant: String)

/**
 * Tiny LRU for rendered pages, capped at [maxEntries] (3: previous, current, next) and at
 * [maxBytes] so heavily zoomed pages cannot exhaust memory on a 2 GB projector.
 *
 * Evicted bitmaps are handed to [onEvict] instead of being recycled here, because the UI may still
 * be drawing one for a frame; the owner recycles them once it is safe.
 */
class PageCache(
    private val maxEntries: Int = 3,
    val maxBytes: Long,
    private val onEvict: (Bitmap) -> Unit,
) {
    private val map = LinkedHashMap<PageKey, Bitmap>(8, 0.75f, true)

    @Synchronized
    fun get(key: PageKey): Bitmap? = map[key]?.takeUnless { it.isRecycled }

    @Synchronized
    fun contains(key: PageKey): Boolean = map.containsKey(key)

    @Synchronized
    fun put(key: PageKey, bitmap: Bitmap) {
        map.put(key, bitmap)?.let { if (it !== bitmap) onEvict(it) }
        trim()
    }

    @Synchronized
    fun holds(bitmap: Bitmap): Boolean = map.values.any { it === bitmap }

    /** Drops everything except [keep] (the current page and its neighbours at the current zoom). */
    @Synchronized
    fun retain(keep: Set<PageKey>) {
        val it = map.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (e.key !in keep) {
                it.remove()
                onEvict(e.value)
            }
        }
    }

    @Synchronized
    fun clear() {
        map.values.forEach(onEvict)
        map.clear()
    }

    private fun bytes() = map.values.sumOf { it.allocationByteCount.toLong() }

    private fun trim() {
        // Always keep the newest entry, even if it alone exceeds the byte budget.
        while (map.size > 1 && (map.size > maxEntries || bytes() > maxBytes)) {
            val eldest = map.entries.iterator().next()
            map.remove(eldest.key)
            onEvict(eldest.value)
        }
    }
}
