package com.ebookvibe.tvreader.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ebookvibe.tvreader.ui.theme.FocusFill
import com.ebookvibe.tvreader.ui.theme.FocusRing
import com.ebookvibe.tvreader.ui.theme.SurfaceIdle

private val ButtonShape = RoundedCornerShape(12.dp)

/**
 * The one button used everywhere. Minimum 48dp tall, and when focused it turns amber with a
 * thick white ring and grows slightly, so the focused element is obvious from across the room.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
) {
    Button(
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minWidth = 64.dp, minHeight = 52.dp),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
        shape = ButtonDefaults.shape(shape = ButtonShape),
        scale = ButtonDefaults.scale(focusedScale = 1.08f),
        colors = ButtonDefaults.colors(
            containerColor = if (selected) Color(0xFF3D4A66) else SurfaceIdle,
            contentColor = Color.White,
            focusedContainerColor = FocusFill,
            focusedContentColor = Color.Black,
            pressedContainerColor = Color(0xFFFFD54F),
            pressedContentColor = Color.Black,
            disabledContainerColor = Color(0xFF1C1F26),
            disabledContentColor = Color(0xFF6B7080),
        ),
        border = ButtonDefaults.border(
            border = if (selected) Border(BorderStroke(2.dp, FocusFill), shape = ButtonShape) else Border.None,
            focusedBorder = Border(BorderStroke(4.dp, FocusRing), shape = ButtonShape),
        ),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 1)
    }
}
