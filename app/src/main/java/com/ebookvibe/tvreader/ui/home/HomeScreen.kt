package com.ebookvibe.tvreader.ui.home

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ebookvibe.tvreader.data.Book
import com.ebookvibe.tvreader.ui.components.FocusCard
import com.ebookvibe.tvreader.ui.components.TvButton
import com.ebookvibe.tvreader.ui.components.rememberSystemPdfPicker
import com.ebookvibe.tvreader.ui.theme.SafeMargin

@Composable
fun HomeScreen(
    onOpenBrowser: () -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenUri: (Uri) -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val books by vm.books.collectAsStateWithLifecycle()
    val openButton = remember { FocusRequester() }
    val firstCard = remember { FocusRequester() }
    val pickSystem = rememberSystemPdfPicker(onOpenUri)

    // Land on the most recent book (one click to resume reading), or on "Open file".
    LaunchedEffect(books == null, books.isNullOrEmpty()) {
        val list = books ?: return@LaunchedEffect
        withFrameNanos { }
        runCatching { if (list.isEmpty()) openButton.requestFocus() else firstCard.requestFocus() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(SafeMargin),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TV PDF Reader", style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
            TvButton("Open file", onClick = onOpenBrowser, modifier = Modifier.focusRequester(openButton))
            Spacer(Modifier.width(20.dp))
            TvButton("System picker", onClick = pickSystem)
        }
        Spacer(Modifier.height(24.dp))

        val list = books
        when {
            list == null -> Unit
            list.isEmpty() -> Column(Modifier.padding(top = 32.dp)) {
                Text("No books yet", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Press OK on \"Open file\" to browse internal storage, Downloads or a USB drive.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                Text(
                    "Recently read  ·  hold OK on a book to remove it",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    contentPadding = PaddingValues(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(list, key = { _, b -> b.id }) { index, book ->
                        BookCard(
                            book = book,
                            loadThumb = vm::thumb,
                            onClick = { onOpenBook(book.id) },
                            onLongClick = { vm.remove(book) },
                            modifier = if (index == 0) Modifier.focusRequester(firstCard) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookCard(
    book: Book,
    loadThumb: suspend (Book) -> android.graphics.Bitmap?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed on lastOpened so a thumbnail generated by the reader shows up when we return.
    val thumb by produceState<ImageBitmap?>(null, book.id, book.lastOpened) {
        value = loadThumb(book)?.asImageBitmap()
    }
    FocusCard(onClick = onClick, onLongClick = onLongClick, modifier = modifier, focusedScale = 1.06f) {
        Column(Modifier.padding(10.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .background(Color(0xFF3A3F4B)),
                contentAlignment = Alignment.Center,
            ) {
                val t = thumb
                if (t != null) {
                    Image(t, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                } else {
                    Text("PDF", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF9AA0AE))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                if (book.pageCount > 0) "Page ${book.lastPage + 1} / ${book.pageCount}" else "Not opened yet",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
    }
}
