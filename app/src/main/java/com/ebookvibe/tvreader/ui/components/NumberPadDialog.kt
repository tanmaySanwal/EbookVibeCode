package com.ebookvibe.tvreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

private val DigitKeys = mapOf(
    Key.Zero to 0, Key.One to 1, Key.Two to 2, Key.Three to 3, Key.Four to 4,
    Key.Five to 5, Key.Six to 6, Key.Seven to 7, Key.Eight to 8, Key.Nine to 9,
    Key.NumPad0 to 0, Key.NumPad1 to 1, Key.NumPad2 to 2, Key.NumPad3 to 3, Key.NumPad4 to 4,
    Key.NumPad5 to 5, Key.NumPad6 to 6, Key.NumPad7 to 7, Key.NumPad8 to 8, Key.NumPad9 to 9,
)

/**
 * Number entry that works with nothing but a D-pad: an on-screen keypad (no IME needed, which is
 * unreliable on TV), plus the remote's own digit keys if it has them.
 */
@Composable
fun NumberPadDialog(
    title: String,
    hint: String,
    range: IntRange,
    initial: String = "",
    confirmLabel: String = "Go",
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val value = text.toIntOrNull()
    val valid = value != null && value in range
    val maxDigits = range.last.toString().length

    fun type(d: Int) {
        if (text.length < maxDigits) text = (text + d).trimStart('0')
    }

    fun confirm() {
        if (valid) onConfirm(value!!)
    }

    val firstFocus = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss) {
        // Inside the dialog window so the requester is attached before we ask for focus.
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { firstFocus.requestFocus() }
        }
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .padding(32.dp)
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val digit = DigitKeys[e.key]
                    when {
                        digit != null -> { type(digit); true }
                        e.key == Key.Backspace || e.key == Key.Delete -> { text = text.dropLast(1); true }
                        else -> false
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(
                text.ifEmpty { "–" },
                fontSize = 44.sp,
                color = if (text.isEmpty() || valid) Color.White else Color(0xFFFF8A80),
                modifier = Modifier
                    .width(260.dp)
                    .background(Color(0xFF0E1015), RoundedCornerShape(12.dp))
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center,
            )
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", confirmLabel))
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { label ->
                        val focusMod = if (label == "1" && initial.isEmpty() || label == confirmLabel && initial.isNotEmpty()) {
                            Modifier.focusRequester(firstFocus)
                        } else {
                            Modifier
                        }
                        TvButton(
                            text = label,
                            // Never disabled: a disabled button can't hold focus and focus would jump away.
                            modifier = focusMod.width(110.dp),
                            onClick = {
                                when (label) {
                                    "⌫" -> text = text.dropLast(1)
                                    confirmLabel -> confirm()
                                    else -> type(label.toInt())
                                }
                            },
                        )
                    }
                }
            }
            TvButton("Cancel", onClick = onDismiss, modifier = Modifier.width(358.dp))
        }
    }
}
