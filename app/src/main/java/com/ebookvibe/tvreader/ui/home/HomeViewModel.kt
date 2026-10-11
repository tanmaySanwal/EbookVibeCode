package com.ebookvibe.tvreader.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ebookvibe.tvreader.ReaderApp
import com.ebookvibe.tvreader.data.Book
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as ReaderApp).books

    /** null until DataStore has loaded, so the screen can avoid flashing the empty state. */
    val books: StateFlow<List<Book>?> = repo.recentBooks
        .map<List<Book>, List<Book>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun remove(book: Book) = repo.remove(book.id)

    suspend fun thumb(book: Book) = repo.loadThumb(book.id)
}
