package com.ebookvibe.tvreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

data class DialogOption(val label: String, val selected: Boolean = false, val onClick: () -> Unit)

/** A vertical list of big buttons; the selected option starts focused. BACK dismisses. */
@Composable
fun OptionsDialog(
    title: String,
    options: List<DialogOption>,
    onDismiss: () -> Unit,
    subtitle: String? = null,
) {
    val focus = remember { FocusRequester() }
    val focusIndex = options.indexOfFirst { it.selected }.coerceAtLeast(0)
    Dialog(onDismissRequest = onDismiss) {
        // Inside the dialog window so the requester is attached before we ask for focus.
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { focus.requestFocus() }
        }
        Column(
            Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            options.forEachIndexed { i, option ->
                TvButton(
                    text = (if (option.selected) "✓  " else "") + option.label,
                    onClick = option.onClick,
                    selected = option.selected,
                    modifier = (if (i == focusIndex) Modifier.focusRequester(focus) else Modifier).width(320.dp),
                )
            }
        }
    }
}
