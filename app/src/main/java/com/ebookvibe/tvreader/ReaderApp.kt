package com.ebookvibe.tvreader

import android.app.Application
import com.ebookvibe.tvreader.data.BookRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ReaderApp : Application() {
    /** Outlives screens so "save last page" writes are never cancelled when the reader closes. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val books: BookRepository by lazy { BookRepository(this, appScope) }
}
