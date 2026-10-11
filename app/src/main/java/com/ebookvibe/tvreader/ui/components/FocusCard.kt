package com.ebookvibe.tvreader.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.ebookvibe.tvreader.ui.theme.FocusFill
import com.ebookvibe.tvreader.ui.theme.FocusRing
import com.ebookvibe.tvreader.ui.theme.SurfaceIdle

/** Clickable surface with the same loud focus treatment as [TvButton]; used for rows and cards. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    focusedScale: Float = 1.03f,
    highlightFill: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusedScale),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = SurfaceIdle,
            contentColor = Color.White,
            focusedContainerColor = if (highlightFill) FocusFill else Color(0xFF3A4152),
            focusedContentColor = if (highlightFill) Color.Black else Color.White,
            pressedContainerColor = Color(0xFFFFD54F),
            pressedContentColor = Color.Black,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(4.dp, FocusRing), shape = shape),
        ),
        content = content,
    )
}
