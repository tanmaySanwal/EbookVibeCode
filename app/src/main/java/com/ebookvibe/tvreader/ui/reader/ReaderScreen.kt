package com.ebookvibe.tvreader.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ebookvibe.tvreader.ui.components.TvButton
import com.ebookvibe.tvreader.ui.theme.SafeMargin

/** Placeholder until the PDF viewer lands in stage 2. */
@Composable
fun ReaderScreen(bookId: String, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().padding(SafeMargin), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Selected:", style = MaterialTheme.typography.headlineSmall)
        Text(bookId, style = MaterialTheme.typography.bodyLarge)
        TvButton("Back", onClick = onClose)
    }
}
