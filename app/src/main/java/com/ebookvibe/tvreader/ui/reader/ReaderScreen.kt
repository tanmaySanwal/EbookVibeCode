package com.ebookvibe.tvreader.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ebookvibe.tvreader.ui.components.NumberPadDialog
import com.ebookvibe.tvreader.ui.components.TvButton
import com.ebookvibe.tvreader.ui.theme.SafeMargin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val PageKeysPrev = setOf(Key.PageUp, Key.ChannelUp, Key.MediaPrevious, Key.MediaRewind)
private val PageKeysNext = setOf(Key.PageDown, Key.ChannelDown, Key.MediaNext, Key.MediaFastForward)
private val OkKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)
private val DigitKeys = mapOf(
    Key.One to 1, Key.Two to 2, Key.Three to 3, Key.Four to 4, Key.Five to 5,
    Key.Six to 6, Key.Seven to 7, Key.Eight to 8, Key.Nine to 9,
)

@Composable
fun ReaderScreen(bookId: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    val vm: ReaderViewModel = viewModel(
        factory = viewModelFactory { initializer { ReaderViewModel(app, bookId) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()

    val pageFocus = remember { FocusRequester() }
    val toolbarFocus = remember { FocusRequester() }
    var toolbarFocusRequest by remember { mutableIntStateOf(0) }
    var goToInitial by remember { mutableStateOf("") }
    var centerLongPressed by remember { mutableStateOf(false) }
    var modeHint by remember { mutableStateOf<String?>(null) }

    KeepScreenOn(enabled = !state.sleep.fadingOut)

    fun openToolbar() {
        vm.onUserActivity()
        toolbarFocusRequest++
    }

    // BACK priority: dialog (handled by the Dialog window) → hide toolbar → leave the reader.
    BackHandler { onClose() }
    BackHandler(enabled = state.toolbarVisible) {
        vm.hideToolbar()
        runCatching { pageFocus.requestFocus() }
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { pageFocus.requestFocus() }
    }
    LaunchedEffect(toolbarFocusRequest) {
        if (toolbarFocusRequest == 0) return@LaunchedEffect
        withFrameNanos { }
        runCatching { toolbarFocus.requestFocus() }
    }
    // When the toolbar auto-hides, hand focus back to the page so the D-pad keeps working.
    LaunchedEffect(state.toolbarVisible) {
        if (!state.toolbarVisible) runCatching { pageFocus.requestFocus() }
    }
    LaunchedEffect(modeHint) {
        if (modeHint != null) {
            delay(1500)
            modeHint = null
        }
    }

    fun onPageKey(e: KeyEvent): Boolean {
        val native = e.nativeKeyEvent
        if (e.key in OkKeys) {
            // Short press OK: open the toolbar. Long press OK: toggle pan / turn-page mode.
            if (e.type == KeyEventType.KeyDown) {
                if (native.repeatCount == 0) {
                    centerLongPressed = false
                } else if (native.repeatCount == 1 && !centerLongPressed) {
                    centerLongPressed = true
                    vm.togglePanMode()
                    modeHint = if (vm.state.value.panMode) "Pan mode: arrows move the page" else "Turn mode: ◀ ▶ turn pages"
                }
            } else if (e.type == KeyEventType.KeyUp && !centerLongPressed) {
                openToolbar()
            }
            return true
        }
        if (e.type != KeyEventType.KeyDown) return false
        when (e.key) {
            Key.DirectionUp -> vm.onDirection(Direction.UP)
            Key.DirectionDown -> vm.onDirection(Direction.DOWN)
            Key.DirectionLeft -> vm.onDirection(Direction.LEFT)
            Key.DirectionRight -> vm.onDirection(Direction.RIGHT)
            in PageKeysPrev -> vm.previousPage()
            in PageKeysNext -> vm.nextPage()
            else -> {
                val digit = DigitKeys[e.key] ?: return false
                goToInitial = digit.toString()
                vm.openDialog(ReaderDialog.GO_TO_PAGE)
            }
        }
        return true
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(pageBackground(state.theme))
            .onPreviewKeyEvent { e ->
                // Any remote key (except BACK, which has its own job) wakes the toolbar.
                if (e.type == KeyEventType.KeyDown && e.key != Key.Back && e.key != Key.Escape) {
                    vm.onUserActivity()
                }
                if (e.key == Key.Menu) {
                    if (e.type == KeyEventType.KeyUp) openToolbar()
                    return@onPreviewKeyEvent true
                }
                false
            },
    ) {
        PageCanvas(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { vm.setViewport(it.width, it.height) }
                .focusRequester(pageFocus)
                .focusable()
                .onKeyEvent(::onPageKey),
        )

        if (state.error != null) {
            ErrorPanel(state.error!!, onClose)
        } else if (state.loading && state.image == null) {
            Text("Opening…", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.align(Alignment.Center))
        }

        AnimatedVisibility(
            visible = state.toolbarVisible && state.error == null,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(250)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Toolbar(
                state = state,
                vm = vm,
                firstFocus = toolbarFocus,
                onGoTo = {
                    goToInitial = ""
                    vm.openDialog(ReaderDialog.GO_TO_PAGE)
                },
                onClose = onClose,
            )
        }

        modeHint?.let { hint ->
            Text(
                hint,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xCC000000), RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            )
        }
    }

    when (state.dialog) {
        ReaderDialog.GO_TO_PAGE -> NumberPadDialog(
            title = "Go to page",
            hint = "1 – ${state.pageCount}",
            range = 1..state.pageCount.coerceAtLeast(1),
            initial = goToInitial,
            onConfirm = {
                vm.goToPage(it - 1)
                vm.closeDialog()
            },
            onDismiss = vm::closeDialog,
        )
        else -> Unit
    }
}

/** Draws the rendered page 1:1 (it is already rasterised at screen resolution), centred or panned. */
@Composable
private fun PageCanvas(state: ReaderState, modifier: Modifier) {
    val panX = remember { Animatable(0f) }
    val panY = remember { Animatable(0f) }
    var lastShownPage by remember { mutableIntStateOf(-1) }

    LaunchedEffect(state.panX, state.panY, state.shownPage) {
        if (state.shownPage != lastShownPage) {
            // New page: jump straight to its position, no sliding.
            lastShownPage = state.shownPage
            panX.snapTo(state.panX)
            panY.snapTo(state.panY)
        } else {
            launchBoth(
                { panX.animateTo(state.panX, tween(180)) },
                { panY.animateTo(state.panY, tween(180)) },
            )
        }
    }

    val filter: ColorFilter? = pageColorFilter(state.theme)
    Canvas(modifier) {
        val image = state.image ?: return@Canvas
        if (image.asAndroidBitmap().isRecycled) return@Canvas
        val x = if (image.width <= size.width) (size.width - image.width) / 2f else -panX.value
        val y = if (image.height <= size.height) (size.height - image.height) / 2f else -panY.value
        drawImage(
            image = image,
            dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
            dstSize = IntSize(image.width, image.height),
            colorFilter = filter,
        )
    }
}

private suspend fun launchBoth(a: suspend () -> Unit, b: suspend () -> Unit) = coroutineScope {
    launch { a() }
    launch { b() }
}

@Composable
private fun Toolbar(
    state: ReaderState,
    vm: ReaderViewModel,
    firstFocus: FocusRequester,
    onGoTo: () -> Unit,
    onClose: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // Top bar: title and page indicator.
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xE6000000), Color.Transparent)))
                .padding(start = SafeMargin, end = SafeMargin, top = SafeMargin, bottom = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                state.title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(pageLabel(state), style = MaterialTheme.typography.titleLarge, color = Color.White)
        }

        // Bottom controls.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                .padding(start = SafeMargin, end = SafeMargin, bottom = SafeMargin, top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                // Prev/Next stay enabled at the ends: a disabled button would drop focus.
                TvButton("◀  Prev", onClick = vm::previousPage)
                TvButton(pageLabel(state), onClick = onGoTo)
                TvButton(
                    "Next  ▶",
                    onClick = vm::nextPage,
                    modifier = Modifier.focusRequester(firstFocus),
                )
                TvButton("Close", onClick = onClose)
            }
            Text(
                "OK: controls  ·  hold OK: pan / turn mode  ·  BACK: hide",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB0B6C2),
            )
        }
    }
}

private fun pageLabel(s: ReaderState) =
    if (s.pageCount > 0) "Page ${s.page + 1} / ${s.pageCount}" else ""

@Composable
private fun ErrorPanel(message: String, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { focus.requestFocus() }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(24.dp))
            TvButton("Back to library", onClick = onClose, modifier = Modifier.focusRequester(focus))
        }
    }
}

private fun pageBackground(theme: PageTheme): Color = when (theme) {
    PageTheme.LIGHT -> Color(0xFF2B2B2B)
    PageTheme.DARK -> Color.Black
    PageTheme.SEPIA -> Color(0xFF1E1810)
}

/** Colour treatment for the page bitmap; filled in with dark / sepia matrices in PageColors.kt. */
private fun pageColorFilter(theme: PageTheme): ColorFilter? = PageColors.filterFor(theme)

/** Keeps the projector awake while reading; cleared when [enabled] turns false or the reader closes. */
@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val window = context.findActivity()?.window
        if (enabled) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
