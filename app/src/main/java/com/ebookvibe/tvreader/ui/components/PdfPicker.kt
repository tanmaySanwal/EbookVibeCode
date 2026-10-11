package com.ebookvibe.tvreader.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Launcher for the system document picker. Many Android TV builds ship without one, which is why
 * the app has its own browser; this is the fallback when all-files access cannot be granted.
 */
@Composable
fun rememberSystemPdfPicker(onPicked: (Uri) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onPicked(uri)
        }
    }
    return {
        try {
            launcher.launch(arrayOf("application/pdf"))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "This device has no system file picker. Use the built-in browser.", Toast.LENGTH_LONG).show()
        }
    }
}
