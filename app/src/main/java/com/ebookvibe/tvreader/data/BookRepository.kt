package com.ebookvibe.tvreader.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

private val Context.dataStore by preferencesDataStore(name = "reader")

/** Recent books, last-read pages and reader preferences, persisted with DataStore. */
class BookRepository(private val context: Context, private val scope: CoroutineScope) {

    private val booksKey = stringPreferencesKey("books")
    private val pageThemeKey = stringPreferencesKey("page_theme")

    private val thumbDir: File get() = File(context.filesDir, "thumbs").apply { mkdirs() }

    val recentBooks: Flow<List<Book>> = context.dataStore.data.map { prefs ->
        decode(prefs[booksKey]).sortedByDescending { it.lastOpened }
    }

    val pageTheme: Flow<String?> = context.dataStore.data.map { it[pageThemeKey] }

    suspend fun get(id: String): Book? = recentBooks.first().firstOrNull { it.id == id }

    /** Insert or update a book. Runs on the app scope so it completes even if the caller is gone. */
    fun upsert(id: String, transform: (Book?) -> Book) {
        scope.launch {
            context.dataStore.edit { prefs ->
                val list = decode(prefs[booksKey]).toMutableList()
                val index = list.indexOfFirst { it.id == id }
                val updated = transform(list.getOrNull(index))
                if (index >= 0) list[index] = updated else list.add(updated)
                // Keep the home grid manageable.
                val trimmed = list.sortedByDescending { it.lastOpened }.take(MAX_RECENTS)
                prefs[booksKey] = encode(trimmed)
            }
        }
    }

    /** Marks a book as just opened so it moves to the front of the home grid. */
    fun touch(id: String) {
        val title = displayName(id)
        upsert(id) { old -> (old ?: Book(id = id, title = title)).copy(lastOpened = System.currentTimeMillis()) }
    }

    private fun displayName(id: String): String {
        if (!id.startsWith("content://")) return Book.titleFromPath(id)
        val name = runCatching {
            context.contentResolver.query(Uri.parse(id), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
        return Book.titleFromPath(name ?: id)
    }

    fun saveProgress(id: String, page: Int, pageCount: Int, zoom: String?) = upsert(id) { old ->
        (old ?: Book(id = id, title = Book.titleFromPath(id)))
            .copy(lastPage = page, pageCount = pageCount, zoom = zoom)
    }

    fun remove(id: String) {
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[booksKey] = encode(decode(prefs[booksKey]).filterNot { it.id == id })
            }
            thumbFile(id).delete()
        }
    }

    fun setPageTheme(theme: String) {
        scope.launch { context.dataStore.edit { it[pageThemeKey] = theme } }
    }

    fun thumbFile(id: String): File = File(thumbDir, Book(id, "").thumbKey + ".png")

    suspend fun loadThumb(id: String): Bitmap? = withContext(Dispatchers.IO) {
        val f = thumbFile(id)
        if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
    }

    suspend fun saveThumb(id: String, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        runCatching {
            thumbFile(id).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun decode(raw: String?): List<Book> {
        if (raw.isNullOrEmpty()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { Book.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun encode(books: List<Book>): String =
        JSONArray().apply { books.forEach { put(it.toJson()) } }.toString()

    companion object {
        const val MAX_RECENTS = 40
    }
}
