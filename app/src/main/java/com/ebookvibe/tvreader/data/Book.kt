package com.ebookvibe.tvreader.data

import android.net.Uri
import org.json.JSONObject
import java.security.MessageDigest

/**
 * A book the user has opened. [id] is either an absolute file path (built-in browser) or a
 * content:// URI string (system picker / another app's VIEW intent).
 */
data class Book(
    val id: String,
    val title: String,
    val pageCount: Int = 0,
    val lastPage: Int = 0,
    val lastOpened: Long = 0L,
    val zoom: String? = null,
) {
    val isContentUri: Boolean get() = id.startsWith("content://")

    /** Stable file name for this book's cached cover thumbnail. */
    val thumbKey: String
        get() = MessageDigest.getInstance("SHA-1").digest(id.toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("pageCount", pageCount)
        .put("lastPage", lastPage)
        .put("lastOpened", lastOpened)
        .apply { if (zoom != null) put("zoom", zoom) }

    companion object {
        fun fromJson(o: JSONObject) = Book(
            id = o.getString("id"),
            title = o.optString("title", "Untitled"),
            pageCount = o.optInt("pageCount", 0),
            lastPage = o.optInt("lastPage", 0),
            lastOpened = o.optLong("lastOpened", 0L),
            zoom = if (o.has("zoom")) o.getString("zoom") else null,
        )

        fun titleFromPath(path: String): String =
            Uri.decode(path.substringAfterLast('/')).removeSuffix(".pdf").removeSuffix(".PDF")
    }
}
