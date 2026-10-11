package com.ebookvibe.tvreader.ui.browser

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ebookvibe.tvreader.data.FileEntry
import com.ebookvibe.tvreader.data.Storage
import com.ebookvibe.tvreader.ui.components.FocusCard
import com.ebookvibe.tvreader.ui.components.TvButton
import com.ebookvibe.tvreader.ui.components.rememberSystemPdfPicker
import com.ebookvibe.tvreader.ui.theme.SafeMargin

@Composable
fun BrowserScreen(
    onOpenFile: (String) -> Unit,
    onOpenUri: (Uri) -> Unit,
    onExit: () -> Unit,
    vm: BrowserViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    // Permission may be granted from the settings screen; re-check every time we come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    UsbMountListener { vm.refresh() }

    // BACK walks up the folder tree, then leaves the browser.
    BackHandler { if (!vm.goUp()) onExit() }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(SafeMargin),
    ) {
        Text("Open a PDF", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            state.dir?.absolutePath ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(20.dp))

        if (!state.hasAccess) {
            PermissionPanel(onOpenUri = onOpenUri, onRetry = vm::refresh)
            return@Column
        }

        Row(Modifier.fillMaxSize()) {
            RootsColumn(state, vm)
            Spacer(Modifier.width(32.dp))
            EntriesList(state, vm, onOpenFile, onOpenUri)
        }
    }
}

@Composable
private fun RootsColumn(state: BrowserState, vm: BrowserViewModel) {
    LazyColumn(
        modifier = Modifier.width(280.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(8.dp),
    ) {
        itemsIndexed(state.roots, key = { _, r -> r.dir.absolutePath }) { _, root ->
            val current = state.root?.dir?.absolutePath == root.dir.absolutePath
            TvButton(
                text = (if (root.removable) "USB  " else "") + root.label,
                onClick = { vm.openRoot(root) },
                selected = current,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            TvButton("Refresh drives", onClick = vm::refresh, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun EntriesList(
    state: BrowserState,
    vm: BrowserViewModel,
    onOpenFile: (String) -> Unit,
    onOpenUri: (Uri) -> Unit,
) {
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val atRoot = state.dir?.absolutePath == state.root?.dir?.absolutePath
    // Row 0 is "parent folder" when we are below the root.
    val offset = if (atRoot) 0 else 1
    val focusIndex = state.focusName
        ?.let { name -> state.entries.indexOfFirst { it.file.name == name } }
        ?.takeIf { it >= 0 }
        ?.plus(offset) ?: 0

    LaunchedEffect(state.dir, state.entries, state.loading) {
        if (state.loading) return@LaunchedEffect
        if (state.entries.isEmpty() && atRoot) return@LaunchedEffect
        listState.scrollToItem(focusIndex)
        withFrameNanos { }
        runCatching { focusRequester.requestFocus() }
    }

    val pickSystem = rememberSystemPdfPicker(onOpenUri)

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (!atRoot) {
            item(key = "..") {
                EntryRow(
                    icon = "⬆",
                    title = "Parent folder",
                    subtitle = state.dir?.parentFile?.name ?: "",
                    onClick = { vm.goUp() },
                    modifier = if (focusIndex == 0) Modifier.focusRequester(focusRequester) else Modifier,
                )
            }
        }
        itemsIndexed(state.entries, key = { _, e -> e.file.absolutePath }) { index, entry ->
            EntryRow(
                icon = if (entry.isDirectory) "📁" else "📄",
                title = entry.file.name,
                subtitle = if (entry.isDirectory) "Folder" else "PDF · " + Storage.formatSize(entry.sizeBytes),
                onClick = {
                    if (entry.isDirectory) vm.openDir(entry.file) else onOpenFile(entry.file.absolutePath)
                },
                modifier = if (index + offset == focusIndex) Modifier.focusRequester(focusRequester) else Modifier,
            )
        }
        if (!state.loading && state.entries.isEmpty()) {
            item(key = "empty") {
                Column(Modifier.padding(16.dp)) {
                    Text("No folders or PDF files here.", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    TvButton("Use system file picker", onClick = pickSystem)
                }
            }
        }
    }
}

@Composable
private fun EntryRow(icon: String, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier) {
    FocusCard(onClick = onClick, modifier = modifier.fillMaxWidth(), focusedScale = 1.02f) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(icon, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(20.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
    }
}

@Composable
private fun PermissionPanel(onOpenUri: (Uri) -> Unit, onRetry: () -> Unit) {
    val context = LocalContext.current
    val firstButton = remember { FocusRequester() }
    var settingsFailed by remember { mutableStateOf(false) }

    val runtimePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            onRetry()
        } else {
            settingsFailed = !Storage.openAllFilesAccessSettings(context)
        }
    }
    val pickSystem = rememberSystemPdfPicker(onOpenUri)

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstButton.requestFocus() }
    }

    Column(Modifier.fillMaxWidth(0.75f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Storage access needed", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (Storage.needsAllFilesAccess)
                "To browse PDFs on internal storage and USB drives, turn on \"All files access\" " +
                    "for TV PDF Reader on the next screen, then press BACK to return here."
            else
                "Allow access to storage so the reader can list PDFs on this device and on USB drives.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            TvButton(
                text = "Grant access",
                modifier = Modifier.focusRequester(firstButton),
                onClick = {
                    if (Storage.needsAllFilesAccess) {
                        settingsFailed = !Storage.openAllFilesAccessSettings(context)
                    } else {
                        runtimePermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                },
            )
            TvButton(text = "Use system file picker", onClick = pickSystem)
            TvButton(text = "Check again", onClick = onRetry)
        }
        if (settingsFailed) {
            Text(
                "This TV has no permission screen for it. Grant it once from a computer with:",
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            Text("If the setting is missing on this TV, grant it from a computer with:", style = MaterialTheme.typography.bodyMedium)
        }
        Box(
            Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
        ) {
            Text(Storage.adbGrantCommand(context), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Refreshes the list of drives when a USB stick is plugged in or removed. */
@Composable
private fun UsbMountListener(onChange: () -> Unit) {
    val context = LocalContext.current
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = onChange()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addDataScheme("file")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
}
